package rs.rmt.peer.transfer;

import rs.rmt.peer.share.ChunkHasher;
import rs.rmt.peer.share.LibraryService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP listener for incoming download requests. Uses a fixed thread pool (not NIO selectors) —
 * simple, easy to reason about/debug, and satisfies "serve multiple peers in parallel without
 * blocking" for the handful of concurrent peers expected in this project (pool of 8 uploads).
 */
public final class FileServer implements Runnable {
    private static final int UPLOAD_THREAD_POOL_SIZE = 8;

    private final LibraryService library;
    private final ChunkHasher chunkHasher;
    private final ExecutorService uploadPool = Executors.newFixedThreadPool(UPLOAD_THREAD_POOL_SIZE);
    private final ServerSocket serverSocket;
    private volatile boolean running = true;

    public FileServer(int port, LibraryService library) throws IOException {
        this(port, library, new ChunkHasher());
    }

    /** Binds synchronously so a port-in-use failure surfaces immediately at construction time. */
    public FileServer(int port, LibraryService library, ChunkHasher chunkHasher) throws IOException {
        this.library = library;
        this.chunkHasher = chunkHasher;
        this.serverSocket = new ServerSocket(port);
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    @Override
    public void run() {
        System.out.println("[FileServer] listening on TCP port " + port());
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                System.out.println("[FileServer] incoming TCP connection from " + socket.getRemoteSocketAddress());
                uploadPool.submit(new UploadHandler(socket, library, chunkHasher));
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        uploadPool.shutdownNow();
    }
}
