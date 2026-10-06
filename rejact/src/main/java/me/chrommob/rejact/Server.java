package me.chrommob.rejact;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.chrommob.rejact.gen.EventCodes;

/**
 * One listener, one port: plain HTTP for documents, assets and uploads, WebSocket upgrade at
 * /_rejact/ws for all reactivity. Events flow up as JSON text frames, ops flow down as JSON text
 * frames on the same duplex channel (one channel per tab), and a WS ping every 30s keeps proxies
 * from idling the socket out. No eval anywhere.
 */
public final class Server {
    private static final int MAX_HEADERS = 32 * 1024;
    private static final int MAX_EVENT_BODY = 256 * 1024;
    private static final int MAX_UPLOAD_BODY = 24 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Cache-buster baked into the runtime script URL: new process = new URL = CDN cache miss. */
    static final String RUNTIME_VERSION = randomToken(8);

    private final ServerSocket listener;
    private final int port;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private final Map<String, View> views = new ConcurrentHashMap<>();
    private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
    /** Durable state (sessions, accounts, shared app state): persisted on every mutation. */
    public final Store store;
    /** Password accounts backed by {@link #store}. */
    public final Accounts accounts;
    private final Map<String, String> blobMimes = new ConcurrentHashMap<>();
    private final byte[] runtimeJs;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "rejact-http");
        t.setDaemon(true);
        return t;
    });

    public Server(int port) throws IOException {
        this.port = port;
        store = new Store(Path.of(System.getProperty("rejact.data", "data")));
        accounts = new Accounts(store);
        listener = new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress("127.0.0.1", port));
        try (InputStream in = Server.class.getResourceAsStream("/rejact-runtime.js")) {
            if (in == null) {
                throw new IOException("rejact-runtime.js not found on classpath");
            }
            runtimeJs = in.readAllBytes();
        }
    }

    /**
     * Builds one Page per session. Use this for per-session SSR and per-session state: the factory
     * runs once per session and its closures are that session's state. Return a constant Page (or
     * use {@link #registerPage(String, Page)}) for global state shared by every session.
     */
    @FunctionalInterface
    public interface PageFactory {
        Page create(String sessionKey);
    }

    private static final class Route {
        final PageFactory factory;
        final Map<String, Page> sessions = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Page> eldest) {
                return size() > 512;
            }
        };

        Route(PageFactory factory) {
            this.factory = factory;
        }

        Page resolve(String sessionKey) {
            synchronized (sessions) {
                return sessions.computeIfAbsent(sessionKey, factory::create);
            }
        }
    }

    /**
     * Registers a page shared by every session (global state). The registration key is the
     * document's URL: the WS handshake carries cfg.path and matches it against the route
     * table, so the server stamps the key onto every page it creates (see Page#route). The
     * constructor argument to {@code new Page(...)} is only an initial value and never gates
     * the live channel.
     */
    public Server registerPage(String path, Page page) {
        return registerPage(path, key2 -> page);
    }

    /** Registers a per-session page: one Page instance per session key (per-session SSR + state). */
    public Server registerPage(String path, PageFactory factory) {
        String key = normalize(path);
        routes.put(key, new Route(session -> {
            Page page = factory.create(session);
            page.route(key);
            return page;
        }));
        return this;
    }

    public void start() {
        Thread acceptor = new Thread(() -> {
            while (!listener.isClosed()) {
                try {
                    Socket socket = listener.accept();
                    pool.execute(() -> handle(socket));
                } catch (IOException e) {
                    if (!listener.isClosed()) {
                        e.printStackTrace();
                    }
                }
            }
        }, "rejact-accept");
        // Non-daemon: the accept loop holds the process open, like a server should.
        acceptor.setDaemon(false);
        acceptor.start();
        ScheduledExecutorService keepalive = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rejact-keepalive");
            t.setDaemon(true);
            return t;
        });
        keepalive.scheduleAtFixedRate(() -> {
            for (View view : views.values()) {
                view.sendPing();
            }
        }, 30, 30, TimeUnit.SECONDS);
        System.out.println("ReJact listening on http://127.0.0.1:" + port);
    }

    public int port() {
        return port;
    }

    /** Stores bytes and returns a URL the page can reference (e.g. for uploaded images). */
    public String addBlob(byte[] data, String mime) {
        String id = randomToken(16);
        blobs.put(id, data);
        blobMimes.put(id, mime);
        return "/_rejact/blob/" + id;
    }

    // ---------- connection handling ----------

    private record Request(String method, String uri, Map<String, String> headers) {
        String path() {
            int q = uri.indexOf('?');
            return q < 0 ? uri : uri.substring(0, q);
        }

        String rawQuery() {
            int q = uri.indexOf('?');
            return q < 0 ? "" : uri.substring(q + 1);
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(120_000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            Request req = readRequest(in);
            if (req == null) {
                return;
            }
            if ("/_rejact/ws".equals(req.path()) && isUpgrade(req)) {
                serveWs(socket, in, out, req);
                return;
            }
            byte[] body = readBody(in, req.headers(), "/_rejact/upload".equals(req.path())
                    ? MAX_UPLOAD_BODY : MAX_EVENT_BODY);
            routeHttp(out, req, body);
        } catch (IOException e) {
            // Connection-level failures (reset, timeout) are not application errors.
        }
    }

    private static boolean isUpgrade(Request req) {
        String upgrade = req.headers().get("upgrade");
        return upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim())
                && req.headers().containsKey("sec-websocket-key");
    }

    private static Request readRequest(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) {
            return null;
        }
        Map<String, String> headers = new HashMap<>();
        String h;
        while ((h = readLine(in)) != null && !h.isEmpty()) {
            int colon = h.indexOf(':');
            if (colon > 0) {
                headers.put(h.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        h.substring(colon + 1).trim());
            }
        }
        return new Request(parts[0], parts[1], headers);
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(128);
        int b;
        while ((b = in.read()) >= 0 && b != '\n') {
            if (b != '\r') {
                sb.append((char) b);
            }
            if (sb.length() > MAX_HEADERS) {
                throw new IOException("header line too large");
            }
        }
        return b < 0 ? null : sb.toString();
    }

    private static byte[] readBody(InputStream in, Map<String, String> headers, int cap)
            throws IOException {
        long len = Long.parseLong(headers.getOrDefault("content-length", "0"));
        if (len > cap) {
            throw new IOException("body too large: " + len);
        }
        return in.readNBytes((int) len);
    }

    private void routeHttp(OutputStream out, Request req, byte[] body) throws IOException {
        String path = req.path();
        String method = req.method();
        if ("GET".equals(method) && "/_rejact/stats".equals(path)) {
            sendResponse(out, 200, "text/plain; charset=utf-8", null,
                    bytes("in=" + Ws.BYTES_IN.get() + "/" + Ws.MSGS_IN.get()
                            + " out=" + Ws.BYTES_OUT.get() + "/" + Ws.MSGS_OUT.get()));
        } else if ("GET".equals(method) && "/_rejact/runtime.js".equals(path)) {
            Map<String, String> extra = new LinkedHashMap<>();
            extra.put("Cache-Control", "public, max-age=31536000, immutable");
            sendResponse(out, 200, "application/javascript; charset=utf-8", extra, runtimeJs);
        } else if ("GET".equals(method) && path.startsWith("/_rejact/blob/")) {
            serveBlob(out, path.substring("/_rejact/blob/".length()));
        } else if ("POST".equals(method) && "/_rejact/upload".equals(path)) {
            handleUpload(out, req, body);
        } else if ("GET".equals(method)) {
            Route route = routes.get(normalize(path));
            if (route == null) {
                sendResponse(out, 404, "text/plain; charset=utf-8", null, bytes("not found"));
            } else {
                servePage(out, req, route);
            }
        } else {
            sendResponse(out, 405, "text/plain; charset=utf-8", null, bytes("method not allowed"));
        }
    }

    private void servePage(OutputStream out, Request req, Route route) throws IOException {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("Cache-Control", "no-store");
        String sid = cookies(req.headers()).get("rj_sid");
        if (sid == null || !sid.matches("[a-zA-Z0-9_-]{8,64}")) {
            sid = randomToken(16);
            // Permanent session: a persistent cookie, so identity survives browser restarts too.
            extra.put("Set-Cookie", "rj_sid=" + sid + "; Path=/; SameSite=Lax; Max-Age=31536000");
        }
        // Durable registry: known sessions survive every server restart.
        if (store.app("sessions").get(sid, "").isEmpty()) {
            store.app("sessions").set(sid, Long.toString(System.currentTimeMillis()));
        }
        // Per-session SSR: the factory builds (or reuses) this session's tree from session state.
        byte[] html = route.resolve(sid).renderHtml(sid).getBytes(StandardCharsets.UTF_8);
        sendResponse(out, 200, "text/html; charset=utf-8", extra, html);
    }

    private void serveBlob(OutputStream out, String id) throws IOException {
        byte[] data = blobs.get(id);
        if (data == null) {
            sendResponse(out, 404, "text/plain; charset=utf-8", null, bytes("not found"));
            return;
        }
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("Cache-Control", "public, max-age=31536000, immutable");
        sendResponse(out, 200, blobMimes.getOrDefault(id, "application/octet-stream"), extra, data);
    }

    private void handleUpload(OutputStream out, Request req, byte[] body) throws IOException {
        Map<String, String> query = query(req.rawQuery());
        View view = views.get(query.getOrDefault("view", ""));
        if (view != null) {
            String metaRaw = req.headers().get("x-rj-meta");
            Map<String, Object> meta = Json.obj(Json.parse(URLDecoder.decode(
                    metaRaw == null ? "{}" : metaRaw, StandardCharsets.UTF_8)));
            Ui.FileHandler handler = view.fileHandlers.remove(query.getOrDefault("el", ""));
            if (handler != null) {
                byte[] data = body == null ? new byte[0] : body;
                long size = meta.get("size") instanceof Number n ? n.longValue() : data.length;
                long lastModified = meta.get("lastModified") instanceof Number n ? n.longValue() : 0L;
                handler.handle(new Ui(view, view.cookies),
                        new Ui.FileMeta(Json.str(meta, "name"), Json.str(meta, "type"), size,
                                lastModified), data);
            }
        }
        sendResponse(out, 204, null, null, new byte[0]);
    }

    // ---------- websocket ----------

    private void serveWs(Socket socket, InputStream in, OutputStream out, Request req)
            throws IOException {
        Map<String, String> query = query(req.rawQuery());
        String viewId = query.getOrDefault("view", "");
        String sid = query.get("sid");
        if (sid == null) {
            sid = cookies(req.headers()).get("rj_sid");
        }
        Route route = routes.get(normalize(query.getOrDefault("path", "/")));
        Page page = (route == null || sid == null || viewId.isEmpty() || viewId.length() > 64)
                ? null : route.resolve(sid);
        if (page == null) {
            sendResponse(out, 400, "text/plain; charset=utf-8", null, bytes("bad ws request"));
            return;
        }
        String accept = Ws.acceptKey(req.headers().get("sec-websocket-key"));
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();

        View view = new View(viewId, page, cookies(req.headers()), out,
                sid == null ? "" : sid, this);
        views.put(viewId, view);
        page.attach(view);
        try {
            ByteArrayOutputStream fragment = new ByteArrayOutputStream();
            while (view.alive) {
                Ws.Frame frame = Ws.read(in);
                if (frame == null) {
                    break;
                }
                switch (frame.opcode()) {
                    case Ws.OP_PING -> Ws.write(out, Ws.OP_PONG, frame.payload());
                    case Ws.OP_CLOSE -> {
                        Ws.write(out, Ws.OP_CLOSE, new byte[0]);
                        view.close();
                    }
                    case Ws.OP_TEXT, Ws.OP_BINARY -> {
                        if (frame.fin()) {
                            handleWsFrame(view, frame.payload());
                        } else {
                            fragment.reset();
                            fragment.write(frame.payload());
                        }
                    }
                    case Ws.OP_CONT -> {
                        fragment.write(frame.payload());
                        if (frame.fin()) {
                            handleWsFrame(view, fragment.toByteArray());
                            fragment.reset();
                        }
                    }
                    default -> {
                        // stray pongs and unknown opcodes: nothing to do
                    }
                }
            }
        } catch (IOException e) {
            // Socket closed or timed out: fall through to cleanup.
        } finally {
            view.close();
            views.remove(viewId, view);
        }
    }

    private void handleWsFrame(View view, byte[] frame) {
        try {
            Wire.Inbound msg = Wire.decode(frame);
            Ui ui = new Ui(view, view.cookies);
            ui.captureViewport(msg.payload());
            switch (msg.type()) {
                case Wire.LOAD -> {
                    for (Map.Entry<String, Object> e : Json.obj(msg.payload().get("v")).entrySet()) {
                        view.values.put(e.getKey(), String.valueOf(e.getValue()));
                    }
                    if (msg.payload().get("r") instanceof Number r && r.intValue() != 0) {
                        // Reconnect: send the authoritative snapshot so missed updates can
                        // never leave stale content on screen. An oversized page (one op is
                        // u16-capped) skips the resync instead of breaking the channel.
                        try {
                            view.send(view.page.snapshot());
                        } catch (RuntimeException ignored) {
                        }
                    }
                    view.page.fireLoad(ui);
                    ui.enterFromUrl();
                }
                case Wire.BACK -> ui.enterFromUrl();
                case Wire.EVENT -> {
                    Object value = msg.payload().get("value");
                    if (value instanceof String s && !msg.el().isEmpty()) {
                        view.values.put(msg.el(), s);
                    }
                    view.page.dispatch(msg.el(), EventCodes.name(msg.evCode()), ui, msg.payload());
                }
                case Wire.UNLOAD -> view.page.fireUnload(ui);
                default -> {
                    // unknown message type: ignore
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- shared plumbing ----------

    private static void sendResponse(OutputStream out, int code, String contentType,
            Map<String, String> extra, byte[] body) throws IOException {
        StringBuilder sb = new StringBuilder(256);
        sb.append("HTTP/1.1 ").append(code).append(' ').append(statusText(code)).append("\r\n");
        if (contentType != null) {
            sb.append("Content-Type: ").append(contentType).append("\r\n");
        }
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n");
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String statusText(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> cookies(Map<String, String> headers) {
        Map<String, String> out = new HashMap<>();
        String cookie = headers.get("cookie");
        if (cookie == null) {
            return out;
        }
        for (String part : cookie.split(";")) {
            int at = part.indexOf('=');
            if (at > 0) {
                out.put(part.substring(0, at).trim(),
                        URLDecoder.decode(part.substring(at + 1).trim(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split("&")) {
            int at = part.indexOf('=');
            String key = at < 0 ? part : part.substring(0, at);
            String value = at < 0 ? "" : part.substring(at + 1);
            out.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static synchronized String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(RANDOM.nextInt(36)));
        }
        return sb.toString();
    }
}
