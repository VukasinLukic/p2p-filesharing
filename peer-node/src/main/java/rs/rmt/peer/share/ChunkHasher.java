package rs.rmt.peer.share;

import rs.rmt.peer.model.ChunkManifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes {@link ChunkManifest}s (SHA-256 per 512KB block) by streaming the file, so a 10GB file
 * costs one buffer, not 10GB of heap.
 *
 * Manifests are cached per file (invalidated on size/mtime change, same rule as
 * {@link SharedFolderScanner}) because a manifest is requested once per downloader, but hashing a
 * large file is expensive.
 */
public final class ChunkHasher {

    private record CacheEntry(long size, long mtime, ChunkManifest manifest) {}

    private final int chunkSize;
    private final Map<Path, CacheEntry> cache = new ConcurrentHashMap<>();

    public ChunkHasher() {
        this(ChunkManifest.DEFAULT_CHUNK_SIZE);
    }

    public ChunkHasher(int chunkSize) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be positive");
        this.chunkSize = chunkSize;
    }

    public int chunkSize() {
        return chunkSize;
    }

    /** Returns the (cached) manifest for a local file. */
    public ChunkManifest manifestFor(String fileHash, Path path) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        long size = attrs.size();
        long mtime = attrs.lastModifiedTime().toMillis();

        CacheEntry cached = cache.get(path);
        if (cached != null && cached.size() == size && cached.mtime() == mtime) {
            return cached.manifest();
        }
        ChunkManifest manifest = compute(fileHash, path, chunkSize);
        cache.put(path, new CacheEntry(size, mtime, manifest));
        return manifest;
    }

    public static ChunkManifest compute(String fileHash, Path path, int chunkSize) throws IOException {
        long size = Files.size(path);
        List<String> hashes = new ArrayList<>(ChunkManifest.chunkCount(size, chunkSize));
        byte[] buffer = new byte[chunkSize];

        try (InputStream in = Files.newInputStream(path)) {
            int filled;
            while ((filled = readFully(in, buffer)) > 0) {
                hashes.add(sha256Hex(buffer, 0, filled));
            }
        }
        return new ChunkManifest(fileHash, size, chunkSize, hashes);
    }

    /** InputStream.read may return short reads; a chunk hash is only correct over a full block. */
    private static int readFully(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read == -1) break;
            total += read;
        }
        return total;
    }

    public static String sha256Hex(byte[] data, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data, offset, length);
            return SharedFolderScanner.toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
