package rs.rmt.tracker.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny path-pattern router (supports "{param}" segments) sitting on top of
 * com.sun.net.httpserver.HttpServer, so no web framework is needed.
 */
public final class Router implements HttpHandler {

    public interface RouteHandler {
        void handle(HttpExchange exchange, Map<String, String> pathParams) throws IOException;
    }

    private record Route(String method, Pattern pattern, List<String> paramNames, RouteHandler handler) {}

    private final List<Route> routes = new ArrayList<>();

    public Router add(String method, String pathTemplate, RouteHandler handler) {
        List<String> names = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        for (String segment : pathTemplate.split("/")) {
            if (segment.isEmpty()) continue;
            regex.append('/');
            if (segment.startsWith("{") && segment.endsWith("}")) {
                names.add(segment.substring(1, segment.length() - 1));
                regex.append("([^/]+)");
            } else {
                regex.append(Pattern.quote(segment));
            }
        }
        regex.append("/?$");
        routes.add(new Route(method, Pattern.compile(regex.toString()), names, handler));
        return this;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            HttpUtil.applyCors(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        for (Route route : routes) {
            if (!route.method().equalsIgnoreCase(method)) continue;
            Matcher m = route.pattern().matcher(path);
            if (!m.matches()) continue;

            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < route.paramNames().size(); i++) {
                params.put(route.paramNames().get(i), m.group(i + 1));
            }
            try {
                HttpUtil.applyCors(exchange);
                route.handler().handle(exchange, params);
            } catch (Exception e) {
                e.printStackTrace();
                HttpUtil.sendJson(exchange, 500, Json.obj("error", String.valueOf(e.getMessage())));
            }
            return;
        }

        HttpUtil.applyCors(exchange);
        HttpUtil.sendJson(exchange, 404, Json.obj("error", "Not found: " + method + " " + path));
    }
}
