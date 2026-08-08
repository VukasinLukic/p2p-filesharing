package rs.rmt.tracker;

import com.sun.net.httpserver.HttpServer;
import rs.rmt.tracker.auth.AuthRoutes;
import rs.rmt.tracker.model.FileMeta;
import rs.rmt.tracker.model.FileSearchResult;
import rs.rmt.tracker.model.PeerInfo;
import rs.rmt.tracker.model.PeerRef;
import rs.rmt.tracker.model.PeerSummary;
import rs.rmt.tracker.registry.TrackerRegistry;
import rs.rmt.tracker.users.SessionStore;
import rs.rmt.tracker.users.UserStore;
import rs.rmt.tracker.util.HttpUtil;
import rs.rmt.tracker.util.Json;
import rs.rmt.tracker.util.Router;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Central registry/tracker for the P2P file sharing network. Never touches file bytes. */
public final class TrackerMain {
    private static final int PORT = Integer.parseInt(System.getProperty("tracker.port", "8080"));
    private static final long HEARTBEAT_TTL_MILLIS = 30_000;

    public static void main(String[] args) throws IOException {
        TrackerRegistry registry = new TrackerRegistry();
        Path usersFile = Paths.get(System.getProperty("tracker.data.dir", "./data")).resolve("users.json");
        UserStore users = new UserStore(usersFile.toAbsolutePath().normalize());
        SessionStore sessions = new SessionStore();
        Router router = buildRouter(registry, users, sessions);

        ExecutorService httpExecutor = Executors.newCachedThreadPool();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", router);
        server.setExecutor(httpExecutor);
        server.start();

        System.out.println("=== P2P Tracker ===");
        System.out.println("Listening on http://0.0.0.0:" + PORT);
        System.out.println("Debug view:   http://localhost:" + PORT + "/api/peers");
        System.out.println("User store:   " + usersFile.toAbsolutePath().normalize() + " (" + users.size() + " account(s))");

        ScheduledExecutorService evictor = Executors.newSingleThreadScheduledExecutor();
        evictor.scheduleAtFixedRate(() -> {
            try {
                int removed = registry.evictDead(HEARTBEAT_TTL_MILLIS);
                if (removed > 0) System.out.println("[EVICT] removed " + removed + " dead peer(s)");
                sessions.purgeExpired();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 10, 10, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Tracker shutting down...");
            evictor.shutdownNow();
            server.stop(0);
            httpExecutor.shutdownNow();
        }));
    }

    /** Peer/file endpoints only - accounts are optional, so this overload keeps them out. */
    public static Router buildRouter(TrackerRegistry registry) {
        return buildRouter(registry, null, null);
    }

    public static Router buildRouter(TrackerRegistry registry, UserStore users, SessionStore sessions) {
        Router router = new Router();
        if (users != null && sessions != null) {
            AuthRoutes.register(router, users, sessions);
        }

        router.add("POST", "/api/peers/register", (exchange, params) -> {
            Map<String, Object> body = Json.parseObject(HttpUtil.readBody(exchange));
            int port;
            try {
                port = Json.getInt(body, "port");
            } catch (Exception e) {
                HttpUtil.sendJson(exchange, 400, Json.obj("error", "Missing or invalid 'port' field"));
                return;
            }
            if (port <= 0 || port > 65535) {
                HttpUtil.sendJson(exchange, 400, Json.obj("error", "'port' must be between 1 and 65535"));
                return;
            }
            String requestedId = Json.getString(body, "peerId");
            String host = exchange.getRemoteAddress().getAddress().getHostAddress();

            PeerInfo info = registry.register(requestedId, host, port);
            System.out.println("[REGISTER] peerId=" + info.peerId() + " host=" + info.host() + " port=" + info.port());
            HttpUtil.sendJson(exchange, 200, Json.obj("peerId", info.peerId(), "host", info.host()));
        });

        router.add("POST", "/api/peers/{peerId}/files", (exchange, params) -> {
            String peerId = params.get("peerId");
            List<FileMeta> files;
            try {
                List<Object> rawFiles = Json.parseArray(HttpUtil.readBody(exchange));
                files = new ArrayList<>();
                for (Object o : rawFiles) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) o;
                    String fileHash = Json.getString(m, "fileHash");
                    String fileName = Json.getString(m, "fileName");
                    if (fileHash == null || fileName == null) throw new IllegalArgumentException("fileHash/fileName required");
                    files.add(new FileMeta(fileHash, fileName, Json.getLong(m, "size")));
                }
            } catch (Exception e) {
                HttpUtil.sendJson(exchange, 400, Json.obj("error", "Invalid file list: " + e.getMessage()));
                return;
            }
            boolean ok = registry.announceFiles(peerId, files);
            if (!ok) {
                HttpUtil.sendJson(exchange, 404, Json.obj("error", "Unknown peer: " + peerId));
                return;
            }
            System.out.println("[ANNOUNCE] peerId=" + peerId + " files=" + files.size());
            HttpUtil.sendJson(exchange, 200, Json.obj("status", "ok", "fileCount", files.size()));
        });

        router.add("PUT", "/api/peers/{peerId}/heartbeat", (exchange, params) -> {
            boolean ok = registry.heartbeat(params.get("peerId"));
            HttpUtil.sendJson(exchange, ok ? 200 : 404, Json.obj("status", ok ? "ok" : "not-found"));
        });

        router.add("DELETE", "/api/peers/{peerId}", (exchange, params) -> {
            registry.unregister(params.get("peerId"));
            System.out.println("[UNREGISTER] peerId=" + params.get("peerId"));
            HttpUtil.sendJson(exchange, 200, Json.obj("status", "ok"));
        });

        router.add("GET", "/api/files/search", (exchange, params) -> {
            String q = HttpUtil.queryParams(exchange).getOrDefault("q", "");
            List<Map<String, Object>> results = new ArrayList<>();
            for (FileSearchResult r : registry.search(q)) {
                results.add(Json.obj("fileHash", r.fileHash(), "fileName", r.fileName(),
                        "size", r.size(), "peerCount", r.peerCount()));
            }
            HttpUtil.sendJson(exchange, 200, results);
        });

        router.add("GET", "/api/files/{fileHash}/peers", (exchange, params) -> {
            List<Map<String, Object>> results = new ArrayList<>();
            for (PeerRef r : registry.peersForFile(params.get("fileHash"))) {
                results.add(Json.obj("peerId", r.peerId(), "host", r.host(), "port", r.port()));
            }
            HttpUtil.sendJson(exchange, 200, results);
        });

        router.add("GET", "/api/peers", (exchange, params) -> {
            List<Map<String, Object>> results = new ArrayList<>();
            for (PeerSummary p : registry.allPeersSummary()) {
                results.add(Json.obj("peerId", p.peerId(), "host", p.host(), "port", p.port(),
                        "fileCount", p.fileCount(), "lastSeenAgoMs", p.lastSeenAgoMs()));
            }
            HttpUtil.sendJson(exchange, 200, results);
        });

        return router;
    }
}
