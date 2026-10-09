package me.chrommob.rejact;

import java.util.List;
import java.util.Map;

/**
 * Wire commands. The client is a tiny generic interpreter — not a closed table of
 * app-level opcodes. Everything the server does to the page is one of six primitives:
 *
 * <ul>
 *   <li>{@link Set} — write a property/attribute/style/class/text via a path</li>
 *   <li>{@link Call} — call a method on the target (or a host helper when the target is empty)</li>
 *   <li>{@link Html} — append or replace a rendered subtree</li>
 *   <li>{@link Del} — remove a node</li>
 *   <li>{@link Var} — set a live expression variable</li>
 *   <li>{@link Bind} — bind an element's text to an {@link Expr} program</li>
 * </ul>
 *
 * <p>High-level helpers ({@link #text}, {@link #focus}, {@link #cookie}, …) are thin
 * composites over those primitives, so new capabilities need no protocol change.
 * Values travel as data and are never spliced into code.
 */
public final class Ops {
    private Ops() {
    }

    public sealed interface Op permits Set, Call, Html, Del, Var, Bind {
        int kind();
    }

    /** One dynamic event subscription travelling with an appended or replaced subtree. */
    public record EvSub(String event, boolean preventDefault) {
    }

    public static final int SET = 0;
    public static final int CALL = 1;
    public static final int HTML = 2;
    public static final int DEL = 3;
    public static final int VAR = 4;
    public static final int BIND = 5;

    /**
     * Generic write. Path grammar:
     * <ul>
     *   <li>{@code *} — replace all child content with escaped text (drops bindings)</li>
     *   <li>{@code @name} — set attribute; {@code value == null} removes it</li>
     *   <li>{@code #prop} — CSS property</li>
     *   <li>{@code .cls} — add class; {@code value == null} removes it</li>
     *   <li>otherwise — element property ({@code value}, {@code checked}, …)</li>
     * </ul>
     */
    public record Set(String el, String path, String value) implements Op {
        public int kind() {
            return SET;
        }
    }

    /** Calls {@code method} on the target with JSON-array args. Empty target = host helper. */
    public record Call(String el, String method, String args) implements Op {
        public int kind() {
            return CALL;
        }
    }

    /** {@code replace} swaps all children; otherwise appends. {@code subs} wires listeners. */
    public record Html(String el, boolean replace, String html, Map<String, List<EvSub>> subs)
            implements Op {
        public int kind() {
            return HTML;
        }
    }

    public record Del(String el) implements Op {
        public int kind() {
            return DEL;
        }
    }

    /** Sets a live variable, snapshotting the receipt time so expressions can age it. */
    public record Var(String name, int value) implements Op {
        public int kind() {
            return VAR;
        }
    }

    /** Binds this element's text to a compiled {@link Expr} program evaluated client side. */
    public record Bind(String el, String program) implements Op {
        public int kind() {
            return BIND;
        }
    }

    // ---------- composites over the primitives ----------

    public static Set text(String el, String text) {
        return new Set(el, "*", text);
    }

    public static Set value(String el, String value) {
        return new Set(el, "value", value);
    }

    public static Set attr(String el, String name, String value) {
        return new Set(el, "@" + name, value);
    }

    public static Set style(String el, String prop, String value) {
        return new Set(el, "#" + prop, value);
    }

    public static List<Op> classes(String el, List<String> add, List<String> remove) {
        List<Op> ops = new java.util.ArrayList<>(add.size() + remove.size());
        if (!add.isEmpty()) {
            ops.add(new Call(el, "classList.add", jsonArray(add)));
        }
        if (!remove.isEmpty()) {
            ops.add(new Call(el, "classList.remove", jsonArray(remove)));
        }
        return ops;
    }

    public static Html append(String el, String html, Map<String, List<EvSub>> subs) {
        return new Html(el, false, html, subs);
    }

    public static Html replace(String el, String html, Map<String, List<EvSub>> subs) {
        return new Html(el, true, html, subs);
    }

    public static Del remove(String el) {
        return new Del(el);
    }

    /**
     * Reorders the children of {@code el} to match {@code childUids}, moving the existing nodes.
     *
     * <p>The alternative is re-rendering the list, which is how sorting a fifty-row table came to
     * cost thirty kilobytes of HTML to say nothing new. This says it in a few hundred bytes, and
     * because the nodes move rather than being replaced, the browser keeps their focus, scroll
     * position, text selection and in-flight CSS transitions. Children not named are left after
     * the named ones, in their existing order.
     */
    public static Call order(String el, List<String> childUids) {
        return new Call(el, "rj.order", jsonArray(childUids));
    }

    public static Call focus(String el) {
        return new Call(el, "focus", "[]");
    }

    public static Call scroll(String el) {
        return new Call(el, "scrollIntoView", "[{\"block\":\"end\"}]");
    }

    public static Call navigate(String href) {
        return new Call("", "navigate", jsonArray(List.of(href)));
    }

    public static Call cookie(String name, String value) {
        return new Call("", "cookie", jsonArray(List.of(name, value)));
    }

    public static Call readFile(String el) {
        return new Call(el, "readFile", "[]");
    }

    public static Call scrollWindow(int y) {
        return new Call("", "scrollTo", "[0," + y + "]");
    }

    /** {@code push} adds a history entry; otherwise the URL is replaced in place. */
    public static Call site(String name, String param, boolean push) {
        return new Call("", "site", "[" + jsonStr(name) + "," + jsonStr(param) + "," + (push ? 1 : 0) + "]");
    }

    public static Call historyBack() {
        return new Call("", "back", "[]");
    }

    public static Bind bind(String el, String program) {
        return new Bind(el, program);
    }

    public static Var setVar(String name, int value) {
        return new Var(name, value);
    }

    private static String jsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(jsonStr(values.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String jsonStr(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
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
        return sb.append('"').toString();
    }
}
