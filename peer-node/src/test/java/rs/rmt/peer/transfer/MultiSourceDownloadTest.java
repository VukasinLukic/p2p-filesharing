package rs.rmt.peer.transfer;

import rs.rmt.peer.model.PeerRef;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.share.SharedFolderScanner;
import rs.rmt.peer.testutil.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Exercises the parallel-chunk multi-source path (real FileServer/UploadHandler instances, no mocks). */
public class MultiSourceDownloadTest {

    public void testDownloadSplitsAcrossMultipleRealPeersAndAssemblesCorrectly() throws Exception {
        byte[] content = randomBytes(3_000_000); // well above the 1MB multi-source threshold
        String hash = sha256Hex(content);

        Path seedDirA = Files.createTempDirectory("seedA");
        Path seedDirB = Files.createTempDirectory("seedB");
        Path seedDirC = Files.createTempDirectory("seedC");
        FileServer serverA = null, serverB = null, serverC = null;
        try {
            serverA = startSeeder(seedDirA, "movie.mkv", hash, content);
            serverB = startSeeder(seedDirB, "movie.mkv", hash, content);
            serverC = startSeeder(seedDirC, "movie.mkv", hash, content);

            Path downloadDir = Files.createTempDirectory("dl-multisource");
            try {
                DownloadService service = new DownloadService(downloadDir, new LibraryService());
                DownloadManager manager = new DownloadManager();
                DownloadManager.DownloadTask task = manager.create(hash, "movie.mkv", content.length);

                List<PeerRef> peers = List.of(
                        new PeerRef("A", "127.0.0.1", serverA.port()),
                        new PeerRef("B", "127.0.0.1", serverB.port()),
                        new PeerRef("C", "127.0.0.1", serverC.port()));

                service.startAsync(task, peers);
                waitForTerminal(task);

                Assert.assertEquals(DownloadManager.Status.COMPLETED, task.status, "multi-source download must complete");
                byte[] onDisk = Files.readAllBytes(downloadDir.resolve("movie.mkv"));
                Assert.assertTrue(Arrays.equals(content, onDisk), "assembled file must exactly match the original across all chunk boundaries");
            } finally {
                deleteRecursively(downloadDir);
            }
        } finally {
            if (serverA != null) serverA.shutdown();
            if (serverB != null) serverB.shutdown();
            if (serverC != null) serverC.shutdown();
            deleteRecursively(seedDirA);
            deleteRecursively(seedDirB);
            deleteRecursively(seedDirC);
        }
    }

    public void testFallsBackToSingleSourceWhenOneChunkPeerCannotServeRanges() throws Exception {
        byte[] content = randomBytes(2_000_000);
        String hash = sha256Hex(content);

        Path seedDirGood = Files.createTempDirectory("seedGood");
        FileServer goodServer = null;
        try {
            goodServer = startSeeder(seedDirGood, "clip.mov", hash, content);

            try (FakeUploader flaky = new FakeUploader(java.net.Socket::close)) { // accepts, then hangs up on every request
                Path downloadDir = Files.createTempDirectory("dl-multisource-fallback");
                try {
                    DownloadService service = new DownloadService(downloadDir, new LibraryService());
                    DownloadManager manager = new DownloadManager();
                    DownloadManager.DownloadTask task = manager.create(hash, "clip.mov", content.length);

                    List<PeerRef> peers = List.of(
                            new PeerRef("flaky", "127.0.0.1", flaky.port()),
                            new PeerRef("good", "127.0.0.1", goodServer.port()));

                    service.startAsync(task, peers);
                    waitForTerminal(task);

                    Assert.assertEquals(DownloadManager.Status.COMPLETED, task.status,
                            "one unresponsive chunk source must not sink the download when single-source fallback can still succeed");
                    byte[] onDisk = Files.readAllBytes(downloadDir.resolve("clip.mov"));
                    Assert.assertTrue(Arrays.equals(content, onDisk), "fallback-assembled file must match the original exactly");
                } finally {
                    deleteRecursively(downloadDir);
                }
            }
        } finally {
            if (goodServer != null) goodServer.shutdown();
            deleteRecursively(seedDirGood);
        }
    }

    // ---------- helpers ----------

    private static FileServer startSeeder(Path seedDir, String fileName, String hash, byte[] content) throws Exception {
        Path filePath = seedDir.resolve(fileName);
        Files.write(filePath, content);
        LibraryService library = new LibraryService();
        library.addFile(hash, fileName, content.length, filePath);

        FileServer server = new FileServer(0, library);
        Thread t = new Thread(server, "test-file-server-" + fileName);
        t.setDaemon(true);
        t.start();
        // Give the accept loop a brief moment to actually start looping (port is already bound
        // synchronously in the constructor, so this is just for the thread to reach accept()).
        Thread.sleep(50);
        return server;
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new SecureRandom().nextBytes(data);
        return data;
    }

    private static String sha256Hex(byte[] data) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        return SharedFolderScanner.toHex(digest.digest(data));
    }

    private static void waitForTerminal(DownloadManager.DownloadTask task) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (task.status == DownloadManager.Status.COMPLETED || task.status == DownloadManager.Status.FAILED) return;
            Thread.sleep(50);
        }
        throw new AssertionError("download did not reach a terminal state within timeout (stuck at " + task.status + ")");
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
