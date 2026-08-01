package rs.rmt.tracker.registry;

import rs.rmt.tracker.model.FileMeta;
import rs.rmt.tracker.model.FileSearchResult;
import rs.rmt.tracker.model.PeerInfo;
import rs.rmt.tracker.model.PeerRef;
import rs.rmt.tracker.model.PeerSummary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central in-memory peer/file registry. The tracker is purely informational:
 * no file bytes ever pass through it, only metadata.
 */
public final class TrackerRegistry {
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();

    public PeerInfo register(String requestedPeerId, String host, int port) {
        String peerId = (requestedPeerId != null && !requestedPeerId.isBlank())
                ? requestedPeerId
                : UUID.randomUUID().toString();
        PeerInfo info = new PeerInfo(peerId, host, port);
        peers.put(peerId, info);
        return info;
    }

    public boolean announceFiles(String peerId, List<FileMeta> files) {
        PeerInfo info = peers.get(peerId);
        if (info == null) return false;
        info.replaceFiles(files);
        info.touch();
        return true;
    }

    public boolean heartbeat(String peerId) {
        PeerInfo info = peers.get(peerId);
        if (info == null) return false;
        info.touch();
        return true;
    }

    public void unregister(String peerId) {
        peers.remove(peerId);
    }

    public List<FileSearchResult> search(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        Map<String, FileMeta> firstSeen = new LinkedHashMap<>();
        Map<String, Integer> peerCounts = new LinkedHashMap<>();

        for (PeerInfo peer : peers.values()) {
            for (FileMeta file : peer.files().values()) {
                if (!q.isEmpty() && !file.fileName().toLowerCase(Locale.ROOT).contains(q)) continue;
                firstSeen.putIfAbsent(file.fileHash(), file);
                peerCounts.merge(file.fileHash(), 1, Integer::sum);
            }
        }

        List<FileSearchResult> results = new ArrayList<>();
        for (Map.Entry<String, FileMeta> entry : firstSeen.entrySet()) {
            FileMeta meta = entry.getValue();
            int count = peerCounts.get(entry.getKey());
            results.add(new FileSearchResult(meta.fileHash(), meta.fileName(), meta.size(), count));
        }
        return results;
    }

    public List<PeerRef> peersForFile(String fileHash) {
        List<PeerRef> result = new ArrayList<>();
        for (PeerInfo peer : peers.values()) {
            if (peer.files().containsKey(fileHash)) {
                result.add(new PeerRef(peer.peerId(), peer.host(), peer.port()));
            }
        }
        return result;
    }

    public List<PeerSummary> allPeersSummary() {
        long now = System.currentTimeMillis();
        List<PeerSummary> result = new ArrayList<>();
        for (PeerInfo peer : peers.values()) {
            result.add(new PeerSummary(peer.peerId(), peer.host(), peer.port(),
                    peer.files().size(), now - peer.lastSeenMillis()));
        }
        return result;
    }

    /** Removes peers that haven't sent a heartbeat within ttlMillis. Returns how many were removed. */
    public int evictDead(long ttlMillis) {
        long cutoff = System.currentTimeMillis() - ttlMillis;
        int before = peers.size();
        peers.values().removeIf(p -> p.lastSeenMillis() < cutoff);
        return before - peers.size();
    }
}
