package me.chrommob.rejact;

/**
 * A tiny expression language compiled to data programs the fixed client runtime evaluates: text
 * derived from live variables and the clock, authored in pure Java, rendered client side. The
 * program is nested-array JSON of numbers and strings - never code - so nothing can break out into
 * executable JavaScript.
 *
 * <p>This is the extension point for client-side behavior: instead of new protocol opcodes per
 * feature, an app composes expressions and syncs variables ({@link Element#bindText},
 * {@link Element#setVar}). Evaluation codes: 0 literal, 1 variable, 2 now, 3 +, 4 -, 5 /, 6 %,
 * 7 concat, 8 pad2, 9 max, 10 age.
 */
public final class Expr {
    private final Object[] node;

    private Expr(Object... node) {
        this.node = node;
    }

    /** A number or text literal. */
    public static Expr lit(long value) {
        return new Expr(0, value);
    }

    public static Expr str(String text) {
        return new Expr(0, text);
    }

    /** The client's clock in milliseconds. */
    public static Expr now() {
        return new Expr(2);
    }

    /** A live variable's last synced value (0 before its first sync). */
    public static Expr var(String name) {
        return new Expr(1, name);
    }

    /** Milliseconds since a live variable was last synced - for values that age, like remaining time. */
    public static Expr age(String name) {
        return new Expr(10, name);
    }

    public Expr plus(Expr b) {
        return new Expr(3, node, b.node);
    }

    public Expr minus(Expr b) {
        return new Expr(4, node, b.node);
    }

    public Expr div(Expr b) {
        return new Expr(5, node, b.node);
    }

    public Expr mod(Expr b) {
        return new Expr(6, node, b.node);
    }

    public Expr max(Expr b) {
        return new Expr(9, node, b.node);
    }

    /** Two-digit text ("05") - zero-padded seconds or minutes. */
    public Expr pad2() {
        return new Expr(8, node);
    }

    /** Concatenates parts into text; numbers render as decimal. */
    public static Expr cat(Expr... parts) {
        Object[] ps = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            ps[i] = parts[i].node;
        }
        return new Expr(7, ps);
    }

    String toJson() {
        StringBuilder sb = new StringBuilder();
        write(sb, node);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object part) {
        if (part instanceof Object[] arr) {
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(sb, arr[i]);
            }
            sb.append(']');
        } else if (part instanceof String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') {
                    sb.append('\\').append(c);
                } else if (c < 0x20) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
            sb.append('"');
        } else {
            sb.append(part);
        }
    }
}
