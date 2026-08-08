package rs.rmt.peer.api;

import rs.rmt.peer.config.PeerConfig;
import rs.rmt.peer.model.ChunkManifest;
import rs.rmt.peer.model.FileMeta;
import rs.rmt.peer.model.FileSearchResult;
import rs.rmt.peer.model.PeerRef;
import rs.rmt.peer.share.ChunkHasher;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.state.PeerState;
import rs.rmt.peer.tracker.TrackerClient;
import rs.rmt.peer.tracker.TrackerSession;
import rs.rmt.peer.transfer.DownloadManager;
import rs.rmt.peer.transfer.DownloadService;
import rs.rmt.peer.util.HttpUtil;
import rs.rmt.peer.util.Json;
import rs.rmt.peer.util.Router;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Wires the local REST API (consumed exclusively by the React GUI on this same machine). */
public final class PeerApiServer {

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
                out.add(Json.obj("fileHash", f.fileHash(), "fileName", f.fileName(), "size", f.size()));
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
}
