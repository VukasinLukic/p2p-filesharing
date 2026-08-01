package rs.rmt.peer.api;

import com.sun.net.httpserver.HttpServer;
import rs.rmt.peer.config.PeerConfig;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.state.PeerState;
import rs.rmt.peer.testutil.Assert;
import rs.rmt.peer.tracker.TrackerClient;
import rs.rmt.peer.transfer.DownloadManager;
import rs.rmt.peer.transfer.DownloadService;
import rs.rmt.peer.util.HttpUtil;
import rs.rmt.peer.util.Json;
import rs.rmt.peer.util.Router;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Boots the real local REST API against a fake tracker HTTP server - exercises the full JSON wiring. */
public class PeerApiServerTest {
    private HttpServer fakeTracker;
    private HttpServer peerApi;
    private String peerApiBase;
    private final HttpClient client = HttpClient.newHttpClient();

    public void testSearchProxiesTrackerAndFlagsOwnership() throws Exception {
        Path tempDir = Files.createTempDirectory("peerapi-test");
        try {
            LibraryService library = new LibraryService();

            fakeTracker = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            fakeTracker.createContext("/api/files/search", exchange -> HttpUtil.sendJson(exchange, 200, List.of(Json.obj(
                    "fileHash", "abc", "fileName", "movie.mkv", "size", 1000, "peerCount", 1))));
            fakeTracker.setExecutor(Executors.newCachedThreadPool());
            fakeTracker.start();
            String trackerUrl = "http://localhost:" + fakeTracker.getAddress().getPort();

            startPeerApi(tempDir, trackerUrl, library, "self");

            HttpResponse<String> resp = get("/api/search?q=movie");
            Assert.assertEquals(200, resp.statusCode(), "search proxy should succeed");
            List<Object> results = Json.parseArray(resp.body());
            Assert.assertEquals(1, results.size(), "one result proxied from fake tracker");
            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) results.get(0);
            Assert.assertEquals(false, first.get("alreadyOwned"), "not owned since library is empty");
        } finally {
            stopAll();
            deleteRecursively(tempDir);
        }
    }

    public void testSearchMarksOwnedFilesAlreadyOwned() throws Exception {
        Path tempDir = Files.createTempDirectory("peerapi-test");
        try {
            LibraryService library = new LibraryService();
            library.addFile("abc", "movie.mkv", 1000, tempDir.resolve("movie.mkv"));

            fakeTracker = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            fakeTracker.createContext("/api/files/search", exchange -> HttpUtil.sendJson(exchange, 200, List.of(Json.obj(
                    "fileHash", "abc", "fileName", "movie.mkv", "size", 1000, "peerCount", 1))));
            fakeTracker.setExecutor(Executors.newCachedThreadPool());
            fakeTracker.start();
            String trackerUrl = "http://localhost:" + fakeTracker.getAddress().getPort();

            startPeerApi(tempDir, trackerUrl, library, "self");

            HttpResponse<String> resp = get("/api/search?q=movie");
            List<Object> results = Json.parseArray(resp.body());
            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) results.get(0);
            Assert.assertEquals(true, first.get("alreadyOwned"), "file already in local library must be flagged owned");
        } finally {
            stopAll();
            deleteRecursively(tempDir);
        }
    }

    public void testDownloadsEndpointRejectsMissingFields() throws Exception {
        Path tempDir = Files.createTempDirectory("peerapi-test");
        try {
            startPeerApi(tempDir, "http://localhost:1", new LibraryService(), null);

            HttpResponse<String> resp = post("/api/downloads", Json.stringify(Json.obj("fileHash", "")));
            Assert.assertEquals(400, resp.statusCode(), "missing fileName must be rejected with 400");
        } finally {
            stopAll();
            deleteRecursively(tempDir);
        }
    }

    public void testDownloadsEndpointRejectsAlreadyOwnedFile() throws Exception {
        Path tempDir = Files.createTempDirectory("peerapi-test");
        try {
            LibraryService library = new LibraryService();
            library.addFile("abc", "already.bin", 10, tempDir.resolve("already.bin"));
            startPeerApi(tempDir, "http://localhost:1", library, null);

            HttpResponse<String> resp = post("/api/downloads",
                    Json.stringify(Json.obj("fileHash", "abc", "fileName", "already.bin", "size", 10)));
            Assert.assertEquals(409, resp.statusCode(), "downloading a file already in the library must be rejected");
        } finally {
            stopAll();
            deleteRecursively(tempDir);
        }
    }

    public void testStatusEndpointReportsConfiguredPorts() throws Exception {
        Path tempDir = Files.createTempDirectory("peerapi-test");
        try {
            startPeerApi(tempDir, "http://localhost:1", new LibraryService(), "peer-123");

            HttpResponse<String> resp = get("/api/status");
            Map<String, Object> body = Json.parseObject(resp.body());
            Assert.assertEquals("peer-123", body.get("peerId"), "status reflects current peerId");
        } finally {
            stopAll();
            deleteRecursively(tempDir);
        }
    }

    private void startPeerApi(Path tempDir, String trackerUrl, LibraryService library, String peerId) throws Exception {
        PeerConfig config = PeerConfig.fromArgs(new String[]{
                "--shared-dir", tempDir.resolve("shared").toString(),
                "--download-dir", tempDir.toString(),
                "--tcp-port", "0",
                "--http-port", "0",
                "--tracker-url", trackerUrl
        });
        PeerState state = new PeerState();
        state.peerId = peerId;
        TrackerClient trackerClient = new TrackerClient(trackerUrl);
        DownloadManager downloadManager = new DownloadManager();
        DownloadService downloadService = new DownloadService(tempDir, library);

        Router router = PeerApiServer.build(config, state, library, trackerClient, downloadManager, downloadService);
        peerApi = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        peerApi.createContext("/", router);
        peerApi.setExecutor(Executors.newCachedThreadPool());
        peerApi.start();
        peerApiBase = "http://localhost:" + peerApi.getAddress().getPort();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create(peerApiBase + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create(peerApiBase + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void stopAll() {
        if (peerApi != null) peerApi.stop(0);
        if (fakeTracker != null) fakeTracker.stop(0);
    }

    private void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
