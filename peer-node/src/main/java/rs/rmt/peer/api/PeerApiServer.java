package rs.rmt.peer.api;

import rs.rmt.peer.config.PeerConfig;
import rs.rmt.peer.model.FileMeta;
import rs.rmt.peer.model.FileSearchResult;
import rs.rmt.peer.model.PeerRef;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.state.PeerState;
import rs.rmt.peer.tracker.TrackerClient;
import rs.rmt.peer.transfer.DownloadManager;
import rs.rmt.peer.transfer.DownloadService;
import rs.rmt.peer.util.HttpUtil;
import rs.rmt.peer.util.Json;
import rs.rmt.peer.util.Router;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Wires the local REST API (consumed exclusively by the React GUI on this same machine). */
public final class PeerApiServer {

    public static Router build(PeerConfig config, PeerState state, LibraryService library,
                                TrackerClient trackerClient, DownloadManager downloadManager,
                                DownloadService downloadService) {
        Router router = new Router();

        router.add("GET", "/api/search", (exchange, params) -> {
            String q = HttpUtil.queryParams(exchange).getOrDefault("q", "");
            List<FileSearchResult> results;
            try {
                results = trackerClient.search(q);
            } catch (Exception e) {
                HttpUtil.sendJson(exchange, 502, Json.obj("error", "Tracker unreachable: " + e.getMessage()));
                return;
            }
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

        router.add("GET", "/api/status", (exchange, params) -> HttpUtil.sendJson(exchange, 200, Json.obj(
                "connectedToTracker", state.connectedToTracker.get(),
                "peerId", state.peerId,
                "tcpPort", config.tcpPort,
                "httpPort", config.httpPort,
                "trackerUrl", config.trackerUrl,
                "sharedDir", config.sharedDir.toString())));

        return router;
    }
}
