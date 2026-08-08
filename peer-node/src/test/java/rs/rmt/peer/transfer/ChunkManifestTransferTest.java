package rs.rmt.peer.transfer;

import rs.rmt.peer.model.ChunkManifest;
import rs.rmt.peer.model.PeerRef;
import rs.rmt.peer.share.ChunkHasher;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.share.SharedFolderScanner;
import rs.rmt.peer.testutil.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/** Fetches a per-chunk manifest from a real FileServer over the real TCP protocol. */
public class ChunkManifestTransferTest {

    public void testManifestIsServedOverTcpAndVerifiesTheFilesChunks() throws Exception {
        Path seedDir = Files.createTempDirectory("manifest-seed");
        FileServer server = null;
        try {
            int chunkSize = 1024;
            byte[] content = randomBytes(chunkSize * 4 + 321);
            Path file = seedDir.resolve("clip.mov");
            Files.write(file, content);
            String fileHash = SharedFolderScanner.sha256(file);

            LibraryService library = new LibraryService();
            library.addFile(fileHash, "clip.mov", content.length, file);
            server = startSeeder(library, new ChunkHasher(chunkSize));

            ChunkManifest manifest = ChunkManifestClient.fetch(
                    new PeerRef("seed", "localhost", server.port()), fileHash);

            Assert.assertEquals(fileHash, manifest.fileHash(), "manifest is for the requested file");
            Assert.assertEquals(chunkSize, manifest.chunkSize(), "server's block size is reported");
            Assert.assertEquals(5, manifest.chunkCount(), "4 full blocks plus the tail");

            // The received manifest must be usable to validate bytes we got from anywhere else -
            // that is the whole point of shipping it over the wire.
            for (int i = 0; i < manifest.chunkCount(); i++) {
                int length = manifest.chunkLength(i);
                byte[] chunk = Arrays.copyOfRange(content, (int) manifest.chunkOffset(i),
                        (int) manifest.chunkOffset(i) + length);
                Assert.assertTrue(manifest.verifyChunk(i, chunk, length), "chunk " + i + " verifies");
            }
        } finally {
            if (server != null) server.shutdown();
            deleteRecursively(seedDir);
        }
    }

    public void testManifestRequestForUnknownFileIsRefusedNotHung() throws Exception {
        Path seedDir = Files.createTempDirectory("manifest-seed");
        FileServer server = null;
        try {
            server = startSeeder(new LibraryService(), new ChunkHasher(1024));
            PeerRef peer = new PeerRef("seed", "localhost", server.port());

            try {
                ChunkManifestClient.fetch(peer, "no-such-hash");
                Assert.fail("a peer without the file must refuse the manifest request");
            } catch (IOException expected) {
                Assert.assertTrue(expected.getMessage().contains("no-such-hash"),
                        "error names the file that was not found: " + expected.getMessage());
            }
        } finally {
            if (server != null) server.shutdown();
            deleteRecursively(seedDir);
        }
    }

    // ---------- helpers ----------

    private static FileServer startSeeder(LibraryService library, ChunkHasher hasher) throws Exception {
        FileServer server = new FileServer(0, library, hasher);
        Thread thread = new Thread(server, "test-manifest-server");
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(50); // let the accept loop reach accept(); the port is already bound
        return server;
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new Random(11).nextBytes(data);
        return data;
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
