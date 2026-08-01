package rs.rmt.peer.transfer;

import rs.rmt.peer.model.PeerRef;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.share.SharedFolderScanner;
import rs.rmt.peer.testutil.Assert;
import rs.rmt.peer.util.Json;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Edge-case coverage for DownloadService using scriptable fake TCP peers - no real network needed. */
public class DownloadServiceTest {

    public void testSuccessfulDownloadFromSinglePeer() throws Exception {
        byte[] content = randomBytes(200_000);
        String hash = sha256Hex(content);

        try (FakeUploader good = new FakeUploader(socket -> serveGoodFile(socket, content))) {
            Path downloadDir = Files.createTempDirectory("dl-success");
            try {
                DownloadService service = new DownloadService(downloadDir, new LibraryService());
                DownloadManager manager = new DownloadManager();
                DownloadManager.DownloadTask task = manager.create(hash, "payload.bin", content.length);

                service.startAsync(task, List.of(new PeerRef("peerA", "127.0.0.1", good.port())));
                waitForTerminal(task);

                Assert.assertEquals(DownloadManager.Status.COMPLETED, task.status, "download must complete");
                Assert.assertEquals((long) content.length, task.bytesReceived.get(), "all bytes accounted for");
                byte[] onDisk = Files.readAllBytes(downloadDir.resolve("payload.bin"));
                Assert.assertTrue(Arrays.equals(content, onDisk), "downloaded content matches original bytes exactly");
            } finally {
                deleteRecursively(downloadDir);
            }
        }
    }

    public void testCorruptedDataIsRejectedAndPartFileCleanedUp() throws Exception {
        byte[] original = randomBytes(50_000);
        byte[] corrupted = original.clone();
        corrupted[corrupted.length / 2] ^= 0xFF; // flip a bit deep inside the payload
        String correctHash = sha256Hex(original); // what the "tracker" told us to expect

        try (FakeUploader bad = new FakeUploader(socket -> serveGoodFile(socket, corrupted))) {
            Path downloadDir = Files.createTempDirectory("dl-corrupt");
            try {
                DownloadService service = new DownloadService(downloadDir, new LibraryService());
                DownloadManager manager = new DownloadManager();
                DownloadManager.DownloadTask task = manager.create(correctHash, "movie.bin", original.length);

                service.startAsync(task, List.of(new PeerRef("peerA", "127.0.0.1", bad.port())));
                waitForTerminal(task);

                Assert.assertEquals(DownloadManager.Status.FAILED, task.status, "corrupted transfer must be rejected");
                Assert.assertTrue(task.errorMessage != null && task.errorMessage.contains("Hash mismatch"),
                        "error explains hash mismatch, was: " + task.errorMessage);
                Assert.assertFalse(Files.exists(downloadDir.resolve("movie.bin.part")), ".part file must be deleted after failed verification");
                Assert.assertFalse(Files.exists(downloadDir.resolve("movie.bin")), "corrupted file must never be renamed into the final name");
            } finally {
                deleteRecursively(downloadDir);
            }
        }
    }

    public void testFallsBackToNextPeerWhenFirstIsDeadOrRefuses() throws Exception {
        byte[] content = randomBytes(80_000);
        String hash = sha256Hex(content);

        try (FakeUploader deadPeer = new FakeUploader(Socket::close); // accepts then immediately hangs up
             FakeUploader goodPeer = new FakeUploader(socket -> serveGoodFile(socket, content))) {

            Path downloadDir = Files.createTempDirectory("dl-fallback");
            try {
                DownloadService service = new DownloadService(downloadDir, new LibraryService());
                DownloadManager manager = new DownloadManager();
                DownloadManager.DownloadTask task = manager.create(hash, "song.mp3", content.length);

                service.startAsync(task, List.of(
                        new PeerRef("dead", "127.0.0.1", deadPeer.port()),
                        new PeerRef("good", "127.0.0.1", goodPeer.port())));
                waitForTerminal(task);

                Assert.assertEquals(DownloadManager.Status.COMPLETED, task.status, "must fall back to the second, working peer");
                byte[] onDisk = Files.readAllBytes(downloadDir.resolve("song.mp3"));
                Assert.assertTrue(Arrays.equals(content, onDisk), "content served by the fallback peer must be intact");
            } finally {
                deleteRecursively(downloadDir);
            }
        }
    }

