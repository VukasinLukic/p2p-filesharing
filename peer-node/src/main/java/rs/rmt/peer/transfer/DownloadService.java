package rs.rmt.peer.transfer;

import rs.rmt.peer.model.PeerRef;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.share.SharedFolderScanner;
import rs.rmt.peer.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Downloads a file from one or more source peers, verifies it against the tracker's fileHash, and
 * falls back to alternate peers/strategies on any failure.
 *
 * Two strategies:
 *  - Multi-source: when >=2 peers offer a large-enough file, split it into byte ranges and fetch
 *    them in parallel from different peers (faster, showcases real P2P swarm behavior).
 *  - Single-source: one TCP connection, full file, SHA-256 computed in-flight. Used for small
 *    files, single-peer cases, and as the guaranteed-correct fallback if multi-source fails for
 *    any reason (a bad chunk, a dead peer, a final hash mismatch).
 */
public final class DownloadService {
    /** Below this size the overhead of extra parallel connections isn't worth it. */
    private static final long MULTI_SOURCE_MIN_SIZE = 1_000_000L;
    private static final int MAX_PARALLEL_SOURCES = 4;

    private final Path downloadDir;
    private final LibraryService library;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DownloadService(Path downloadDir, LibraryService library) {
        this.downloadDir = downloadDir;
        this.library = library;
    }

    public void startAsync(DownloadManager.DownloadTask task, List<PeerRef> candidatePeers) {
        executor.submit(() -> runDownload(task, candidatePeers));
    }

