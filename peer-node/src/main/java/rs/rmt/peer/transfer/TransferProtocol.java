package rs.rmt.peer.transfer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Custom binary protocol over raw TCP: one JSON header line terminated by '\n',
 * followed (for FILE_RESPONSE/OK) by exactly `size` raw file bytes.
 */
public final class TransferProtocol {
    private TransferProtocol() {}

    public static final String TYPE_FILE_REQUEST = "FILE_REQUEST";
    public static final String TYPE_FILE_RESPONSE = "FILE_RESPONSE";
    /** Requests a byte range [offset, offset+length) instead of the whole file - used for multi-source parallel downloads. */
    public static final String TYPE_RANGE_REQUEST = "RANGE_REQUEST";
    public static final String TYPE_RANGE_RESPONSE = "RANGE_RESPONSE";
    /** Asks a peer for the per-chunk SHA-256 manifest of a file; the response is header-only (no body bytes). */
    public static final String TYPE_CHUNKS_REQUEST = "CHUNKS_REQUEST";
    public static final String TYPE_CHUNKS_RESPONSE = "CHUNKS_RESPONSE";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_NOT_FOUND = "NOT_FOUND";

    public static final int CHUNK_SIZE = 64 * 1024;

    /**
     * Reads a single '\n'-terminated line byte-by-byte. Deliberately NOT using a BufferedReader
     * here: a buffering reader can read ahead past the '\n' into the raw binary file bytes that
     * immediately follow the header on the same socket stream, silently corrupting the transfer.
     */
    public static String readRawLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            buf.write(b);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    public static void writeRawLine(OutputStream out, String line) throws IOException {
        out.write(line.getBytes(StandardCharsets.UTF_8));
        out.write('\n');
        out.flush();
    }
}
