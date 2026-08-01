package rs.rmt.peer.transfer;

import rs.rmt.peer.testutil.Assert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class TransferProtocolTest {

    /** The critical correctness property from the implementation plan: reading the header line
     * must not consume a single byte of the binary payload that immediately follows it. */
    public void testReadRawLineStopsExactlyAtNewlineWithoutConsumingBinaryBody() throws Exception {
        String header = "{\"type\":\"FILE_RESPONSE\",\"status\":\"OK\",\"size\":5}";
        byte[] binaryBody = new byte[]{0x00, (byte) 0xFF, 0x0A, 0x0D, 0x42}; // deliberately includes bytes that look like \n / \r

        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        combined.write(header.getBytes(StandardCharsets.UTF_8));
        combined.write('\n');
        combined.write(binaryBody);

        ByteArrayInputStream in = new ByteArrayInputStream(combined.toByteArray());
        String readHeader = TransferProtocol.readRawLine(in);
        Assert.assertEquals(header, readHeader, "header line must match exactly");

        byte[] remaining = in.readAllBytes();
        Assert.assertEquals(binaryBody.length, remaining.length, "no binary bytes must be consumed by the header read");
        for (int i = 0; i < binaryBody.length; i++) {
            Assert.assertEquals(binaryBody[i], remaining[i], "binary byte " + i + " must be untouched");
        }
    }

    public void testWriteRawLineAppendsExactlyOneNewline() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransferProtocol.writeRawLine(out, "hello");
        Assert.assertEquals("hello\n", out.toString(StandardCharsets.UTF_8), "writeRawLine appends exactly one newline");
    }

    public void testReadRawLineHandlesEmptyLine() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream("\nrest".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals("", TransferProtocol.readRawLine(in), "empty line before newline reads as empty string");
        byte[] remaining = in.readAllBytes();
        Assert.assertEquals("rest", new String(remaining, StandardCharsets.UTF_8), "bytes after the newline remain untouched");
    }

    public void testReadRawLineOnClosedStreamReturnsWhateverWasBuffered() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream("no newline here".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals("no newline here", TransferProtocol.readRawLine(in), "EOF without newline returns accumulated bytes");
    }
}
