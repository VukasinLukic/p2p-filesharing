package rs.rmt.peer.transfer;

import rs.rmt.peer.model.ChunkManifest;
import rs.rmt.peer.share.ChunkHasher;
import rs.rmt.peer.share.LibraryService;
import rs.rmt.peer.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Handles one incoming download request: reads the header, streams the requested file (or byte range), closes. */
public final class UploadHandler implements Runnable {
    private final Socket socket;
    private final LibraryService library;
    private final ChunkHasher chunkHasher;

    public UploadHandler(Socket socket, LibraryService library) {
        this(socket, library, new ChunkHasher());
    }

    public UploadHandler(Socket socket, LibraryService library, ChunkHasher chunkHasher) {
        this.socket = socket;
        this.library = library;
        this.chunkHasher = chunkHasher;
    }

    @Override
    public void run() {
        try (Socket s = socket) {
            // A slow or hung downloader must not tie up an upload-pool thread forever.
            s.setSoTimeout(30_000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();

            String requestLine = TransferProtocol.readRawLine(in);
            Map<String, Object> request = Json.parseObject(requestLine);
            String type = Json.getString(request, "type");
            String fileHash = Json.getString(request, "fileHash");

            Optional<Path> pathOpt = library.resolve(fileHash);
            if (pathOpt.isEmpty() || !Files.exists(pathOpt.get())) {
                TransferProtocol.writeRawLine(out, Json.stringify(Json.obj(
                        "type", responseTypeFor(type),
                        "status", TransferProtocol.STATUS_NOT_FOUND)));
                return;
            }

            Path path = pathOpt.get();
            long fileSize = Files.size(path);

            if (TransferProtocol.TYPE_RANGE_REQUEST.equals(type)) {
                serveRange(out, path, fileSize, request);
            } else if (TransferProtocol.TYPE_CHUNKS_REQUEST.equals(type)) {
                serveChunkManifest(out, path, fileHash);
                return; // header-only response, nothing was "served" in the transfer sense
            } else {
                serveWholeFile(out, path, fileSize, fileHash);
            }
            System.out.println("[Upload] served " + path.getFileName() + " to " + s.getRemoteSocketAddress());
        } catch (IOException e) {
            System.err.println("[Upload] error: " + e.getMessage());
        }
    }

    /** Errors must answer in the same message family the caller asked in, or it can't parse them. */
    private static String responseTypeFor(String requestType) {
        if (TransferProtocol.TYPE_RANGE_REQUEST.equals(requestType)) return TransferProtocol.TYPE_RANGE_RESPONSE;
        if (TransferProtocol.TYPE_CHUNKS_REQUEST.equals(requestType)) return TransferProtocol.TYPE_CHUNKS_RESPONSE;
        return TransferProtocol.TYPE_FILE_RESPONSE;
    }

    /** Per-chunk SHA-256 list, so a downloader can verify (and re-fetch) individual blocks. */
    private void serveChunkManifest(OutputStream out, Path path, String fileHash) throws IOException {
        ChunkManifest manifest = chunkHasher.manifestFor(fileHash, path);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", TransferProtocol.TYPE_CHUNKS_RESPONSE);
        response.put("status", TransferProtocol.STATUS_OK);
        response.putAll(manifest.toJson());
        TransferProtocol.writeRawLine(out, Json.stringify(response));
        System.out.println("[Upload] served chunk manifest for " + path.getFileName()
                + " (" + manifest.chunkCount() + " chunk(s))");
    }

    private void serveWholeFile(OutputStream out, Path path, long size, String fileHash) throws IOException {
        TransferProtocol.writeRawLine(out, Json.stringify(Json.obj(
                "type", TransferProtocol.TYPE_FILE_RESPONSE,
                "status", TransferProtocol.STATUS_OK,
                "fileHash", fileHash,
                "size", size)));

        try (InputStream fileIn = Files.newInputStream(path)) {
            byte[] buffer = new byte[TransferProtocol.CHUNK_SIZE];
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private void serveRange(OutputStream out, Path path, long fileSize, Map<String, Object> request) throws IOException {
        long offset = Json.getLong(request, "offset");
        long length = Json.getLong(request, "length");
        if (offset < 0 || length <= 0 || offset + length > fileSize) {
            TransferProtocol.writeRawLine(out, Json.stringify(Json.obj(
                    "type", TransferProtocol.TYPE_RANGE_RESPONSE,
                    "status", TransferProtocol.STATUS_NOT_FOUND)));
            return;
        }

        TransferProtocol.writeRawLine(out, Json.stringify(Json.obj(
                "type", TransferProtocol.TYPE_RANGE_RESPONSE,
                "status", TransferProtocol.STATUS_OK,
                "offset", offset,
                "length", length)));

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(offset);
            byte[] buffer = new byte[TransferProtocol.CHUNK_SIZE];
            long remaining = length;
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = raf.read(buffer, 0, toRead);
                if (read == -1) break;
                out.write(buffer, 0, read);
                remaining -= read;
            }
            out.flush();
        }
    }
}
