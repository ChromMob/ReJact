package me.chrommob.rejact;

import java.util.List;
import java.util.Map;

/**
 * Builders for the wire protocol: a closed set of binary operations the fixed client runtime
 * dispatches. Each op is a typed record; {@link Wire} encodes them into one binary batch frame
 * with a fixed opcode table and no key names on the wire. Values travel as data and are never
 * spliced into code, so no string can break out into executable JavaScript.
 */
public final class Ops {
    private Ops() {
    }

    public static final int TEXT = 1;
    public static final int VALUE = 2;
    public static final int ATTR = 3;
    public static final int STYLE = 4;
    public static final int CLS = 5;
    public static final int APPEND = 6;
    public static final int REPLACE = 7;
    public static final int REMOVE = 8;
    public static final int FOCUS = 9;
    public static final int SCROLL = 10;
    public static final int NAVIGATE = 11;
    public static final int COOKIE = 12;
    public static final int READ_FILE = 13;
    public static final int SCROLL_WINDOW = 14;
    public static final int PUSH_STATE = 15;
    public static final int HISTORY_BACK = 16;
    public static final int BIND = 17;
    public static final int SET_VAR = 18;

    public sealed interface Op permits Text, Value, Attrib, Css, Cls, Append, Replace, Remove,
            Focus, Scroll, Navigate, Cookie, ReadFile, ScrollWindow, Site, HistoryBack, Bind,
            SetVar {
        int opcode();
    }

    /** One dynamic event subscription travelling with an appended or replaced subtree. */
    public record EvSub(String event, boolean preventDefault) {
    }

    public record Text(String el, String text) implements Op {
        public int opcode() {
            return TEXT;
        }
    }

    public record Value(String el, String value) implements Op {
        public int opcode() {
            return VALUE;
        }
    }

    /** {@code value == null} removes the attribute. */
    public record Attrib(String el, String name, String value) implements Op {
        public int opcode() {
            return ATTR;
        }
    }

    public record Css(String el, String prop, String value) implements Op {
        public int opcode() {
            return STYLE;
        }
    }

    /** Scrolls the whole window to an absolute vertical offset (per tab, not per element). */
    public record ScrollWindow(int y) implements Op {
        public int opcode() {
            return SCROLL_WINDOW;
        }
    }

    /** Pushes a history entry in this tab without loading anything. */
    public record Site(String name, String param, boolean push) implements Op {
        public int opcode() {
            return PUSH_STATE;
        }
    }

    /** Steps this tab one back in its own history; the resulting popstate reports as onBack. */
    public record HistoryBack() implements Op {
        public int opcode() {
            return HISTORY_BACK;
        }
    }

    public record Cls(String el, List<String> add, List<String> remove) implements Op {
        public int opcode() {
            return CLS;
        }
    }

    /** Appends an escaped-rendered subtree; {@code subs} wires its dynamic listeners. */
    public record Append(String el, String html, Map<String, List<EvSub>> subs) implements Op {
        public int opcode() {
            return APPEND;
        }
    }

    public record Replace(String el, String html, Map<String, List<EvSub>> subs) implements Op {
        public int opcode() {
            return REPLACE;
        }
    }

    public record Remove(String el) implements Op {
        public int opcode() {
            return REMOVE;
        }
    }

    public record Focus(String el) implements Op {
        public int opcode() {
            return FOCUS;
        }
    }

    public record Scroll(String el) implements Op {
        public int opcode() {
            return SCROLL;
        }
    }

    public record Navigate(String href) implements Op {
        public int opcode() {
            return NAVIGATE;
        }
    }

    public record Cookie(String name, String value) implements Op {
        public int opcode() {
            return COOKIE;
        }
    }

    public record ReadFile(String el) implements Op {
        public int opcode() {
            return READ_FILE;
        }
    }

    /** Binds this element's text to a compiled {@link Expr} program evaluated client side. */
    public record Bind(String el, String program) implements Op {
        public int opcode() {
            return BIND;
        }
    }

    /** Sets a live variable, snapshotting the receipt time so expressions can age it. */
    public record SetVar(String name, int value) implements Op {
        public int opcode() {
            return SET_VAR;
        }
    }

    public static Text text(String el, String text) {
        return new Text(el, text);
    }

    public static Value value(String el, String value) {
        return new Value(el, value);
    }

    public static Attrib attr(String el, String name, String value) {
        return new Attrib(el, name, value);
    }

    public static Css style(String el, String prop, String value) {
        return new Css(el, prop, value);
    }

    public static Cls classes(String el, List<String> add, List<String> remove) {
        return new Cls(el, add, remove);
    }

    public static Append append(String el, String html, Map<String, List<EvSub>> subs) {
        return new Append(el, html, subs);
    }

    public static Replace replace(String el, String html, Map<String, List<EvSub>> subs) {
        return new Replace(el, html, subs);
    }

    public static Remove remove(String el) {
        return new Remove(el);
    }

    public static Focus focus(String el) {
        return new Focus(el);
    }

    public static Scroll scroll(String el) {
        return new Scroll(el);
    }

    public static Navigate navigate(String href) {
        return new Navigate(href);
    }

    public static Cookie cookie(String name, String value) {
        return new Cookie(name, value);
    }

    public static ReadFile readFile(String el) {
        return new ReadFile(el);
    }

    public static Bind bind(String el, String program) {
        return new Bind(el, program);
    }

    public static SetVar setVar(String name, int value) {
        return new SetVar(name, value);
    }
}
