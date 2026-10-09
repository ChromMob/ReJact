package me.chrommob.rejact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import me.chrommob.rejact.gen.Eventful;
import me.chrommob.rejact.gen.GlobalAttrs;

/**
 * Server-side DOM element. This object is the only currency of the API: every operation takes
 * element objects, never selector or id strings. Ids exist on the wire but are generated and
 * owned by the framework (short sequential ids, base 36).
 *
 * <p>Mutations after the element is mounted on a live {@link Page} broadcast wire operations to
 * connected views, so the same call works at build time and at runtime.
 *
 * <p>Concurrency: tree mutations and tree walks ({@link Page#renderHtml}) share one monitor per
 * page — the {@link Page} itself — so a server-side render can never interleave with a mutation
 * and emit a torn document or trip a structural ConcurrentModificationException. Unmounted
 * subtrees synchronize on themselves; they are only reachable by their builder thread.
 */
public abstract class Element<S extends Element<S>> implements Eventful<S>, GlobalAttrs<S> {
    private static final AtomicLong IDS = new AtomicLong();

    record Sub(Function<Map<String, Object>, ? extends EventPayload> parser,
            Handler<? extends EventPayload> handler, boolean preventDefault) {
    }

    static final class TextNode {
        final String value;

        TextNode(String value) {
            this.value = value;
        }
    }

    static final class RawNode {
        final String html;

        RawNode(String html) {
            this.html = html;
        }
    }

    private final String tag;
    private final boolean voidElement;
    private final String uid = Long.toString(IDS.incrementAndGet(), 36);
    private final Map<String, String> attrs = new LinkedHashMap<>();
    private final Map<String, String> styleProps = new LinkedHashMap<>();
    private final List<Object> children = new ArrayList<>();
    private final Map<String, Sub> subs = new LinkedHashMap<>();
    private final List<String> classNames = new ArrayList<>();

    Page page;
    Element<?> parent;

    protected Element(String tag, boolean voidElement) {
        this.tag = tag;
        this.voidElement = voidElement;
    }

    /** The page-wide monitor once mounted (shared with renderHtml), else this element. */
    private Object lock() {
        Page p = page;
        return p == null ? this : p;
    }

    @SuppressWarnings("unchecked")
    public S self() {
        return (S) this;
    }

    public String uid() {
        return uid;
    }

    public String tagName() {
        return tag;
    }

    /** This element's current parent, or null when it is not mounted under one. */
    public Element<?> parentElement() {
        return parent;
    }

    // ---------- structure ----------
    public S add(Element<?>... kids) {
        synchronized (lock()) {
            validateChildren(kids, false);
            for (Element<?> kid : kids) {
                kid.parent = this;
                children.add(kid);
                kid.mount(page);
            }
            if (page != null) {
                page.broadcast(Ops.append(uid, renderAll(kids), collectSubs(kids)));
            }
            return self();
        }
    }

    public S replaceChildren(Element<?>... kids) {
        synchronized (lock()) {
            validateChildren(kids, true);
            for (Object child : children) {
                if (child instanceof Element<?> e) {
                    e.unmount();
                }
            }
            children.clear();
            for (Element<?> kid : kids) {
                kid.parent = this;
                children.add(kid);
                kid.mount(page);
            }
            if (page != null) {
                page.broadcast(Ops.replace(uid, renderAll(kids), collectSubs(kids)));
            }
            return self();
        }
    }

