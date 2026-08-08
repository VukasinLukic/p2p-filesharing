package rs.rmt.peer.model;

import rs.rmt.peer.share.ChunkHasher;
import rs.rmt.peer.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-chunk verification data for one file: the whole-file SHA-256 plus one SHA-256 per fixed-size
 * block. Lets a downloader reject a single bad 512KB block (and re-fetch just that block from
 * another peer) instead of discovering the corruption only after assembling the whole file.
 *
 * Groundwork for the BitTorrent-style verification described in noveStvari.md: the manifest is
 * computed, served over both the REST API and the TCP protocol, and verifiable here — the
 * multi-source download path still verifies whole-file only (see README "za dalje").
 */
public record ChunkManifest(String fileHash, long size, int chunkSize, List<String> chunkHashes) {

    /** 512KB: small enough to localise corruption, large enough that manifests stay tiny. */
    public static final int DEFAULT_CHUNK_SIZE = 512 * 1024;

    public ChunkManifest {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be positive");
        chunkHashes = List.copyOf(chunkHashes);
    }

    public static int chunkCount(long size, int chunkSize) {
        if (size <= 0) return 0;
        return (int) ((size + chunkSize - 1) / chunkSize);
    }

    public int chunkCount() {
        return chunkHashes.size();
    }

    public long chunkOffset(int index) {
        requireValidIndex(index);
        return (long) index * chunkSize;
    }

    /** The last chunk is short whenever size isn't an exact multiple of chunkSize. */
    public int chunkLength(int index) {
        requireValidIndex(index);
        return (int) Math.min(chunkSize, size - chunkOffset(index));
    }

    public String chunkHash(int index) {
        requireValidIndex(index);
        return chunkHashes.get(index);
    }

    /** True when `data[0..length)` is exactly what chunk `index` of this file should contain. */
    public boolean verifyChunk(int index, byte[] data, int length) {
        if (index < 0 || index >= chunkHashes.size()) return false;
        if (length != chunkLength(index)) return false;
        return chunkHashes.get(index).equalsIgnoreCase(ChunkHasher.sha256Hex(data, 0, length));
    }

    private void requireValidIndex(int index) {
        if (index < 0 || index >= chunkHashes.size()) {
            throw new IndexOutOfBoundsException("chunk index " + index + " of " + chunkHashes.size());
        }
    }

    public Map<String, Object> toJson() {
        return Json.obj(
                "fileHash", fileHash,
                "size", size,
                "chunkSize", chunkSize,
                "chunkCount", chunkHashes.size(),
                "chunkHashes", chunkHashes);
    }

    public static ChunkManifest fromJson(Map<String, Object> json) {
        List<String> hashes = new ArrayList<>();
        Object raw = json.get("chunkHashes");
        if (raw instanceof List<?> list) {
            for (Object o : list) hashes.add(String.valueOf(o));
        }
        return new ChunkManifest(
                Json.getString(json, "fileHash"),
                Json.getLong(json, "size", 0),
                (int) Json.getLong(json, "chunkSize", DEFAULT_CHUNK_SIZE),
                hashes);
    }
}
