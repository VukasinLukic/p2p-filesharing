package rs.rmt.peer.tracker;

import rs.rmt.peer.model.FileMeta;
import rs.rmt.peer.model.FileSearchResult;
import rs.rmt.peer.model.PeerRef;
import rs.rmt.peer.util.Json;
import rs.rmt.peer.util.LocalAddress;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thin REST client for talking to the central Tracker. */
public final class TrackerClient {
    public record RegisterResult(String peerId, String host) {}

    private final String trackerUrl;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    /** Detected once: enumerating interfaces on every heartbeat re-registration would be wasteful. */
    private volatile String advertisedHost;

    public TrackerClient(String trackerUrl) {
        this.trackerUrl = trackerUrl;
    }

    public RegisterResult register(String existingPeerId, int tcpPort) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        if (existingPeerId != null) body.put("peerId", existingPeerId);
        body.put("port", tcpPort);
        // Only matters when this peer and the tracker share a machine: the request then arrives
        // over loopback and the tracker cannot see an address worth giving to other machines.
        String host = advertisedHost();
        if (host != null) body.put("host", host);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trackerUrl + "/api/peers/register"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("register failed: HTTP " + resp.statusCode());
        Map<String, Object> respBody = Json.parseObject(resp.body());
        return new RegisterResult(Json.getString(respBody, "peerId"), Json.getString(respBody, "host"));
    }

    public boolean announceFiles(String peerId, List<FileMeta> files) throws IOException, InterruptedException {
        List<Map<String, Object>> arr = new ArrayList<>();
        for (FileMeta f : files) {
            arr.add(Json.obj("fileHash", f.fileHash(), "fileName", f.fileName(), "size", f.size()));
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trackerUrl + "/api/peers/" + peerId + "/files"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(arr)))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() == 200;
    }

    public boolean heartbeat(String peerId) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trackerUrl + "/api/peers/" + peerId + "/heartbeat"))
                .timeout(Duration.ofSeconds(5))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() == 200;
    }

    @SuppressWarnings("unchecked")
    public List<FileSearchResult> search(String query) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trackerUrl + "/api/files/search?q=" + encoded))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        List<Object> raw = Json.parseArray(resp.body());
        List<FileSearchResult> results = new ArrayList<>();
        for (Object o : raw) {
            Map<String, Object> m = (Map<String, Object>) o;
            results.add(new FileSearchResult(Json.getString(m, "fileHash"), Json.getString(m, "fileName"),
                    Json.getLong(m, "size"), Json.getInt(m, "peerCount")));
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    public List<PeerRef> peersForFile(String fileHash) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trackerUrl + "/api/files/" + fileHash + "/peers"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        List<Object> raw = Json.parseArray(resp.body());
        List<PeerRef> results = new ArrayList<>();
        for (Object o : raw) {
            Map<String, Object> m = (Map<String, Object>) o;
            results.add(new PeerRef(Json.getString(m, "peerId"), Json.getString(m, "host"), Json.getInt(m, "port")));
        }
        return results;
    }

    private String advertisedHost() {
        String cached = advertisedHost;
        if (cached == null) {
            cached = LocalAddress.towards(trackerUrl);
            advertisedHost = cached;
            if (cached != null) System.out.println("[Register] advertising LAN address " + cached);
        }
        return cached;
    }

    public void unregister(String peerId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trackerUrl + "/api/peers/" + peerId))
                    .timeout(Duration.ofSeconds(3))
                    .DELETE()
                    .build();
            client.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // best-effort on shutdown
        }
    }
}
