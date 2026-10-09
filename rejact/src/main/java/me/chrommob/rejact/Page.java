package me.chrommob.rejact;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import me.chrommob.rejact.gen.tags.Html;

/**
 * A page: one server-owned element tree plus the connected views looking at it. Element
 * mutations broadcast to every connected view, handlers run per view via {@link Ui}.
 */
public final class Page {
    private static final java.util.concurrent.ScheduledThreadPoolExecutor TIMERS =
            new java.util.concurrent.ScheduledThreadPoolExecutor(1, r -> {
                Thread thread = new Thread(r, "rejact-timers");
                thread.setDaemon(true);
                return thread;
            });
    static { TIMERS.setRemoveOnCancelPolicy(true); }

    private record Timer(long millis, Consumer<Ui> action) { }
    private final List<Timer> timers = new ArrayList<>();
    private final List<java.util.concurrent.ScheduledFuture<?>> scheduled = new ArrayList<>();

    private String path;

    /**
     * Stamps the document's URL onto the page. The server calls this with the registerPage
     * key: the WS handshake resolves the route on cfg.path, so the key is authoritative and
     * the constructor argument is only an initial value.
     */
    void route(String key) {
        this.path = key;
    }
    private final Html root = new Html();
    private final Element<?> tree = root;
    private final Map<String, Element<?>> registry = new ConcurrentHashMap<>();
    private final Map<String, View> views = new ConcurrentHashMap<>();
    private Consumer<Ui> loadHandler;
    private Consumer<Ui> unloadHandler;

    public Page(String path) {
        this.path = path;
        tree.mount(this);
    }

    /** The document root element; build the page tree on it. */
    public Html root() {
        return root;
    }

    public String path() {
        return path;
    }

    public Page onLoad(Consumer<Ui> handler) {
        this.loadHandler = handler;
        return this;
    }

    public Page onUnload(Consumer<Ui> handler) {
        this.unloadHandler = handler;
        return this;
    }

    /**
     * Virtual sites: named states inside one page, like pages of a site that never reloads. The
     * framework owns the URL, the history entries, back/forward, deep links and per-tab scroll
     * memory; the handler simply shapes the page for that site and is entered again automatically
     * whenever a tab lands on it (click, back, forward, or a shared link on load).
     */
    public interface SiteHandler {
        void enter(String param);
    }

    private final Map<String, SiteHandler> sites = new ConcurrentHashMap<>();

    /** Declares a virtual site. The empty name is the page's root site. */
    public Page site(String name, SiteHandler handler) {
        sites.put(name, handler);
        return this;
    }

    boolean hasSites() {
        return !sites.isEmpty();
    }

    void enterSite(String name, String param) {
        SiteHandler handler = sites.get(name);
        if (handler != null) {
            handler.enter(param);
        }
    }

    /** Runs the action for every connected view on a fixed schedule. */
    public synchronized Page every(Duration period, Consumer<Ui> action) {
        long millis = period.toMillis();
        if (millis < 1) throw new IllegalArgumentException("timer period must be at least one millisecond");
        Timer timer = new Timer(millis, java.util.Objects.requireNonNull(action, "action"));
        timers.add(timer);
        if (!views.isEmpty()) schedule(timer);
        return this;
    }

    private void schedule(Timer timer) {
        scheduled.add(TIMERS.scheduleAtFixedRate(() -> {
            synchronized (this) {
                // A tick that touches twenty elements is still one repaint, not twenty.
                batched(() -> {
                    for (View view : views.values()) {
                        try {
                            timer.action().accept(new Ui(view, view.cookies));
                        } catch (Exception e) {
                            System.getLogger(Page.class.getName())
                                    .log(System.Logger.Level.WARNING, "timer failed", e);
                        }
                    }
                });
            }
        }, timer.millis(), timer.millis(), TimeUnit.MILLISECONDS));
    }

    /** One {@link Ui} per connected view of this page. */
    public List<Ui> clients() {
        List<Ui> out = new ArrayList<>();
        for (View view : views.values()) {
            out.add(new Ui(view, view.cookies));
        }
        return out;
    }

