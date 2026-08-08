package rs.rmt.peer.api;

import rs.rmt.peer.config.PeerConfig;
import rs.rmt.peer.model.ChunkManifest;
import rs.rmt.peer.model.FileMeta;
import rs.rmt.peer.model.FileSearchResult;
import rs.rmt.peer.model.PeerRef;
import rs.rmt.peer.share.ChunkHasher;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.share.SharedFolderScanner;
import rs.rmt.peer.state.PeerState;
import rs.rmt.peer.tracker.TrackerClient;
import rs.rmt.peer.tracker.TrackerSession;
import rs.rmt.peer.transfer.DownloadManager;
import rs.rmt.peer.transfer.DownloadService;
import rs.rmt.peer.util.HttpUtil;
import rs.rmt.peer.util.Json;
import rs.rmt.peer.util.Router;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Wires the local REST API (consumed exclusively by the React GUI on this same machine). */
public final class PeerApiServer {
    private static final long MAX_UPLOAD_BYTES = 512L * 1024 * 1024;

    public static Router build(PeerConfig config, PeerState state, LibraryService library,
                                TrackerClient trackerClient, TrackerSession trackerSession,
                                DownloadManager downloadManager, DownloadService downloadService) {
        Router router = new Router();
        ChunkHasher chunkHasher = new ChunkHasher();

        router.add("GET", "/api/search", (exchange, params) -> {
            String q = HttpUtil.queryParams(exchange).getOrDefault("q", "");
            System.out.println("[API Search] query='" + q + "' -> tracker " + config.trackerUrl);
            List<FileSearchResult> results;
            try {
                results = trackerClient.search(q);
            } catch (Exception e) {
                HttpUtil.sendJson(exchange, 502, Json.obj("error", "Tracker unreachable: " + e.getMessage()));
                return;
            }
            System.out.println("[API Search] tracker returned " + results.size() + " result(s)");
            List<Map<String, Object>> out = new ArrayList<>();
            for (FileSearchResult r : results) {
                out.add(Json.obj(
                        "fileHash", r.fileHash(),
                        "fileName", r.fileName(),
                        "size", r.size(),
                        "peerCount", r.peerCount(),
                        "alreadyOwned", library.contains(r.fileHash())));
            }
            HttpUtil.sendJson(exchange, 200, out);
        });

        router.add("POST", "/api/downloads", (exchange, params) -> {
            Map<String, Object> body = Json.parseObject(HttpUtil.readBody(exchange));
            String fileHash = Json.getString(body, "fileHash");
            String fileName = Json.getString(body, "fileName");
            long size = Json.getLong(body, "size", 0);
            System.out.println("[API Download] requested file='" + fileName + "' hash=" + fileHash + " size=" + size);

            if (fileHash == null || fileHash.isBlank() || fileName == null || fileName.isBlank()) {
                HttpUtil.sendJson(exchange, 400, Json.obj("error", "'fileHash' and 'fileName' are required"));
                return;
            }
            if (library.contains(fileHash)) {
                HttpUtil.sendJson(exchange, 409, Json.obj("error", "File already in local library"));
                return;
            }

            List<PeerRef> candidates;
            try {
                candidates = trackerClient.peersForFile(fileHash);
            } catch (Exception e) {
                HttpUtil.sendJson(exchange, 502, Json.obj("error", "Tracker unreachable: " + e.getMessage()));
                return;
            }
            candidates.removeIf(p -> p.peerId().equals(state.peerId));
            System.out.println("[API Download] tracker gave " + candidates.size() + " remote candidate(s): " + candidates);

            DownloadManager.DownloadTask task = downloadManager.create(fileHash, fileName, size);
            downloadService.startAsync(task, candidates);
            HttpUtil.sendJson(exchange, 200, Json.obj("downloadId", task.downloadId));
        });

        router.add("GET", "/api/downloads", (exchange, params) -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (DownloadManager.DownloadTask t : downloadManager.all()) {
                out.add(Json.obj(
                        "downloadId", t.downloadId,
                        "fileName", t.fileName,
                        "size", t.size,
                        "bytesReceived", t.bytesReceived.get(),
                        "progressPct", t.progressPct(),
                        "speedBytesPerSec", t.speedBytesPerSec(),
                        "status", t.status.name(),
                        "errorMessage", t.errorMessage));
            }
            HttpUtil.sendJson(exchange, 200, out);
        });

