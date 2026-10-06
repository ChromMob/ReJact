package me.chrommob.rejact;

import me.chrommob.rejact.gen.tags.Input;
import java.util.Map;
/**
 * Per-connection handle passed to event handlers. Everything the handler needs to reach the
 * specific browser that triggered the event: cookies of that request, targeted DOM operations,
 * navigation, and file reads from that connection's file inputs.
 */
public final class Ui {
    /** Metadata describing a file the browser sent for a file input. */
    public record FileMeta(String name, String type, long size, long lastModified) {
    }

    /** Callback receiving file bytes from {@link #readFile}. */
    @FunctionalInterface
    public interface FileHandler {
        void handle(Ui ui, FileMeta meta, byte[] data);
    }

    private final View view;
    private final Map<String, String> cookies;
    private int eventScrollY;
    private int eventViewportW;
    private String eventHash = "";

    Ui(View view, Map<String, String> cookies) {
        this.view = view;
        this.cookies = cookies;
    }

    /**
     * Last known value of an input element on this connection. The browser syncs input values
     * once at connect and afterwards only reports changes, so this is synchronous and takes the
     * element object, never a selector.
     */
    public String valueOf(Element<?> element) {
        return view.values.getOrDefault(element.uid(), "");
    }

    public Page page() {
        return view.page;
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    public void setCookie(String name, String value) {
        cookies.put(name, value);
        view.send(Ops.cookie(name, value));
    }

    public void redirect(String href) {
        view.send(Ops.navigate(href));
    }

    public void focus(Element<?> element) {
        view.send(Ops.focus(element.uid()));
    }

    public void scrollTo(Element<?> element) {
        view.send(Ops.scroll(element.uid()));
    }

    /** Asks the browser to send the currently selected file of the given file input. */
    public void readFile(Input input, FileHandler handler) {
        view.fileHandlers.put(input.uid(), handler);
        view.send(Ops.readFile(input.uid()));
    }

    public void send(Ops.Op op) {
        view.send(op);
    }

    // ---------- identity and durable state ----------

    /** The signed-in account name on this session, or "" when anonymous. */
    public String user() {
        return sessionState().get("account", "");
    }

    /** Verifies the password and binds this permanent session to the account. */
    public boolean login(String name, String password) {
        if (view.server.accounts.verify(name, password)) {
            sessionState().set("account", name.trim());
            return true;
        }
        return false;
    }

    /** Creates the account and signs in on success. */
    public boolean register(String name, String password) {
        if (view.server.accounts.register(name, password)) {
            sessionState().set("account", name.trim());
            return true;
        }
        return false;
    }

    public void logout() {
        sessionState().remove("account");
    }

    /**
     * Durable state store for whoever is on this tab: the account store when signed in (shared by
     * every device they use), the session store when anonymous. Persisted on every mutation.
     */
    public Store.Kv state() {
        String user = user();
        return user.isEmpty()
                ? view.server.store.session(view.sessionKey)
                : view.server.store.account(user);
    }

    /** Durable state bound to this session only, regardless of sign-in. */
    public Store.Kv sessionState() {
        return view.server.store.session(view.sessionKey);
    }

    // ---------- per-tab viewport ----------

    /** Window scroll position at the moment the current event fired (0 if unknown). */
    public int scrollY() {
        return eventScrollY;
    }

    /** Viewport width at the moment the current event fired (0 if unknown). */
    public int viewportWidth() {
        return eventViewportW;
    }

    void captureViewport(Map<String, Object> payload) {
        eventScrollY = asInt(payload.get("sy"));
        eventViewportW = asInt(payload.get("w"));
        eventHash = String.valueOf(payload.getOrDefault("h", ""));
    }

    private static int asInt(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException bad) {
            return 0;
        }
    }

    /** Scrolls this tab's window to an absolute vertical offset. */
    public void scrollWindow(int y) {
        view.send(new Ops.ScrollWindow(y));
    }

    /** Remembers a value for this tab only (one connection, gone when the tab closes). */
    public void remember(String key, String value) {
        view.attrs.put(key, value);
    }

    public String recall(String key) {
        return view.attrs.getOrDefault(key, "");
    }

    /** Enters one of the page's virtual sites on this tab - URL, history entry and scroll follow. */
    public void go(String site) {
        enterSite(site, "", true);
    }

    /** Enters a virtual site with a parameter, e.g. go("read", articleId). */
    public void go(String site, String param) {
        enterSite(site, param, true);
    }

    /** Steps this tab one back through its own history; the previous site re-enters by itself. */
    public void back() {
        view.send(new Ops.HistoryBack());
    }

    /** Sets a live variable on this tab only (see {@link Element#setVar} for the semantics). */
    public void setVar(String name, long value) {
        view.send(Ops.setVar(name, (int) value));
    }

    /** The tab's current virtual site name. */
    public String site() {
        return view.attrs.getOrDefault("rj.site", "");
    }

    void enterFromUrl() {
        if (!view.page.hasSites()) {
            return;
        }
        String body = eventHash.startsWith("#") ? eventHash.substring(1) : eventHash;
        String name = body;
        String param = "";
        int eq = body.indexOf('=');
        if (eq >= 0) {
            name = body.substring(0, eq);
            param = body.substring(eq + 1);
        }
        enterSite(urlDecode(name), urlDecode(param), false);
    }

    private void enterSite(String name, String param, boolean fresh) {
        view.attrs.put("rj.scroll." + site(), Integer.toString(eventScrollY));
        view.attrs.put("rj.site", name);
        view.page.enterSite(name, param);
        view.send(new Ops.Site(name, param, fresh));
        if (fresh) {
            view.send(new Ops.ScrollWindow(0));
        } else {
            String y = view.attrs.get("rj.scroll." + name);
            if (y != null) {
                view.send(new Ops.ScrollWindow(Integer.parseInt(y)));
            }
        }
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }
}