    public void testFallsBackAfterFirstPeerServesCorruptedCopy() throws Exception {
        byte[] good = randomBytes(60_000);
        byte[] corrupted = good.clone();
        corrupted[0] ^= 0xFF;
        String hash = sha256Hex(good);

        try (FakeUploader badPeer = new FakeUploader(socket -> serveGoodFile(socket, corrupted));
             FakeUploader goodPeer = new FakeUploader(socket -> serveGoodFile(socket, good))) {

            Path downloadDir = Files.createTempDirectory("dl-fallback-corrupt");
            try {
                DownloadService service = new DownloadService(downloadDir, new LibraryService());
                DownloadManager manager = new DownloadManager();
                DownloadManager.DownloadTask task = manager.create(hash, "clip.mov", good.length);

                service.startAsync(task, List.of(
                        new PeerRef("bad", "127.0.0.1", badPeer.port()),
                        new PeerRef("good", "127.0.0.1", goodPeer.port())));
                waitForTerminal(task);

                Assert.assertEquals(DownloadManager.Status.COMPLETED, task.status,
                        "one bad copy must not sink the whole download when a good peer is available");
                byte[] onDisk = Files.readAllBytes(downloadDir.resolve("clip.mov"));
                Assert.assertTrue(Arrays.equals(good, onDisk), "final file must be the clean copy, not the corrupted one");
            } finally {
                deleteRecursively(downloadDir);
            }
        }
    }

    public void testNoAvailablePeersFailsImmediately() throws Exception {
        Path downloadDir = Files.createTempDirectory("dl-nopeers");
        try {
            DownloadService service = new DownloadService(downloadDir, new LibraryService());
            DownloadManager manager = new DownloadManager();
            DownloadManager.DownloadTask task = manager.create("somehash", "nothing.bin", 100);

            service.startAsync(task, List.of());
            waitForTerminal(task);

            Assert.assertEquals(DownloadManager.Status.FAILED, task.status, "empty peer list must fail immediately");
            Assert.assertEquals("No peers available for this file", task.errorMessage, "clear error message for empty peer list");
        } finally {
            deleteRecursively(downloadDir);
        }
    }

    public void testAllPeersUnreachableFailsWithClearMessage() throws Exception {
        Path downloadDir = Files.createTempDirectory("dl-unreachable");
        try {
            DownloadService service = new DownloadService(downloadDir, new LibraryService());
            DownloadManager manager = new DownloadManager();
            DownloadManager.DownloadTask task = manager.create("h1", "ghost.bin", 100);

            // Port 1 is a reserved/unused low port that will refuse the connection immediately.
            service.startAsync(task, List.of(new PeerRef("ghost", "127.0.0.1", 1)));
            waitForTerminal(task);

            Assert.assertEquals(DownloadManager.Status.FAILED, task.status, "unreachable peer must fail, not hang");
        } finally {
            deleteRecursively(downloadDir);
        }
    }

    // ---------- helpers ----------

    private static void serveGoodFile(Socket socket, byte[] content) throws IOException {
        var in = socket.getInputStream();
        var out = socket.getOutputStream();
        TransferProtocol.readRawLine(in); // consume the FILE_REQUEST line
        TransferProtocol.writeRawLine(out, Json.stringify(Json.obj(
                "type", TransferProtocol.TYPE_FILE_RESPONSE,
                "status", TransferProtocol.STATUS_OK,
                "size", content.length)));
        out.write(content);
        out.flush();
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new SecureRandom().nextBytes(data);
        return data;
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return SharedFolderScanner.toHex(digest.digest(data));
    }

    private static void waitForTerminal(DownloadManager.DownloadTask task) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (task.status == DownloadManager.Status.COMPLETED || task.status == DownloadManager.Status.FAILED) return;
            Thread.sleep(50);
        }
        throw new AssertionError("download did not reach a terminal state within timeout (stuck at " + task.status + ")");
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
