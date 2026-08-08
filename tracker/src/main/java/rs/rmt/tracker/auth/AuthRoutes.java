package rs.rmt.tracker.auth;

import rs.rmt.tracker.users.SessionStore;
import rs.rmt.tracker.users.User;
import rs.rmt.tracker.users.UserStore;
import rs.rmt.tracker.util.HttpUtil;
import rs.rmt.tracker.util.Json;
import rs.rmt.tracker.util.Router;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Account registration / login / profile endpoints - the base for the "little social" part from
 * noveStvari.md. Sessions are bearer tokens (Authorization header), not cookies, because the GUI
 * is served from a different origin (Vite on :5173) than this tracker.
 *
 * Nothing here gates the existing peer/file endpoints: file sharing keeps working without an
 * account, and login is additive. Making discovery require a token is a separate decision.
 */
public final class AuthRoutes {
    private AuthRoutes() {}

    public static void register(Router router, UserStore users, SessionStore sessions) {

        router.add("POST", "/api/auth/register", (exchange, params) -> {
            Map<String, Object> body = Json.parseObject(HttpUtil.readBody(exchange));
            User user;
            try {
                user = users.register(
                        Json.getString(body, "username"),
                        Json.getString(body, "password"),
                        Json.getString(body, "displayName"));
            } catch (UserStore.ValidationException e) {
                HttpUtil.sendJson(exchange, 400, Json.obj("error", e.getMessage()));
                return;
            }
            System.out.println("[AUTH] registered username=" + user.username());
            HttpUtil.sendJson(exchange, 201, Json.obj(
                    "token", sessions.create(user.userId()),
                    "user", user.toPublicJson()));
        });

        router.add("POST", "/api/auth/login", (exchange, params) -> {
            Map<String, Object> body = Json.parseObject(HttpUtil.readBody(exchange));
            Optional<User> user = users.authenticate(
                    Json.getString(body, "username"),
                    Json.getString(body, "password"));
            if (user.isEmpty()) {
                // Same message for "no such user" and "wrong password" - anything else lets an
                // attacker enumerate which usernames exist.
                HttpUtil.sendJson(exchange, 401, Json.obj("error", "Neispravno korisnicko ime ili lozinka"));
                return;
            }
            HttpUtil.sendJson(exchange, 200, Json.obj(
                    "token", sessions.create(user.get().userId()),
                    "user", user.get().toPublicJson()));
        });

        router.add("GET", "/api/auth/me", (exchange, params) -> {
            Optional<User> user = currentUser(exchange, users, sessions);
            if (user.isEmpty()) {
                HttpUtil.sendJson(exchange, 401, Json.obj("error", "Nevalidan ili istekao token"));
                return;
            }
            HttpUtil.sendJson(exchange, 200, user.get().toPublicJson());
        });

        router.add("POST", "/api/auth/logout", (exchange, params) -> {
            sessions.invalidate(SessionStore.bearerToken(exchange.getRequestHeaders().getFirst("Authorization")));
            HttpUtil.sendJson(exchange, 200, Json.obj("status", "ok"));
        });

        // Public profile directory - "who else uses this network".
        router.add("GET", "/api/users", (exchange, params) -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (User user : users.all()) out.add(user.toPublicJson());
            HttpUtil.sendJson(exchange, 200, out);
        });
    }

    /** Resolves the Authorization header to an account, or empty when the token isn't usable. */
    public static Optional<User> currentUser(com.sun.net.httpserver.HttpExchange exchange,
                                             UserStore users, SessionStore sessions) {
        String token = SessionStore.bearerToken(exchange.getRequestHeaders().getFirst("Authorization"));
        return sessions.resolve(token).flatMap(users::findById);
    }
}
