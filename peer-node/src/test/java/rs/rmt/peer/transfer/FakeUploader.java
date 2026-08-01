package rs.rmt.peer.transfer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal scriptable fake TCP peer, used to test DownloadService against controlled/hostile server behavior. */
final class FakeUploader implements AutoCloseable {
    interface Handler {
        void handle(Socket socket) throws Exception;
    }

    private final ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    FakeUploader(Handler handler) throws IOException {
        serverSocket = new ServerSocket(0);
        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket s = serverSocket.accept();
                    pool.submit(() -> {
                        try (Socket sock = s) {
                            handler.handle(sock);
                        } catch (Exception ignored) {
                            // simulated failures are the point of some tests; nothing to report here
                        }
                    });
                } catch (IOException e) {
                    // expected once close() calls serverSocket.close()
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }
}
