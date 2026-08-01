package rs.rmt.peer.transfer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe registry of active/finished downloads, polled by the GUI via GET /api/downloads. */
public final class DownloadManager {

    public enum Status { IN_PROGRESS, VERIFYING, COMPLETED, FAILED }

    public static final class DownloadTask {
        public final String downloadId;
        public final String fileHash;
        public final String fileName;
        public final long size;
        public final AtomicLong bytesReceived = new AtomicLong(0);
        public volatile Status status = Status.IN_PROGRESS;
        public volatile String errorMessage;

        private long lastSampleTimeMs;
        private long lastSampleBytes;
        private volatile double speedBytesPerSec = 0;

        DownloadTask(String downloadId, String fileHash, String fileName, long size) {
            this.downloadId = downloadId;
            this.fileHash = fileHash;
            this.fileName = fileName;
            this.size = size;
            this.lastSampleTimeMs = System.currentTimeMillis();
        }

        public synchronized void addBytes(long delta) {
            long total = bytesReceived.addAndGet(delta);
            long now = System.currentTimeMillis();
            long elapsed = now - lastSampleTimeMs;
            if (elapsed >= 200) {
                long bytesInWindow = total - lastSampleBytes;
                speedBytesPerSec = elapsed > 0 ? (bytesInWindow * 1000.0 / elapsed) : 0;
                lastSampleTimeMs = now;
                lastSampleBytes = total;
            }
        }

        /** Clears progress so a retry against a different peer starts its progress bar from zero. */
        public synchronized void reset() {
            bytesReceived.set(0);
            lastSampleTimeMs = System.currentTimeMillis();
            lastSampleBytes = 0;
            speedBytesPerSec = 0;
            errorMessage = null;
        }

        public double speedBytesPerSec() {
            return speedBytesPerSec;
        }

        public double progressPct() {
            if (size <= 0) return status == Status.COMPLETED ? 100.0 : 0.0;
            return Math.min(100.0, (bytesReceived.get() * 100.0) / size);
        }
    }

    private final Map<String, DownloadTask> tasks = new ConcurrentHashMap<>();

    public DownloadTask create(String fileHash, String fileName, long size) {
        String id = UUID.randomUUID().toString();
        DownloadTask task = new DownloadTask(id, fileHash, fileName, size);
        tasks.put(id, task);
        return task;
    }

    public List<DownloadTask> all() {
        return List.copyOf(tasks.values());
    }

    public DownloadTask get(String downloadId) {
        return tasks.get(downloadId);
    }
}
