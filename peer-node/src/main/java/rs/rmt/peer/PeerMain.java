package rs.rmt.peer;

import com.sun.net.httpserver.HttpServer;
import rs.rmt.peer.api.PeerApiServer;
import rs.rmt.peer.config.PeerConfig;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.share.SharedFolderScanner;
import rs.rmt.peer.state.PeerState;
import rs.rmt.peer.tracker.TrackerClient;
import rs.rmt.peer.tracker.TrackerSession;
import rs.rmt.peer.transfer.DownloadManager;
import rs.rmt.peer.transfer.DownloadService;
import rs.rmt.peer.transfer.FileServer;
import rs.rmt.peer.util.Router;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Entry point: wires config, TCP file server, tracker registration/heartbeat, and local REST API. */
public final class PeerMain {
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

    public static void main(String[] args) throws Exception {
        PeerConfig config = PeerConfig.fromArgs(args);
        PeerState state = new PeerState();

        System.out.println("=== P2P Peer Node ===");
        System.out.println("Shared dir:   " + config.sharedDir);
        System.out.println("Download dir: " + config.downloadDir);
        System.out.println("TCP port:     " + config.tcpPort);
        System.out.println("HTTP port:    " + config.httpPort);
        System.out.println("Tracker:      " + config.trackerUrl);
        System.out.println("[Startup] scanning shared folder before registering with tracker");

        LibraryService library = new LibraryService();
        SharedFolderScanner scanner = new SharedFolderScanner();
        library.replaceAll(scanner.scan(config.sharedDir));
        System.out.println("[Scan] " + library.allFiles().size() + " file(s) in shared dir");
        library.allFiles().forEach(file -> System.out.println("[Scan]   " + file.fileName()
                + " | " + file.size() + " bytes | " + file.fileHash()));

        FileServer fileServer = new FileServer(config.tcpPort, library);
        Thread fileServerThread = new Thread(fileServer, "file-server");
        fileServerThread.setDaemon(true);
        fileServerThread.start();

        TrackerClient trackerClient = new TrackerClient(config.trackerUrl);
        TrackerSession trackerSession = new TrackerSession(config, state, trackerClient, library);
        DownloadManager downloadManager = new DownloadManager();
        DownloadService downloadService = new DownloadService(config.downloadDir, library);

        trackerSession.registerAndAnnounce();

        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(trackerSession::heartbeat,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        ExecutorService httpExecutor = Executors.newCachedThreadPool();
        Router apiRouter = PeerApiServer.build(config, state, library, trackerClient, trackerSession,
                downloadManager, downloadService);
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", config.httpPort), 0);
        httpServer.createContext("/", apiRouter);
        httpServer.setExecutor(httpExecutor);
        httpServer.start();
        System.out.println("[HTTP] local REST API on http://localhost:" + config.httpPort);
        System.out.println("[Ready] Open http://localhost:8888/?port=" + config.httpPort
                + " (this peer's local API is port " + config.httpPort + ")");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Peer shutting down...");
            heartbeatExecutor.shutdownNow();
            if (state.peerId != null) trackerClient.unregister(state.peerId);
            fileServer.shutdown();
            httpServer.stop(0);
            httpExecutor.shutdownNow();
        }));
    }
}
