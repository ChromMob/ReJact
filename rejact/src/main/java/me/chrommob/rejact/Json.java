package me.chrommob.rejact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser/writer. Writer escapes &lt;, &gt;, &amp; to unicode escapes so output is
 * safe to embed in HTML &lt;script&gt; blocks as well as on the wire. No dependencies.
 */
public final class Json {
    private Json() {}

    // ---------- writer ----------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(64);
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (v instanceof Double d) {
            if (!Double.isFinite(d)) throw new IllegalArgumentException("non-finite JSON number");
            sb.append(d.doubleValue());
        } else if (v instanceof Float f) {
            if (!Float.isFinite(f)) throw new IllegalArgumentException("non-finite JSON number");
            sb.append(f.doubleValue());
        } else if (v instanceof Number n) {
            sb.append(n.longValue());
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(v));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------- parser ----------

    public static Object parse(String text) {
        Parser parser = new Parser(java.util.Objects.requireNonNull(text, "text"));
        Object value = parser.parseValue();
        parser.skipWs();
        if (parser.pos != text.length()) throw new IllegalArgumentException("trailing JSON input");
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    public static int i(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        try {
            return v == null ? 0 : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b.booleanValue() : "true".equals(v);
    }

    private static final class Parser {
        private final String s;
        private int pos;
        private int depth;

        Parser(String s) {
            this.s = s;
        }

        Object parseValue() {
            skipWs();
            if (pos >= s.length()) throw new IllegalArgumentException("unexpected end of JSON");
            if (++depth > 64) throw new IllegalArgumentException("JSON nesting exceeds 64 levels");
            char c = s.charAt(pos);
            try {
                return switch (c) {
                    case '{' -> parseObject();
                    case '[' -> parseArray();
                    case '"' -> parseString();
                    case 't' -> parseLiteral("true", Boolean.TRUE);
                    case 'f' -> parseLiteral("false", Boolean.FALSE);
                    case 'n' -> parseLiteral("null", null);
                    default -> parseNumber();
                };
            } finally {
                depth--;
            }
        }

        private Object parseLiteral(String lit, Object value) {
            if (s.startsWith(lit, pos)) {
                pos += lit.length();
                return value;
            }
            throw new IllegalArgumentException("bad literal at " + pos);
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != ':') throw new IllegalArgumentException("expected : at " + pos);
                pos++;
                m.put(key, parseValue());
                skipWs();
                if (pos >= s.length()) throw new IllegalArgumentException("unterminated object");
                char c = s.charAt(pos++);
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("expected , at " + pos);
            }
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (pos >= s.length()) throw new IllegalArgumentException("unterminated array");
                char c = s.charAt(pos++);
                if (c == ']') return list;
                if (c != ',') throw new IllegalArgumentException("expected , at " + pos);
            }
        }

        private String parseString() {
            if (pos >= s.length() || s.charAt(pos) != '"') throw new IllegalArgumentException("expected string at " + pos);
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= s.length()) break;
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > s.length()) throw new IllegalArgumentException("short unicode escape");
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("bad escape \\" + e);
                    }
                } else {
                    if (c < 0x20) throw new IllegalArgumentException("unescaped control character");
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }

        private Object parseNumber() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            String n = s.substring(start, pos);
            if (!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
                throw new IllegalArgumentException("invalid JSON number");
            }
            if (n.contains(".") || n.contains("e") || n.contains("E")) {
                double value = Double.parseDouble(n);
                if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite JSON number");
                return value;
            }
            return Long.parseLong(n);
        }

        private void skipWs() {
            while (pos < s.length() && " \t\r\n".indexOf(s.charAt(pos)) >= 0) pos++;
        }
    }
}
