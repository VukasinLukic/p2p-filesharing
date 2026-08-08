package rs.rmt.peer.share;

import rs.rmt.peer.model.ChunkManifest;
import rs.rmt.peer.testutil.Assert;
import rs.rmt.peer.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Per-chunk SHA-256 manifests: chunk maths, streaming correctness, and JSON round-trip. */
public class ChunkHasherTest {

    public void testManifestSplitsFileIntoChunksWithPartialTail() throws Exception {
        Path dir = Files.createTempDirectory("chunks-test");
        try {
            int chunkSize = 1024;
            byte[] data = randomBytes(chunkSize * 3 + 100); // 3 full chunks + a 100-byte tail
            Path file = dir.resolve("data.bin");
            Files.write(file, data);

            ChunkManifest manifest = ChunkHasher.compute("whole-hash", file, chunkSize);

            Assert.assertEquals(4, manifest.chunkCount(), "3 full chunks plus the partial tail");
            Assert.assertEquals((long) data.length, manifest.size(), "manifest records the file size");
            Assert.assertEquals(0L, manifest.chunkOffset(0), "first chunk starts at 0");
            Assert.assertEquals(3L * chunkSize, manifest.chunkOffset(3), "last chunk offset");
            Assert.assertEquals(chunkSize, manifest.chunkLength(0), "full chunk length");
            Assert.assertEquals(100, manifest.chunkLength(3), "tail chunk is short");
        } finally {
            deleteRecursively(dir);
        }
    }

    public void testChunkHashesMatchTheirOwnBytes() throws Exception {
        Path dir = Files.createTempDirectory("chunks-test");
        try {
            int chunkSize = 512;
            byte[] data = randomBytes(chunkSize * 2 + 7);
            Path file = dir.resolve("data.bin");
            Files.write(file, data);

            ChunkManifest manifest = ChunkHasher.compute("whole-hash", file, chunkSize);

            for (int i = 0; i < manifest.chunkCount(); i++) {
                int length = manifest.chunkLength(i);
                byte[] chunk = Arrays.copyOfRange(data, (int) manifest.chunkOffset(i),
                        (int) manifest.chunkOffset(i) + length);
                Assert.assertTrue(manifest.verifyChunk(i, chunk, length), "chunk " + i + " must verify");
            }
        } finally {
            deleteRecursively(dir);
        }
    }

    public void testVerifyChunkRejectsCorruptedAndMisalignedData() throws Exception {
        Path dir = Files.createTempDirectory("chunks-test");
        try {
            int chunkSize = 256;
            byte[] data = randomBytes(chunkSize * 2);
            Path file = dir.resolve("data.bin");
            Files.write(file, data);
            ChunkManifest manifest = ChunkHasher.compute("whole-hash", file, chunkSize);

            byte[] chunk0 = Arrays.copyOfRange(data, 0, chunkSize);
            chunk0[10] ^= 0xFF; // single flipped byte - the whole point of per-chunk hashing
            Assert.assertFalse(manifest.verifyChunk(0, chunk0, chunkSize), "corrupted chunk must be rejected");

            byte[] chunk1 = Arrays.copyOfRange(data, chunkSize, chunkSize * 2);
            Assert.assertFalse(manifest.verifyChunk(0, chunk1, chunkSize),
                    "correct bytes at the wrong index must be rejected");
            Assert.assertFalse(manifest.verifyChunk(0, chunk0, chunkSize - 1), "short chunk must be rejected");
            Assert.assertFalse(manifest.verifyChunk(99, chunk0, chunkSize), "index past the end must be rejected");
        } finally {
            deleteRecursively(dir);
        }
    }

    public void testEmptyFileProducesEmptyManifest() throws Exception {
        Path dir = Files.createTempDirectory("chunks-test");
        try {
            Path file = dir.resolve("empty.bin");
            Files.write(file, new byte[0]);

            ChunkManifest manifest = ChunkHasher.compute("whole-hash", file, 1024);
            Assert.assertEquals(0, manifest.chunkCount(), "an empty file has no chunks");
            Assert.assertEquals(0, ChunkManifest.chunkCount(0, 1024), "chunkCount(0) is 0, not 1");
        } finally {
            deleteRecursively(dir);
        }
    }

    public void testManifestSurvivesJsonRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("chunks-test");
        try {
            Path file = dir.resolve("data.bin");
            Files.write(file, randomBytes(2500));
            ChunkManifest original = ChunkHasher.compute("whole-hash", file, 1024);

            Map<String, Object> parsed = Json.parseObject(Json.stringify(original.toJson()));
            ChunkManifest restored = ChunkManifest.fromJson(parsed);

            Assert.assertEquals(original.fileHash(), restored.fileHash(), "fileHash survives");
            Assert.assertEquals(original.size(), restored.size(), "size survives");
            Assert.assertEquals(original.chunkSize(), restored.chunkSize(), "chunkSize survives");
            Assert.assertEquals(original.chunkHashes(), restored.chunkHashes(), "all chunk hashes survive");
        } finally {
            deleteRecursively(dir);
        }
    }

    public void testManifestIsCachedUntilTheFileChanges() throws Exception {
        Path dir = Files.createTempDirectory("chunks-test");
        try {
            Path file = dir.resolve("data.bin");
            Files.write(file, randomBytes(4096));
            ChunkHasher hasher = new ChunkHasher(1024);

            ChunkManifest first = hasher.manifestFor("h", file);
            ChunkManifest second = hasher.manifestFor("h", file);
            Assert.assertTrue(first == second, "an unchanged file must be served from the cache");

            // Rewrite with different content AND a different size so the cache key changes even on
            // filesystems with coarse mtime resolution.
            Files.write(file, randomBytes(8192));
            ChunkManifest third = hasher.manifestFor("h", file);
            Assert.assertFalse(first == third, "a changed file must be re-hashed");
            Assert.assertEquals(8, third.chunkCount(), "re-hash reflects the new size");
        } finally {
            deleteRecursively(dir);
        }
    }

    public void testDefaultChunkSizeIs512Kb() {
        Assert.assertEquals(512 * 1024, ChunkManifest.DEFAULT_CHUNK_SIZE, "default block size is 512KB");
        Assert.assertEquals(512 * 1024, new ChunkHasher().chunkSize(), "hasher uses the default block size");
    }

    private static byte[] randomBytes(int length) {
        byte[] data = new byte[length];
        new java.util.Random(7).nextBytes(data);
        return data;
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path p : paths) Files.deleteIfExists(p);
        }
    }
}