    /** Renders the full HTML document, including the JSON config and the fixed runtime script. */
    public String renderHtml(String sessionKey) {
        // Same monitor as Element's mutators: a render never interleaves with a tree
        // mutation, so the document is one consistent snapshot (no torn HTML, no CME).
        synchronized (this) {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("path", path);
            cfg.put("subs", subsJson(tree.collectSubs()));
            StringBuilder sb = new StringBuilder(2048);
            sb.append("<!doctype html>");
            tree.render(sb);
            String inject = "<script type=\"application/json\" id=\"rjcfg\">" + Json.write(cfg)
                    + "</script><script src=\"/_rejact/runtime.js?v=" + Server.RUNTIME_VERSION + "\"></script>";
            int at = sb.lastIndexOf("</body>");
            if (at >= 0) {
                sb.insert(at, inject);
            } else {
                sb.append(inject);
            }
            return sb.toString();
        }
    }

    private static Map<String, Object> subsJson(Map<String, List<Ops.EvSub>> subs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Ops.EvSub>> e : subs.entrySet()) {
            List<String> events = new ArrayList<>();
            List<String> prevent = new ArrayList<>();
            for (Ops.EvSub sub : e.getValue()) {
                events.add(sub.event());
                if (sub.preventDefault()) {
                    prevent.add(sub.event());
                }
            }
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("e", events);
            def.put("p", prevent);
            out.put(e.getKey(), def);
        }
        return out;
    }

    /** Authoritative snapshot of the current tree, for a view that reconnects mid-session. */
    public Ops.Op snapshot() {
        synchronized (this) {
            return tree.replaceSelf();
        }
    }

    void fireLoad(Ui ui) {
        if (loadHandler != null) {
            loadHandler.accept(ui);
        }
    }

    void fireUnload(Ui ui) {
        if (unloadHandler != null) {
            unloadHandler.accept(ui);
        }
    }

    void dispatch(String uid, String event, Ui ui, Map<String, Object> payload) {
        Element<?> element = registry.get(uid);
        if (element != null) {
            element.dispatch(event, ui, payload);
        }
    }

    synchronized void attach(View view) {
        if (!view.alive) return;
        boolean first = views.isEmpty();
        views.put(view.id, view);
        if (first) timers.forEach(this::schedule);
    }

    synchronized void detach(View view) {
        views.remove(view.id, view);
        if (views.isEmpty()) {
            scheduled.forEach(task -> task.cancel(false));
            scheduled.clear();
        }
    }

    void register(Element<?> element) {
        registry.put(element.uid(), element);
    }

    /** Mirrors a value typed in the browser into the tree, so a reload or reconnect brings it back. */
    void adoptTypedValue(String uid, String value) {
        Element<?> element = registry.get(uid);
        if (element != null) {
            element.adoptTypedValue(value);
        }
    }

    boolean containsElement(String uid) {
        return registry.containsKey(uid);
    }

    void unregister(Element<?> element) {
        registry.remove(element.uid());
        for (View view : views.values()) {
            view.values.remove(element.uid());
            view.fileHandlers.remove(element.uid());
        }
    }

    /** Keeps every view's input-value cache in sync when the server sets a value itself. */
    void updateValue(String uid, String value) {
        for (View view : views.values()) {
            view.values.put(uid, value);
        }
    }

    void broadcast(Ops.Op op) {
        for (View view : views.values()) {
            view.send(op);
        }
    }

    /**
     * Runs {@code body} with every connected view batching its ops, so one logical change reaches
     * each browser as a single frame and a single DOM update. Views that connect while the batch
     * is open are not retro-enrolled: they are mid-handshake and get the authoritative tree anyway.
     *
     * <p>The framework already wraps inbound events, uploads and timer ticks in a batch. Call this
     * directly for updates that originate server-side - a background job, or a change pushed in
     * from another user's connection - which otherwise reach the browser one mutation at a time.
     *
     * <p>Nestable, and safe to call from any thread. The batch is opened and closed on the page
     * monitor, the same lock element mutations take, so a concurrent gesture on another connection
     * cannot interleave into this one's frame.
     */
    public void batch(Runnable body) {
        batched(body);
    }

    void batched(Runnable body) {
        List<View> enrolled;
        synchronized (this) {
            enrolled = new ArrayList<>(views.values());
            enrolled.forEach(View::openBatch);
        }
        try {
            body.run();
        } finally {
            synchronized (this) {
                enrolled.forEach(View::closeBatch);
            }
        }
    }

    Collection<View> views() {
        return views.values();
    }
}
