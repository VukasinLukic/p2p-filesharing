package rs.rmt.tracker.registry;

import rs.rmt.tracker.model.FileMeta;
import rs.rmt.tracker.model.FileSearchResult;
import rs.rmt.tracker.model.PeerInfo;
import rs.rmt.tracker.model.PeerRef;
import rs.rmt.tracker.model.PeerSummary;
import rs.rmt.tracker.util.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 *
 * Optionally backed by a JSON file so a tracker restart doesn't force every peer to notice and
 * re-register from scratch. Not a hard requirement - peers already re-register automatically when
 * a heartbeat comes back 404 - just a convenience. Loaded entries keep their old lastSeenMillis, so
 * the regular evictDead() sweep (called from TrackerMain's scheduler) prunes anything that doesn't
 * heartbeat again within the TTL, same as it would across any other gap in heartbeats.
 */
public final class TrackerRegistry {
    private final Path storageFile;
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();

    public TrackerRegistry() {
        this(null);
    }

    public TrackerRegistry(Path storageFile) {
        this.storageFile = storageFile;
        load();
    }

    public PeerInfo register(String requestedPeerId, String host, int port) {
        String peerId = (requestedPeerId != null && !requestedPeerId.isBlank())
                ? requestedPeerId
                : UUID.randomUUID().toString();
        PeerInfo info = new PeerInfo(peerId, host, port);
        peers.put(peerId, info);
        save();
        return info;
    }

    public boolean announceFiles(String peerId, List<FileMeta> files) {
        PeerInfo info = peers.get(peerId);
        if (info == null) return false;
        info.replaceFiles(files);
        info.touch();
        save();
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
        save();
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
        int removed = before - peers.size();
        if (removed > 0) save();
        return removed;
    }

    // ---------- Persistence ----------

    private void load() {
        if (storageFile == null || !Files.exists(storageFile)) return;
        try {
            String text = Files.readString(storageFile, StandardCharsets.UTF_8);
            for (Object entry : Json.parseArray(text)) {
                @SuppressWarnings("unchecked")
                PeerInfo info = PeerInfo.fromStorageJson((Map<String, Object>) entry);
                peers.put(info.peerId(), info);
            }
            System.out.println("[Peers] loaded " + peers.size() + " peer(s) from " + storageFile);
        } catch (IOException | RuntimeException e) {
            // Same policy as UserStore: refuse to start on a corrupt file rather than silently
            // wiping it out on the next save().
            throw new IllegalStateException("Could not read peer registry " + storageFile + ": " + e.getMessage(), e);
        }
    }

    private void save() {
        if (storageFile == null) return;
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (PeerInfo info : peers.values()) serialized.add(info.toStorageJson());

        try {
            Path parent = storageFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
            Files.writeString(temp, Json.stringify(serialized), StandardCharsets.UTF_8);
            try {
                Files.move(temp, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, storageFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not persist peer registry " + storageFile, e);
        }
    }
}
