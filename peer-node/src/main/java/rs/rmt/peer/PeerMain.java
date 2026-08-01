package rs.rmt.peer;

import com.sun.net.httpserver.HttpServer;
import rs.rmt.peer.api.PeerApiServer;
import rs.rmt.peer.config.PeerConfig;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.share.SharedFolderScanner;
import rs.rmt.peer.state.PeerState;
import rs.rmt.peer.tracker.TrackerClient;
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

        LibraryService library = new LibraryService();
        SharedFolderScanner scanner = new SharedFolderScanner();
        library.replaceAll(scanner.scan(config.sharedDir));
        System.out.println("[Scan] " + library.allFiles().size() + " file(s) in shared dir");

        FileServer fileServer = new FileServer(config.tcpPort, library);
        Thread fileServerThread = new Thread(fileServer, "file-server");
        fileServerThread.setDaemon(true);
        fileServerThread.start();

        TrackerClient trackerClient = new TrackerClient(config.trackerUrl);
        DownloadManager downloadManager = new DownloadManager();
        DownloadService downloadService = new DownloadService(config.downloadDir, library);

        registerAndAnnounce(config, state, trackerClient, library);

        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(() -> runHeartbeat(config, state, trackerClient, library),
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        ExecutorService httpExecutor = Executors.newCachedThreadPool();
        Router apiRouter = PeerApiServer.build(config, state, library, trackerClient, downloadManager, downloadService);
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(config.httpPort), 0);
        httpServer.createContext("/", apiRouter);
        httpServer.setExecutor(httpExecutor);
        httpServer.start();
        System.out.println("[HTTP] local REST API on http://localhost:" + config.httpPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Peer shutting down...");
            heartbeatExecutor.shutdownNow();
            if (state.peerId != null) trackerClient.unregister(state.peerId);
            fileServer.shutdown();
            httpServer.stop(0);
            httpExecutor.shutdownNow();
        }));
    }

    /** Registers (if needed) and makes sure the file list has been announced at least once. */
    private static void registerAndAnnounce(PeerConfig config, PeerState state, TrackerClient trackerClient, LibraryService library) {
        try {
            if (state.peerId == null) {
                TrackerClient.RegisterResult reg = trackerClient.register(null, config.tcpPort);
                state.peerId = reg.peerId();
                System.out.println("[Register] peerId=" + state.peerId + " host=" + reg.host());
            }
            trackerClient.announceFiles(state.peerId, library.allFiles());
            state.filesAnnounced.set(true);
            state.connectedToTracker.set(true);
        } catch (Exception e) {
            state.connectedToTracker.set(false);
            System.err.println("[Register/Announce] failed, will retry on next heartbeat: " + e.getMessage());
        }
    }

    private static void runHeartbeat(PeerConfig config, PeerState state, TrackerClient trackerClient, LibraryService library) {
        try {
            if (state.peerId == null || !state.filesAnnounced.get()) {
                registerAndAnnounce(config, state, trackerClient, library);
                return;
            }
            boolean ok = trackerClient.heartbeat(state.peerId);
            if (!ok) {
                System.out.println("[Heartbeat] tracker forgot us (restarted?) - re-registering");
                state.peerId = null;
                state.filesAnnounced.set(false);
                registerAndAnnounce(config, state, trackerClient, library);
            } else {
                state.connectedToTracker.set(true);
            }
        } catch (Exception e) {
            state.connectedToTracker.set(false);
            System.err.println("[Heartbeat] tracker unreachable: " + e.getMessage());
        }
    }
}
