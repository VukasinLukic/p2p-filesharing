package rs.rmt.peer.transfer;

import rs.rmt.peer.testutil.Assert;

public class DownloadManagerTest {

    public void testProgressPercentageComputation() {
        DownloadManager manager = new DownloadManager();
        DownloadManager.DownloadTask task = manager.create("hash", "file.bin", 1000);
        Assert.assertEquals(0.0, task.progressPct(), "starts at 0%");

        task.addBytes(500);
        Assert.assertEquals(50.0, task.progressPct(), "50% after half the bytes");

        task.addBytes(500);
        Assert.assertEquals(100.0, task.progressPct(), "100% after all bytes");
    }

    public void testCreateGeneratesUniqueIds() {
        DownloadManager manager = new DownloadManager();
        DownloadManager.DownloadTask t1 = manager.create("h1", "a", 10);
        DownloadManager.DownloadTask t2 = manager.create("h2", "b", 10);
        Assert.assertFalse(t1.downloadId.equals(t2.downloadId), "each task gets a unique id");
    }

    public void testGetReturnsCreatedTaskAndNullForUnknown() {
        DownloadManager manager = new DownloadManager();
        DownloadManager.DownloadTask created = manager.create("h1", "a", 10);
        Assert.assertTrue(manager.get(created.downloadId) == created, "get() returns the same task instance");
        Assert.assertNull(manager.get("does-not-exist"), "unknown id returns null");
    }

    public void testAllListsEveryCreatedTask() {
        DownloadManager manager = new DownloadManager();
        manager.create("h1", "a", 10);
        manager.create("h2", "b", 20);
        Assert.assertEquals(2, manager.all().size(), "all() lists every created task");
    }

    public void testResetClearsProgressAndError() {
        DownloadManager manager = new DownloadManager();
        DownloadManager.DownloadTask task = manager.create("h1", "a", 10);
        task.addBytes(5);
        task.errorMessage = "boom";
        task.reset();
        Assert.assertEquals(0L, task.bytesReceived.get(), "bytesReceived cleared");
        Assert.assertNull(task.errorMessage, "errorMessage cleared");
        Assert.assertEquals(0.0, task.progressPct(), "progress back to 0%");
    }

    public void testProgressOnZeroSizeFileDoesNotDivideByZero() {
        DownloadManager manager = new DownloadManager();
        DownloadManager.DownloadTask task = manager.create("h1", "empty.bin", 0);
        Assert.assertEquals(0.0, task.progressPct(), "zero-size, not-yet-completed file reports 0%, not NaN");
        task.status = DownloadManager.Status.COMPLETED;
        Assert.assertEquals(100.0, task.progressPct(), "zero-size file reports 100% once marked completed");
    }
}