    private void runDownload(DownloadManager.DownloadTask task, List<PeerRef> candidatePeers) {
        System.out.println("[Download] starting '" + task.fileName + "' from " + candidatePeers.size()
                + " candidate(s): " + candidatePeers);
        if (candidatePeers.isEmpty()) {
            task.status = DownloadManager.Status.FAILED;
            task.errorMessage = "No peers available for this file";
            return;
        }

        if (candidatePeers.size() >= 2 && task.size >= MULTI_SOURCE_MIN_SIZE) {
            boolean ok = attemptMultiSourceDownload(task, candidatePeers);
            if (ok) return;
            System.out.println("[Download] multi-source path did not complete cleanly, falling back to single-source for " + task.fileName);
            task.reset();
            task.status = DownloadManager.Status.IN_PROGRESS;
        }

        String lastError = null;
        for (PeerRef peer : candidatePeers) {
            try {
                System.out.println("[Download] connecting to " + peer.host() + ":" + peer.port()
                        + " for " + task.fileName);
                attemptSingleSourceDownload(task, peer);
                if (task.status == DownloadManager.Status.COMPLETED) {
                    task.errorMessage = null; // clear any transient failure text left over from earlier attempts
                    return;
                }
                // A hash mismatch means THIS peer's copy is bad, not necessarily every peer's copy -
                // remember why, then reset and keep trying the remaining candidates.
                if (task.status == DownloadManager.Status.FAILED) {
                    lastError = task.errorMessage;
                    System.err.println("[Download] " + peer.host() + ":" + peer.port() + " gave a bad copy (" +
                            lastError + "), trying next peer if any");
                    task.reset();
                    task.status = DownloadManager.Status.IN_PROGRESS;
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                System.err.println("[Download] attempt from " + peer.host() + ":" + peer.port() + " failed: " + lastError);
            }
        }

        if (task.status != DownloadManager.Status.COMPLETED) {
            task.status = DownloadManager.Status.FAILED;
            task.errorMessage = lastError != null ? lastError : "All known peers failed or were unreachable";
        }
    }

    // ---------- Single-source (one connection, full file, in-flight hashing) ----------

    private void attemptSingleSourceDownload(DownloadManager.DownloadTask task, PeerRef peer) throws IOException, NoSuchAlgorithmException {
        Files.createDirectories(downloadDir);
        String safeName = Paths.get(task.fileName).getFileName().toString();
        Path partPath = downloadDir.resolve(safeName + ".part");

        try (Socket socket = new Socket()) {
            System.out.println("[Download] TCP connect -> " + peer.host() + ":" + peer.port());
            socket.connect(new InetSocketAddress(peer.host(), peer.port()), 5000);
            System.out.println("[Download] TCP connected -> " + peer.host() + ":" + peer.port());
            // A peer that accepts the connection but then goes silent (crashed, network partition)
            // must not hang this download forever - fail fast and let the caller try the next peer.
            socket.setSoTimeout(15_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            TransferProtocol.writeRawLine(out, Json.stringify(Json.obj(
                    "type", TransferProtocol.TYPE_FILE_REQUEST,
                    "fileHash", task.fileHash)));

            String responseLine = TransferProtocol.readRawLine(in);
            Map<String, Object> response = Json.parseObject(responseLine);
            String status = Json.getString(response, "status");
            if (!TransferProtocol.STATUS_OK.equals(status)) {
                throw new IOException("peer responded: " + status);
            }
            long size = Json.getLong(response, "size");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (OutputStream fileOut = Files.newOutputStream(partPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[TransferProtocol.CHUNK_SIZE];
                long remaining = size;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = in.read(buffer, 0, toRead);
                    if (read == -1) throw new IOException("connection closed early (" + remaining + " bytes remaining)");
                    fileOut.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    task.addBytes(read);
                    remaining -= read;
                }
            }

            task.status = DownloadManager.Status.VERIFYING;
            String computedHash = SharedFolderScanner.toHex(digest.digest());
            if (!computedHash.equalsIgnoreCase(task.fileHash)) {
                Files.deleteIfExists(partPath);
                task.status = DownloadManager.Status.FAILED;
                task.errorMessage = "Hash mismatch - file corrupted";
                return;
            }

            Path finalPath = downloadDir.resolve(safeName);
            Files.move(partPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            library.addFile(task.fileHash, task.fileName, size, finalPath);
            task.status = DownloadManager.Status.COMPLETED;
            System.out.println("[Download] completed " + safeName + " from " + peer.host() + ":" + peer.port());
        }
    }

    // ---------- Multi-source (parallel byte ranges from different peers) ----------

    private boolean attemptMultiSourceDownload(DownloadManager.DownloadTask task, List<PeerRef> peers) {
        int partCount = Math.min(peers.size(), MAX_PARALLEL_SOURCES);
        long size = task.size;

        Path partPath;
        try {
            Files.createDirectories(downloadDir);
            String safeName = Paths.get(task.fileName).getFileName().toString();
            partPath = downloadDir.resolve(safeName + ".part");
            // Pre-size the file so each chunk thread can write to its own byte range independently.
            try (RandomAccessFile raf = new RandomAccessFile(partPath.toFile(), "rw")) {
                raf.setLength(size);
            }
        } catch (IOException e) {
            System.err.println("[Download] could not prepare multi-source part file: " + e.getMessage());
            return false;
        }

        List<long[]> ranges = new ArrayList<>(); // {offset, length}
        long baseChunk = size / partCount;
        long offset = 0;
        for (int i = 0; i < partCount; i++) {
            long length = (i == partCount - 1) ? (size - offset) : baseChunk;
            ranges.add(new long[]{offset, length});
            offset += length;
        }

        ExecutorService chunkExecutor = Executors.newFixedThreadPool(partCount);
        List<Future<?>> futures = new ArrayList<>();
        Path finalPartPath = partPath;
        for (int i = 0; i < partCount; i++) {
            long[] range = ranges.get(i);
            PeerRef peer = peers.get(i);
            futures.add(chunkExecutor.submit((Callable<Void>) () -> {
                downloadRange(task, peer, finalPartPath, range[0], range[1]);
                return null;
            }));
        }

        boolean allOk = true;
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.err.println("[Download] a chunk failed during multi-source download: " + cause.getMessage());
                allOk = false;
            }
        }
        chunkExecutor.shutdownNow();

        if (!allOk) {
            deleteQuietly(partPath);
            return false;
        }

        try {
            task.status = DownloadManager.Status.VERIFYING;
            String computedHash = SharedFolderScanner.sha256(partPath);
            if (!computedHash.equalsIgnoreCase(task.fileHash)) {
                deleteQuietly(partPath);
                System.err.println("[Download] multi-source assembly failed hash verification for " + task.fileName);
                return false;
            }
            String safeName = Paths.get(task.fileName).getFileName().toString();
            Path finalPath = downloadDir.resolve(safeName);
            Files.move(partPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            library.addFile(task.fileHash, task.fileName, size, finalPath);
            task.status = DownloadManager.Status.COMPLETED;
            System.out.println("[Download] multi-source completed " + safeName + " using " + partCount + " parallel source(s)");
            return true;
        } catch (IOException e) {
            deleteQuietly(partPath);
            System.err.println("[Download] multi-source verification error: " + e.getMessage());
            return false;
        }
    }

    private void downloadRange(DownloadManager.DownloadTask task, PeerRef peer, Path partPath, long offset, long length) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peer.host(), peer.port()), 5000);
            socket.setSoTimeout(15_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            TransferProtocol.writeRawLine(out, Json.stringify(Json.obj(
                    "type", TransferProtocol.TYPE_RANGE_REQUEST,
                    "fileHash", task.fileHash,
                    "offset", offset,
                    "length", length)));

            String responseLine = TransferProtocol.readRawLine(in);
            Map<String, Object> response = Json.parseObject(responseLine);
            if (!TransferProtocol.STATUS_OK.equals(Json.getString(response, "status"))) {
                throw new IOException("peer refused range request: " + response.get("status"));
            }

            try (RandomAccessFile raf = new RandomAccessFile(partPath.toFile(), "rw");
                 FileChannel channel = raf.getChannel()) {
                byte[] buffer = new byte[TransferProtocol.CHUNK_SIZE];
                long remaining = length;
                long pos = offset;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = in.read(buffer, 0, toRead);
                    if (read == -1) throw new IOException("connection closed early during range read");
                    channel.write(ByteBuffer.wrap(buffer, 0, read), pos);
                    pos += read;
                    remaining -= read;
                    task.addBytes(read);
                }
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
