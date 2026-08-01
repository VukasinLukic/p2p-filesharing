package rs.rmt.tracker;

import com.sun.net.httpserver.HttpServer;
import rs.rmt.tracker.registry.TrackerRegistry;
import rs.rmt.tracker.testutil.Assert;
import rs.rmt.tracker.util.Json;
import rs.rmt.tracker.util.Router;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Boots the real tracker router on an ephemeral port and drives it over real HTTP - no mocks. */
public class TrackerApiIntegrationTest {
    private HttpServer server;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    private void startServer() throws Exception {
        TrackerRegistry registry = new TrackerRegistry();
        Router router = TrackerMain.buildRouter(registry);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    private void stopServer() {
        if (server != null) server.stop(0);
    }

    public void testFullApiLifecycle() throws Exception {
        startServer();
        try {
            HttpResponse<String> regResp = post("/api/peers/register", Json.stringify(Json.obj("port", 9001)));
            Assert.assertEquals(200, regResp.statusCode(), "register should succeed");
            Map<String, Object> regBody = Json.parseObject(regResp.body());
            String peerId = (String) regBody.get("peerId");
            Assert.assertNotNull(peerId, "register returns a peerId");

            String files = Json.stringify(List.of(Json.obj("fileHash", "h1", "fileName", "movie.mkv", "size", 12345)));
            HttpResponse<String> announceResp = post("/api/peers/" + peerId + "/files", files);
            Assert.assertEquals(200, announceResp.statusCode(), "announce should succeed for known peer");

            HttpResponse<String> searchResp = get("/api/files/search?q=movie");
            Assert.assertEquals(200, searchResp.statusCode(), "search should succeed");
            List<Object> results = Json.parseArray(searchResp.body());
            Assert.assertEquals(1, results.size(), "search finds the announced file");

            HttpResponse<String> peersResp = get("/api/files/h1/peers");
            List<Object> peers = Json.parseArray(peersResp.body());
            Assert.assertEquals(1, peers.size(), "one peer offers this file");

            HttpResponse<String> hbResp = put("/api/peers/" + peerId + "/heartbeat");
            Assert.assertEquals(200, hbResp.statusCode(), "heartbeat should succeed for known peer");

            HttpResponse<String> delResp = delete("/api/peers/" + peerId);
            Assert.assertEquals(200, delResp.statusCode(), "unregister should succeed");

            HttpResponse<String> hbAfterDelete = put("/api/peers/" + peerId + "/heartbeat");
            Assert.assertEquals(404, hbAfterDelete.statusCode(), "heartbeat for removed peer must 404");
        } finally {
            stopServer();
        }
    }

    public void testRegisterRejectsMissingPort() throws Exception {
        startServer();
        try {
            HttpResponse<String> resp = post("/api/peers/register", Json.stringify(Json.obj("peerId", "x")));
            Assert.assertEquals(400, resp.statusCode(), "missing 'port' must be rejected with 400");
        } finally {
            stopServer();
        }
    }

    public void testRegisterRejectsOutOfRangePort() throws Exception {
        startServer();
        try {
            HttpResponse<String> resp = post("/api/peers/register", Json.stringify(Json.obj("port", 99999)));
            Assert.assertEquals(400, resp.statusCode(), "out-of-range port must be rejected with 400");
        } finally {
            stopServer();
        }
    }

    public void testAnnounceForUnknownPeerReturns404() throws Exception {
        startServer();
        try {
            String files = Json.stringify(List.of(Json.obj("fileHash", "h1", "fileName", "a.txt", "size", 1)));
            HttpResponse<String> resp = post("/api/peers/does-not-exist/files", files);
            Assert.assertEquals(404, resp.statusCode(), "announcing for unknown peer must 404");
        } finally {
            stopServer();
        }
    }

    public void testUnknownRouteReturns404() throws Exception {
        startServer();
        try {
            HttpResponse<String> resp = get("/api/does-not-exist");
            Assert.assertEquals(404, resp.statusCode(), "unknown route must 404");
        } finally {
            stopServer();
        }
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
                .PUT(HttpRequest.BodyPublishers.noBody()).build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).DELETE().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
