package rs.rmt.peer.share;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Scans the shared directory and computes SHA-256 hashes via streaming (8KB buffer) so large
 * files never get fully loaded into memory. Unchanged files (same path/size/mtime) are served
 * from an in-memory cache to avoid rehashing on every scan.
 */
public final class SharedFolderScanner {

    public record ScannedFile(String fileHash, String fileName, long size, Path path) {}

    private record CacheEntry(long size, long mtime, String hash) {}

    private final ConcurrentHashMap<Path, CacheEntry> hashCache = new ConcurrentHashMap<>();

    public List<ScannedFile> scan(Path sharedDir) throws IOException {
        if (!Files.exists(sharedDir)) {
            Files.createDirectories(sharedDir);
        }
        List<ScannedFile> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sharedDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            for (Path path : files) {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                long size = attrs.size();
                long mtime = attrs.lastModifiedTime().toMillis();

                CacheEntry cached = hashCache.get(path);
                String hash;
                if (cached != null && cached.size() == size && cached.mtime() == mtime) {
                    hash = cached.hash();
                } else {
                    hash = sha256(path);
                    hashCache.put(path, new CacheEntry(size, mtime, hash));
                }

                String relName = sharedDir.relativize(path).toString().replace('\\', '/');
                result.add(new ScannedFile(hash, relName, size, path));
            }
            // Drop cache entries for files that no longer exist in this scan (deleted/moved),
            // otherwise the cache grows without bound across the lifetime of a long-running peer.
            hashCache.keySet().retainAll(files);
        }
        return result;
    }

    public static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
