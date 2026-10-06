package me.chrommob.rejact;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.chrommob.rejact.gen.EventCodes;

/**
 * Binary wire codec. Everything on the WebSocket is a binary frame with a fixed layout and a
 * closed opcode table: no repeated key names, no per-message JSON envelopes.
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
 * <p>Server to client (one frame per op batch):
 *
 * <pre>
 *   [0xB1][count: u8][op...]  op = [opcode: u8][el: u8 len + bytes][args per opcode]
 *   strings are [u16 LE len + UTF-8 bytes]; a null attribute value is u16 0xFFFF.
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
        Map<String, Object> payload = plen == 0 ? new LinkedHashMap<>()
                : Json.obj(Json.parse(new String(frame, o, plen, StandardCharsets.UTF_8)));
        return new Inbound(type, evCode, el, payload);
    }

    static byte[] encode(List<Ops.Op> ops) {
        if (ops.size() > 255) {
            // The batch count is u8: wrapping it desyncs the client's op stream silently.
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
        out.write(op.opcode());
        String el = switch (op) {
            case Ops.Text t -> t.el();
            case Ops.Value v -> v.el();
            case Ops.Attrib a -> a.el();
            case Ops.Css s -> s.el();
            case Ops.Cls c -> c.el();
            case Ops.Append a -> a.el();
            case Ops.Replace r -> r.el();
            case Ops.Remove r -> r.el();
            case Ops.Focus f -> f.el();
            case Ops.Scroll s -> s.el();
            case Ops.ReadFile r -> r.el();
            case Ops.Navigate ignored -> "";
            case Ops.Cookie ignored -> "";
            case Ops.ScrollWindow ignored -> "";
            case Ops.Site ignored -> "";
            case Ops.HistoryBack ignored -> "";
            case Ops.Bind b -> b.el();
            case Ops.SetVar ignored -> "";
        };
        writeShort(out, el);
        switch (op) {
            case Ops.Text t -> writeString(out, t.text());
            case Ops.Value v -> writeString(out, v.value());
            case Ops.Attrib a -> {
                writeString(out, a.name());
                if (a.value() == null) {
                    out.write(NULL_STR);
                    out.write(NULL_STR >>> 8);
                } else {
                    writeString(out, a.value());
                }
            }
            case Ops.Css s -> {
                writeString(out, s.prop());
                writeString(out, s.value());
            }
            case Ops.Cls c -> {
                out.write(c.add().size());
                for (String name : c.add()) {
                    writeString(out, name);
                }
                out.write(c.remove().size());
                for (String name : c.remove()) {
                    writeString(out, name);
                }
            }
            case Ops.Append a -> writeSubtree(out, a.html(), a.subs());
            case Ops.Replace r -> writeSubtree(out, r.html(), r.subs());
            case Ops.ScrollWindow w -> {
                out.write(w.y());
                out.write(w.y() >>> 8);
                out.write(w.y() >>> 16);
                out.write(w.y() >>> 24);
            }
            case Ops.Site s -> {
                writeString(out, s.name());
                writeString(out, s.param());
                out.write(s.push() ? 1 : 0);
            }
            case Ops.HistoryBack ignored -> {
            }
            case Ops.Bind b -> writeString(out, b.program());
            case Ops.SetVar v -> {
                writeString(out, v.name());
                out.write(v.value());
                out.write(v.value() >>> 8);
                out.write(v.value() >>> 16);
                out.write(v.value() >>> 24);
            }
            case Ops.Navigate n -> writeString(out, n.href());
            case Ops.Cookie c -> {
                writeString(out, c.name());
                writeString(out, c.value());
            }
            default -> {
                // remove, focus, scroll, read_file: target element only
            }
        }
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
            // The length prefix is u16: letting it wrap silently corrupts the stream
            // (the client applies a truncated subtree and misparses the rest).
            throw new IllegalArgumentException("wire string too long: " + b.length);
        }
        out.write(b.length);
        out.write(b.length >>> 8);
        out.write(b, 0, b.length);
    }
}
