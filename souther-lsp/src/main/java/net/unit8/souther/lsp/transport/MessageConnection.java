package net.unit8.souther.lsp.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The LSP base protocol transport: {@code Content-Length}-framed JSON messages over a byte stream
 * (typically stdio). The one subtle rule is that the length is a count of UTF-8 <em>bytes</em>, not
 * characters — a Japanese identifier makes the two differ — so framing is done at the byte level and
 * the JSON payload is decoded/encoded as UTF-8 exactly once, here.
 */
public final class MessageConnection {

    private final InputStream in;
    private final OutputStream out;

    public MessageConnection(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    /** Reads one framed message and returns its JSON body, or {@code null} at end of input. */
    public String read() {
        try {
            int contentLength = -1;
            while (true) {
                String line = readHeaderLine();
                if (line == null) {
                    return null;   // end of input
                }
                if (line.isEmpty()) {
                    break;         // the blank line terminates the header block
                }
                int colon = line.indexOf(':');
                if (colon >= 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                }
            }
            if (contentLength < 0) {
                throw new IllegalStateException("message header had no Content-Length");
            }
            byte[] body = readExactly(contentLength);
            return new String(body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read an LSP message", e);
        }
    }

    /** Writes one framed message. Synchronized so a response and a server notification never interleave. */
    public synchronized void write(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        try {
            out.write(header);
            out.write(body);
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write an LSP message", e);
        }
    }

    /** Reads one header line up to and including {@code \n}, returned without the trailing CRLF;
     * {@code null} at end of input before any byte. */
    private String readHeaderLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c = in.read();
        if (c == -1) {
            return null;
        }
        while (c != -1 && c != '\n') {
            if (c != '\r') {
                sb.append((char) c);
            }
            c = in.read();
        }
        return sb.toString();
    }

    private byte[] readExactly(int n) throws IOException {
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buf, read, n - read);
            if (r == -1) {
                throw new IllegalStateException("stream ended mid-message: wanted " + n
                        + " bytes, got " + read);
            }
            read += r;
        }
        return buf;
    }
}
