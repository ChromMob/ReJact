package me.chrommob.rejact;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import me.chrommob.rejact.gen.EventCodes;

/**
 * Binary wire codec. Server → client is a batch of generic commands
 * ({@code set} / {@code call} / {@code html} / {@code del} / {@code var} / {@code bind}),
 * not a closed app-level opcode table.
 *
 * <p>Client to server (one frame per message):
 *
 * <pre>
 *   [type: u8][ev: u8][el: u8 len + bytes][payload len: u16 LE][payload JSON, UTF-8]
 *   type 1 = page load (payload {"v": {uid: value}} — the one-time values sync)
 *   type 2 = event (ev = generated event code, payload = typed event fields)
 *   type 3 = page unload
 * </pre>
 *
 * <p>Server to client (one frame per command batch):
 *
 * <pre>
 *   [0xB1][count: u8][cmd...]  cmd = [kind: u8][el: u8 len + bytes][args per kind]
 *   strings are [u16 LE len + UTF-8 bytes]; null values are u16 0xFFFF.
 * </pre>
 */
final class Wire {
    static final int LOAD = 1;
    static final int EVENT = 2;
    static final int UNLOAD = 3;
    static final int BACK = 4;
    private static final int BATCH = 0xB1;
    private static final int NULL_STR = 0xFFFF;

    private Wire() {
    }

    record Inbound(int type, int evCode, String el, Map<String, Object> payload) {
    }

    static Inbound decode(byte[] frame) {
        if (frame.length < 4) {
            throw new IllegalArgumentException("short frame");
        }
        int type = frame[0] & 0xFF;
        int evCode = frame[1] & 0xFF;
        int o = 2;
        int elLen = frame[o] & 0xFF;
        o += 1;
        String el = new String(frame, o, elLen, StandardCharsets.UTF_8);
        o += elLen;
        int plen = (frame[o] & 0xFF) | ((frame[o + 1] & 0xFF) << 8);
        o += 2;
        if (o + plen > frame.length) {
            throw new IllegalArgumentException("payload overflow");
        }
        Map<String, Object> payload = plen == 0 ? new java.util.LinkedHashMap<>()
                : Json.obj(Json.parse(new String(frame, o, plen, StandardCharsets.UTF_8)));
        return new Inbound(type, evCode, el, payload);
    }

    static byte[] encode(List<Ops.Op> ops) {
        if (ops.size() > 255) {
            throw new IllegalArgumentException("op batch too large: " + ops.size() + " (max 255)");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 + ops.size() * 16);
        out.write(BATCH);
        out.write(ops.size());
        for (Ops.Op op : ops) {
            encodeOp(out, op);
        }
        return out.toByteArray();
    }

    private static void encodeOp(ByteArrayOutputStream out, Ops.Op op) {
        out.write(op.kind());
        writeShort(out, elOf(op));
        switch (op) {
            case Ops.Set s -> {
                writeString(out, s.path());
                if (s.value() == null) {
                    out.write(NULL_STR);
                    out.write(NULL_STR >>> 8);
                } else {
                    writeString(out, s.value());
                }
            }
            case Ops.Call c -> {
                writeString(out, c.method());
                writeString(out, c.args());
            }
            case Ops.Html h -> {
                out.write(h.replace() ? 1 : 0);
                writeSubtree(out, h.html(), h.subs());
            }
            case Ops.Del ignored -> {
            }
            case Ops.Var v -> {
                writeString(out, v.name());
                out.write(v.value());
                out.write(v.value() >>> 8);
                out.write(v.value() >>> 16);
                out.write(v.value() >>> 24);
            }
            case Ops.Bind b -> writeString(out, b.program());
        }
    }

    private static String elOf(Ops.Op op) {
        return switch (op) {
            case Ops.Set s -> s.el();
            case Ops.Call c -> c.el();
            case Ops.Html h -> h.el();
            case Ops.Del d -> d.el();
            case Ops.Var ignored -> "";
            case Ops.Bind b -> b.el();
        };
    }

    private static void writeSubtree(ByteArrayOutputStream out, String html,
            Map<String, List<Ops.EvSub>> subs) {
        writeString(out, html);
        out.write(subs.size());
        for (Map.Entry<String, List<Ops.EvSub>> e : subs.entrySet()) {
            writeShort(out, e.getKey());
            out.write(e.getValue().size());
            for (Ops.EvSub sub : e.getValue()) {
                out.write(EventCodes.code(sub.event()));
                out.write(sub.preventDefault() ? 1 : 0);
            }
        }
    }

    private static void writeShort(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length > 255) {
            throw new IllegalArgumentException("short string too long: " + b.length);
        }
        out.write(b.length);
        out.write(b, 0, b.length);
    }

    private static void writeString(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length > 0xFFFF) {
            throw new IllegalArgumentException("wire string too long: " + b.length);
        }
        out.write(b.length);
        out.write(b.length >>> 8);
        out.write(b, 0, b.length);
    }
}