    private void validateChildren(Element<?>[] kids, boolean replacing) {
        java.util.Set<Element<?>> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Element<?> kid : kids) {
            java.util.Objects.requireNonNull(kid, "child");
            if (!seen.add(kid) || (kid.parent != null && !(replacing && kid.parent == this))
                    || (kid.parent == null && kid.page != null)) {
                throw new IllegalArgumentException("an element can belong to only one parent");
            }
            for (Element<?> ancestor = this; ancestor != null; ancestor = ancestor.parent) {
                if (ancestor == kid) throw new IllegalArgumentException("element trees cannot contain cycles");
            }
        }
    }

    /**
     * Reorders existing children to the given sequence, moving nodes instead of re-rendering them.
     *
     * <p>Use this for sorting, grouping and filtering a list whose rows already exist: it costs a
     * handful of bytes per row rather than a row's worth of HTML, and the browser keeps each row's
     * focus, selection and scroll state because the nodes are the same nodes. Children omitted
     * from {@code order} keep their relative order after the ones named. Elements that are not
     * children of this one are ignored.
     */
    public S orderChildren(List<? extends Element<?>> order) {
        synchronized (lock()) {
            List<String> uids = new ArrayList<>(order.size());
            List<Object> moved = new ArrayList<>(children.size());
            java.util.Set<Element<?>> named =
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (Element<?> kid : order) {
                if (kid != null && kid.parent == this && named.add(kid)) {
                    moved.add(kid);
                    uids.add(kid.uid());
                }
            }
            for (Object child : children) {
                if (!(child instanceof Element<?> e) || !named.contains(e)) {
                    moved.add(child);
                }
            }
            // Reordering to the order they are already in is a no-op the caller should not have
            // to detect: this runs on every repaint, and an op per repaint is exactly the kind
            // of traffic the batch is meant to remove.
            boolean unchanged = children.equals(moved);
            children.clear();
            children.addAll(moved);
            if (page != null && !uids.isEmpty() && !unchanged) {
                page.broadcast(Ops.order(uid, uids));
            }
            return self();
        }
    }

    public S remove(Element<?> kid) {
        synchronized (lock()) {
            if (children.remove(kid)) {
                kid.unmount();
                if (page != null) {
                    page.broadcast(Ops.remove(kid.uid()));
                }
            }
            return self();
        }
    }

    public S clear() {
        return replaceChildren();
    }

    /** Sets the text content of this element, replacing all children. Escaped by default. */
    public S text(String text) {
        synchronized (lock()) {
            for (Object child : children) {
                if (child instanceof Element<?> e) {
                    e.unmount();
                }
            }
            children.clear();
            children.add(new TextNode(text));
            if (page != null) {
                page.broadcast(Ops.text(uid, text));
            }
            return self();
        }
    }

    /**
     * Binds this element's text to an expression over live variables and the clock. The expression
     * evaluates and repaints entirely client side, so the wire only carries variable syncs (see
     * {@link #setVar}) instead of per-tick text. Requires a live page: call it from an event
     * handler or {@link Page#onLoad}. Setting plain text with {@link #text(String)} unbinds.
     */
    public S bindText(Expr expr) {
        if (page != null) {
            page.broadcast(Ops.bind(uid, expr.toJson()));
        }
        return self();
    }

    /**
     * Sets a live variable for every connection viewing this page. Each connection snapshots the
     * receipt time, so an expression can age the value (e.g. remaining = var - age) immune to
     * client clock skew; re-sync "every so often" to correct drift.
     */
    public S setVar(String name, long value) {
        if (page != null) {
            page.broadcast(Ops.setVar(name, (int) value));
        }
        return self();
    }

    /** Appends escaped text at build time. */
    public S addText(String text) {
        synchronized (lock()) {
            children.add(new TextNode(text));
            return self();
        }
    }

    /** Appends raw, unescaped HTML. This is the ONLY way to inject markup from strings. */
    public S raw(String html) {
        synchronized (lock()) {
            children.add(new RawNode(html));
            return self();
        }
    }

    // ---------- attributes and style ----------

    public S attr(String name, String value) {
        synchronized (lock()) {
            if (value == null) {
                attrs.remove(name);
            } else {
                attrs.put(name, value);
            }
            if (page != null) {
                if ("value".equals(name) && ("input".equals(tag) || "textarea".equals(tag) || "select".equals(tag))) {
                    page.broadcast(Ops.value(uid, value == null ? "" : value));
                    page.updateValue(uid, value == null ? "" : value);
                } else {
                    page.broadcast(Ops.attr(uid, name, value));
                }
            }
            return self();
        }
    }

    public S style(String prop, String value) {
        synchronized (lock()) {
            styleProps.put(prop, value);
            if (page != null) {
                page.broadcast(Ops.style(uid, prop, value));
            }
            return self();
        }
    }

    public S addClass(String name) {
        synchronized (lock()) {
            if (!classNames.contains(name)) {
                classNames.add(name);
            }
            if (page != null) {
                for (Ops.Op op : Ops.classes(uid, List.of(name), List.of())) {
                    page.broadcast(op);
                }
            }
            return self();
        }
    }

    public S removeClass(String name) {
        synchronized (lock()) {
            if (classNames.remove(name) && page != null) {
                for (Ops.Op op : Ops.classes(uid, List.of(), List.of(name))) {
                    page.broadcast(op);
                }
            }
            return self();
        }
    }

    public S show() {
        return hidden(false);
    }

    public S hide() {
        return hidden(true);
    }

    // ---------- events ----------

    @Override
    public S listen(String event, Function<Map<String, Object>, ? extends EventPayload> parser,
            Handler<? extends EventPayload> handler, boolean preventDefault) {
        synchronized (lock()) {
            subs.put(event, new Sub(parser, handler, preventDefault));
            return self();
        }
    }

    /**
     * Records what the user typed into this field, in the tree only.
     *
     * <p>The browser already shows the value, so nothing is sent: broadcasting it back would
     * overwrite keystrokes still in flight. What it fixes is the other direction. The tree is what
     * a reload renders and what a reconnect replaces the page with, so a field whose typed value
     * never reached the tree came back as it was first built, and the next keystroke saved that
     * stale text plus one character over the real document.
     *
     * <p>Fields that do not hold text the user would expect back are left alone: a password must
     * not be written into markup, and a checkbox's value is a label, not what was entered.
     */
    void adoptTypedValue(String value) {
        synchronized (lock()) {
            if ("textarea".equals(tag)) {
                children.clear();
                children.add(new TextNode(value));
                attrs.put("value", value);
            } else if ("input".equals(tag) && !NOT_TYPED.contains(attrs.getOrDefault("type", "text"))) {
                attrs.put("value", value);
            }
        }
    }

    private static final java.util.Set<String> NOT_TYPED = java.util.Set.of(
            "password", "checkbox", "radio", "file", "button", "submit", "reset", "image", "hidden");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    void dispatch(String event, Ui ui, Map<String, Object> payload) {
        Sub sub = subs.get(event);
        if (sub == null) {
            return;
        }
        EventPayload parsed = sub.parser().apply(payload);
        ((Handler) sub.handler()).handle(ui, parsed);
    }

    // ---------- mounting ----------
    void mount(Page newPage) {
        this.page = newPage;
        if (newPage != null) {
            newPage.register(this);
        }
        for (Object child : children) {
            if (child instanceof Element<?> e) {
                e.mount(newPage);
            }
        }
    }

    void unmount() {
        unmountTree();
        parent = null;
    }

    private void unmountTree() {
        if (page != null) page.unregister(this);
        page = null;
        for (Object child : children) {
            if (child instanceof Element<?> element) element.unmountTree();
        }
    }

    // ---------- rendering ----------
    public String renderToString() {
        synchronized (lock()) {
            StringBuilder sb = new StringBuilder(128);
            render(sb);
            return sb.toString();
        }
    }

    /** Inner-HTML snapshot with its dynamic listeners: the payload of a full-content replace. */
    public Ops.Op replaceSelf() {
        synchronized (lock()) {
            return Ops.replace(uid, renderAll(children.toArray(new Element<?>[0])), collectSubs());
        }
    }

    void render(StringBuilder sb) {
        sb.append('<').append(tag).append(" data-rj=\"").append(uid).append('"');
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            sb.append(' ').append(e.getKey()).append("=\"").append(Esc.html(e.getValue())).append('"');
        }
        if (!classNames.isEmpty()) {
            sb.append(" class=\"").append(Esc.html(String.join(" ", classNames))).append('"');
        }
        if (!styleProps.isEmpty()) {
            sb.append(" style=\"");
            for (Map.Entry<String, String> e : styleProps.entrySet()) {
                sb.append(e.getKey()).append(':').append(Esc.html(e.getValue())).append(';');
            }
            sb.append('"');
        }
        if (voidElement) {
            sb.append("/>");
            return;
        }
        sb.append('>');
        renderChildren(sb);
        sb.append("</").append(tag).append('>');
    }

    private void renderChildren(StringBuilder sb) {
        for (Object child : children) {
            if (child instanceof Element<?> e) {
                e.render(sb);
            } else if (child instanceof TextNode t) {
                sb.append(Esc.html(t.value));
            } else if (child instanceof RawNode r) {
                sb.append(r.html);
            }
        }
    }

    private static String renderAll(Element<?>[] kids) {
        StringBuilder sb = new StringBuilder(128);
        for (Element<?> kid : kids) {
            kid.render(sb);
        }
        return sb.toString();
    }

    Map<String, List<Ops.EvSub>> collectSubs() {
        return collectSubs(new LinkedHashMap<>());
    }

    private Map<String, List<Ops.EvSub>> collectSubs(Map<String, List<Ops.EvSub>> out) {
        if (!subs.isEmpty()) {
            List<Ops.EvSub> events = new ArrayList<>();
            for (Map.Entry<String, Sub> e : subs.entrySet()) {
                events.add(new Ops.EvSub(e.getKey(), e.getValue().preventDefault()));
            }
            out.put(uid, events);
        }
        for (Object child : children) {
            if (child instanceof Element<?> e) {
                e.collectSubs(out);
            }
        }
        return out;
    }

    private static Map<String, List<Ops.EvSub>> collectSubs(Element<?>[] kids) {
        Map<String, List<Ops.EvSub>> out = new LinkedHashMap<>();
        for (Element<?> kid : kids) {
            kid.collectSubs(out);
        }
        return out;
    }
}
