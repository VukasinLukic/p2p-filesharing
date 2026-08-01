package rs.rmt.peer.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** Runtime configuration, parsed from CLI args so multiple peer instances can run on one machine. */
public final class PeerConfig {
    public final Path sharedDir;
    public final Path downloadDir;
    public final int tcpPort;
    public final int httpPort;
    public final String trackerUrl;

    private PeerConfig(Path sharedDir, Path downloadDir, int tcpPort, int httpPort, String trackerUrl) {
        this.sharedDir = sharedDir;
        this.downloadDir = downloadDir;
        this.tcpPort = tcpPort;
        this.httpPort = httpPort;
        this.trackerUrl = trackerUrl;
    }

    public static PeerConfig fromArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                opts.put(args[i].substring(2), args[i + 1]);
            }
        }
        String sharedDir = opts.getOrDefault("shared-dir", "./shared");
        String downloadDir = opts.getOrDefault("download-dir", "./downloads");
        int tcpPort = Integer.parseInt(opts.getOrDefault("tcp-port", "9001"));
        int httpPort = Integer.parseInt(opts.getOrDefault("http-port", "7001"));
        String trackerUrl = opts.getOrDefault("tracker-url", "http://localhost:8080");
        return new PeerConfig(
                Paths.get(sharedDir).toAbsolutePath().normalize(),
                Paths.get(downloadDir).toAbsolutePath().normalize(),
                tcpPort, httpPort, trackerUrl);
    }
}
