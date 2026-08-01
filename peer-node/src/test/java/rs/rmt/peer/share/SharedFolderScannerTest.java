package rs.rmt.peer.share;

import rs.rmt.peer.testutil.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class SharedFolderScannerTest {

    public void testScanComputesCorrectHashAndSize() throws Exception {
        Path tmpDir = Files.createTempDirectory("scanner-test");
        try {
            Path file = tmpDir.resolve("hello.txt");
            Files.writeString(file, "hello world");

            SharedFolderScanner scanner = new SharedFolderScanner();
            var files = scanner.scan(tmpDir);

            Assert.assertEquals(1, files.size(), "one file found");
            SharedFolderScanner.ScannedFile scanned = files.get(0);
            Assert.assertEquals("hello.txt", scanned.fileName(), "relative file name");
            Assert.assertEquals(11L, scanned.size(), "file size in bytes");
            Assert.assertEquals(SharedFolderScanner.sha256(file), scanned.fileHash(), "hash matches direct computation");
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    public void testCacheReturnsSameHashWithoutModification() throws Exception {
        Path tmpDir = Files.createTempDirectory("scanner-test");
        try {
            Path file = tmpDir.resolve("data.bin");
            Files.write(file, new byte[]{1, 2, 3, 4, 5});

            SharedFolderScanner scanner = new SharedFolderScanner();
            String hash1 = scanner.scan(tmpDir).get(0).fileHash();
            String hash2 = scanner.scan(tmpDir).get(0).fileHash();
            Assert.assertEquals(hash1, hash2, "unchanged file yields identical hash on rescan");
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    public void testHashChangesWhenContentChanges() throws Exception {
        Path tmpDir = Files.createTempDirectory("scanner-test");
        try {
            Path file = tmpDir.resolve("data.bin");
            Files.write(file, "version-1".getBytes());
            SharedFolderScanner scanner = new SharedFolderScanner();
            String hashBefore = scanner.scan(tmpDir).get(0).fileHash();

            Files.write(file, "version-2-longer-content".getBytes());
            String hashAfter = scanner.scan(tmpDir).get(0).fileHash();

            Assert.assertFalse(hashBefore.equals(hashAfter), "hash must change when file content changes");
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    public void testDeletedFileDropsOutOfNextScan() throws Exception {
        Path tmpDir = Files.createTempDirectory("scanner-test");
        try {
            Path file = tmpDir.resolve("temp.txt");
            Files.writeString(file, "temporary");
            SharedFolderScanner scanner = new SharedFolderScanner();
            Assert.assertEquals(1, scanner.scan(tmpDir).size(), "file present on first scan");

            Files.delete(file);
            Assert.assertEquals(0, scanner.scan(tmpDir).size(), "file gone after deletion + rescan");
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
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
