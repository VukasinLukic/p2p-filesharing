package rs.rmt.tracker.model;

import rs.rmt.tracker.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Mutable, thread-safe holder for a registered peer's state in the tracker's in-memory registry. */
public final class PeerInfo {
    private final String peerId;
    private final String host;
    private final int port;
    private volatile long lastSeenMillis;
    private final Map<String, FileMeta> files = new ConcurrentHashMap<>();

    public PeerInfo(String peerId, String host, int port) {
        this(peerId, host, port, System.currentTimeMillis());
    }

    public PeerInfo(String peerId, String host, int port, long lastSeenMillis) {
        this.peerId = peerId;
        this.host = host;
        this.port = port;
        this.lastSeenMillis = lastSeenMillis;
    }

    public String peerId() { return peerId; }
    public String host() { return host; }
    public int port() { return port; }
    public long lastSeenMillis() { return lastSeenMillis; }
    public Map<String, FileMeta> files() { return files; }

    public void touch() {
        this.lastSeenMillis = System.currentTimeMillis();
    }

    public void replaceFiles(Iterable<FileMeta> newFiles) {
        files.clear();
        for (FileMeta f : newFiles) {
            files.put(f.fileHash(), f);
        }
    }

    public Map<String, Object> toStorageJson() {
        List<Map<String, Object>> filesJson = new ArrayList<>();
        for (FileMeta f : files.values()) {
            filesJson.add(Json.obj("fileHash", f.fileHash(), "fileName", f.fileName(), "size", f.size()));
        }
        return Json.obj("peerId", peerId, "host", host, "port", port,
                "lastSeenMillis", lastSeenMillis, "files", filesJson);
    }

    @SuppressWarnings("unchecked")
    public static PeerInfo fromStorageJson(Map<String, Object> json) {
        PeerInfo info = new PeerInfo(
                Json.getString(json, "peerId"),
                Json.getString(json, "host"),
                Json.getInt(json, "port"),
                Json.getLong(json, "lastSeenMillis", 0));
        Object rawFiles = json.get("files");
        if (rawFiles instanceof List<?> list) {
            List<FileMeta> files = new ArrayList<>();
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                files.add(new FileMeta(Json.getString(m, "fileHash"), Json.getString(m, "fileName"),
                        Json.getLong(m, "size", 0)));
            }
            info.replaceFiles(files);
        }
        return info;
    }
}
