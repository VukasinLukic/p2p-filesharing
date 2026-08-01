package rs.rmt.tracker.model;

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
        this.peerId = peerId;
        this.host = host;
        this.port = port;
        this.lastSeenMillis = System.currentTimeMillis();
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
}