        router.add("GET", "/api/library", (exchange, params) -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (FileMeta f : library.allFiles()) {
                boolean isShared = library.resolve(f.fileHash())
                        .map(path -> path.startsWith(config.sharedDir)).orElse(false);
                out.add(Json.obj("fileHash", f.fileHash(), "fileName", f.fileName(), "size", f.size(),
                        "shared", isShared));
            }
            HttpUtil.sendJson(exchange, 200, out);
        });

        router.add("POST", "/api/library/upload", (exchange, params) -> {
            String encodedName = exchange.getRequestHeaders().getFirst("X-File-Name");
            String fileName = safeFileName(encodedName);
            if (fileName == null) {
                HttpUtil.sendJson(exchange, 400, Json.obj("error", "A valid file name is required"));
                return;
            }
            long contentLength = parseContentLength(exchange.getRequestHeaders().getFirst("Content-Length"));
            if (contentLength > MAX_UPLOAD_BYTES) {
                HttpUtil.sendJson(exchange, 413, Json.obj("error", "File is larger than 512 MB"));
                return;
            }

            Files.createDirectories(config.sharedDir);
            Path target = uniqueTarget(config.sharedDir, fileName);
            Path temp = config.sharedDir.resolve(".upload-" + UUID.randomUUID() + ".part");
            try {
                long received = copyUpload(exchange, temp);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                String hash = SharedFolderScanner.sha256(target);
                library.addFile(hash, config.sharedDir.relativize(target).toString().replace('\\', '/'), received, target);
                boolean announced = trackerSession.registerAndAnnounce();
                System.out.println("[Upload] added " + target.getFileName() + " (" + received
                        + " bytes), announced=" + announced);
                HttpUtil.sendJson(exchange, 201, Json.obj("fileHash", hash, "fileName", target.getFileName().toString(),
                        "size", received, "announced", announced));
            } catch (IOException e) {
                Files.deleteIfExists(temp);
                System.err.println("[Upload] failed for " + fileName + ": " + e.getMessage());
                HttpUtil.sendJson(exchange, 500, Json.obj("error", "Could not save file: " + e.getMessage()));
            }
        });

        router.add("GET", "/api/downloads/files", (exchange, params) -> {
            List<Map<String, Object>> out = new ArrayList<>();
            if (Files.exists(config.downloadDir)) {
                try (var files = Files.walk(config.downloadDir)) {
                    for (Path path : files.filter(Files::isRegularFile).toList()) {
                        if (path.getFileName().toString().endsWith(".part")) continue;
                        out.add(Json.obj("fileName", config.downloadDir.relativize(path).toString().replace('\\', '/'),
                                "size", Files.size(path)));
                    }
                }
            }
            HttpUtil.sendJson(exchange, 200, out);
        });

        // Per-chunk manifest for a file this peer owns. Groundwork for block-level verification
        // (noveStvari.md) and handy for demoing what the hashing actually produces.
        router.add("GET", "/api/files/{fileHash}/chunks", (exchange, params) -> {
            String fileHash = params.get("fileHash");
            Optional<Path> pathOpt = library.resolve(fileHash);
            if (pathOpt.isEmpty() || !Files.exists(pathOpt.get())) {
                HttpUtil.sendJson(exchange, 404, Json.obj("error", "File not in local library: " + fileHash));
                return;
            }
            ChunkManifest manifest = chunkHasher.manifestFor(fileHash, pathOpt.get());
            HttpUtil.sendJson(exchange, 200, manifest.toJson());
        });

        router.add("GET", "/api/status", (exchange, params) ->
                HttpUtil.sendJson(exchange, 200, statusPayload(config, state)));

        // "Refresh Connection" in the GUI's network settings: re-register + re-announce right now
        // instead of waiting up to one heartbeat interval for the peer to notice on its own.
        router.add("POST", "/api/tracker/reconnect", (exchange, params) -> {
            System.out.println("[API Reconnect] manual reconnect requested from GUI");
            boolean ok = trackerSession.forceReconnect();
            Map<String, Object> payload = statusPayload(config, state);
            payload.put("reconnected", ok);
            if (!ok) payload.put("error", "Tracker nije dostupan na " + config.trackerUrl);
            HttpUtil.sendJson(exchange, 200, payload);
        });

        return router;
    }

    private static Map<String, Object> statusPayload(PeerConfig config, PeerState state) {
        return Json.obj(
                "connectedToTracker", state.connectedToTracker.get(),
                "peerId", state.peerId,
                "tcpPort", config.tcpPort,
                "httpPort", config.httpPort,
                "trackerUrl", config.trackerUrl,
                "sharedDir", config.sharedDir.toString());
    }

    private static String safeFileName(String encodedName) {
        if (encodedName == null || encodedName.isBlank()) return null;
        try {
            String decoded = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
            Path name = Path.of(decoded).getFileName();
            if (name == null) return null;
            String value = name.toString().trim();
            return value.isEmpty() || value.equals(".") || value.equals("..") ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseContentLength(String value) {
        try {
            return value == null ? -1 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Path uniqueTarget(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) return candidate;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        int number = 2;
        do {
            candidate = directory.resolve(base + " (" + number++ + ")" + extension);
        } while (Files.exists(candidate));
        return candidate;
    }

    private static long copyUpload(com.sun.net.httpserver.HttpExchange exchange, Path target) throws IOException {
        long received = 0;
        byte[] buffer = new byte[8192];
        try (var input = exchange.getRequestBody(); var output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                received += read;
                if (received > MAX_UPLOAD_BYTES) throw new IOException("File is larger than 512 MB");
                output.write(buffer, 0, read);
            }
        }
        return received;
    }
}
