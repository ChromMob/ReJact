package me.chrommob.rejact;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal RFC 6455 codec: handshake key derivation plus frame read/write. Covers text frames,
 * continuation frames, ping/pong and close. Client frames are masked; server frames are not.
 */
final class Ws {
    static final int OP_CONT = 0x0;
    static final int OP_TEXT = 0x1;
    static final int OP_BINARY = 0x2;
    static final int OP_CLOSE = 0x8;
    static final int OP_PING = 0x9;
    static final int OP_PONG = 0xA;
    static final int MAX_FRAME = Wire.MAX_MESSAGE;

    /** Wire accounting for /_rejact/stats: application payload bytes and message counts. */
    static final AtomicLong BYTES_IN = new AtomicLong();
    static final AtomicLong BYTES_OUT = new AtomicLong();
    static final AtomicLong MSGS_IN = new AtomicLong();
    static final AtomicLong MSGS_OUT = new AtomicLong();

    private Ws() {
    }

    static String acceptKey(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void write(OutputStream out, int opcode, byte[] payload) throws IOException {
        BYTES_OUT.addAndGet(payload.length);
        MSGS_OUT.incrementAndGet();
        synchronized (out) {
            out.write(0x80 | opcode);
            int len = payload.length;
            if (len < 126) {
                out.write(len);
            } else if (len < 65536) {
                out.write(126);
                out.write(len >>> 8);
                out.write(len);
            } else {
                out.write(127);
                for (int i = 7; i >= 0; i--) {
                    out.write((int) ((long) len >>> (8 * i)));
                }
            }
            out.write(payload);
            out.flush();
        }
    }

    /** One received frame; fin=false means continuation frames follow. */
    record Frame(boolean fin, int opcode, byte[] payload) {
    }

    static Frame read(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 < 0) return null;
        int b1 = readByte(in);
        boolean fin = (b0 & 0x80) != 0;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        if (!masked || (b0 & 0x70) != 0
                || (opcode != OP_CONT && opcode != OP_TEXT && opcode != OP_BINARY
                    && opcode != OP_CLOSE && opcode != OP_PING && opcode != OP_PONG)) {
            throw new IOException("invalid client frame");
        }
        long len = b1 & 0x7F;
        if (opcode >= OP_CLOSE && (!fin || len > 125)) {
            throw new IOException("invalid control frame");
        }
        if (len == 126) {
            len = ((long) readByte(in) << 8) | readByte(in);
            if (len < 126) throw new IOException("non-canonical frame length");
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) {
                len = (len << 8) | readByte(in);
            }
        }
        if ((b1 & 0x7f) == 127 && len < 65536) throw new IOException("invalid extended frame length");
        if (opcode == OP_CLOSE && len == 1) throw new IOException("invalid close frame");
        if (len < 0 || len > MAX_FRAME) {
            throw new IOException("ws frame too large: " + len);
        }
        byte[] mask = new byte[4];
        if (masked) {
            readFully(in, mask);
        }
        byte[] payload = new byte[(int) len];
        readFully(in, payload);
        BYTES_IN.addAndGet(payload.length);
        MSGS_IN.incrementAndGet();
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= mask[i % 4];
            }
        }
        return new Frame(fin, opcode, payload);
    }

    private static int readByte(InputStream in) throws IOException {
        int value = in.read();
        if (value < 0) throw new IOException("stream closed mid-frame");
        return value;
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                throw new IOException("stream closed mid-frame");
            }
            off += n;
        }
    }
}
