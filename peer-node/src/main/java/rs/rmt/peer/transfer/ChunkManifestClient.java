package rs.rmt.peer.transfer;

import rs.rmt.peer.model.ChunkManifest;
import rs.rmt.peer.model.PeerRef;
import rs.rmt.peer.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/**
 * Fetches a peer's per-chunk SHA-256 manifest over the same TCP protocol used for transfers.
 *
 * The download path doesn't consult it yet (it still verifies the assembled file as a whole);
 * this is the client half of the per-chunk verification groundwork from noveStvari.md, so the
 * swap only needs the DownloadService side once we're ready.
 */
public final class ChunkManifestClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private ChunkManifestClient() {}

    public static ChunkManifest fetch(PeerRef peer, String fileHash) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peer.host(), peer.port()), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            TransferProtocol.writeRawLine(out, Json.stringify(Json.obj(
                    "type", TransferProtocol.TYPE_CHUNKS_REQUEST,
                    "fileHash", fileHash)));

            Map<String, Object> response = Json.parseObject(TransferProtocol.readRawLine(in));
            if (!TransferProtocol.STATUS_OK.equals(Json.getString(response, "status"))) {
                throw new IOException("peer has no chunk manifest for " + fileHash
                        + ": " + response.get("status"));
            }
            return ChunkManifest.fromJson(response);
        }
    }
}
