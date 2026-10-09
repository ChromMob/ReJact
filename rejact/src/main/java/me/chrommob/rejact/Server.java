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
 * /_rejact/ws for all reactivity. Events and DOM operations use binary WebSocket
 * frames on the same duplex channel (one channel per tab), and a WS ping every 30s keeps proxies
 * from idling the socket out. No eval anywhere.
 */
public final class Server implements AutoCloseable {
    private static final int MAX_HEADERS = 32 * 1024;
    private static final int MAX_EVENT_BODY = Wire.MAX_MESSAGE;
    private static final int MAX_UPLOAD_BODY = 24 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Cache-buster baked into the runtime script URL: new process = new URL = CDN cache miss. */
    static final String RUNTIME_VERSION = randomToken(8);

    private final ServerSocket listener;
    private final int port;
    private final java.net.URI publicOrigin;
    private final java.util.Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.Semaphore capacity = new java.util.concurrent.Semaphore(1024);
    private final ScheduledExecutorService keepalive = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "rejact-keepalive");
        thread.setDaemon(true);
        return thread;
    });
    private boolean started;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private final Map<String, View> views = new ConcurrentHashMap<>();
    private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
    /** Durable state (sessions, accounts, shared app state): persisted on every mutation. */
    public final Store store;
    /** Password accounts backed by {@link #store}. */
    public final Accounts accounts;
    private final Map<String, String> blobMimes = new ConcurrentHashMap<>();
    private final byte[] runtimeJs;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    /** Binds to loopback, using the directory selected by the rejact.data system property. */
    public Server(int port) throws IOException {
        this(new InetSocketAddress("127.0.0.1", port),
                Path.of(System.getProperty("rejact.data", "data")), null);
    }

    /**
     * Creates a server with explicit storage and an optional external origin.
     * Set publicOrigin to the HTTPS origin served by your reverse proxy. A null origin
     * checks browser origins against the Host header, for local development.
     */
    public Server(InetSocketAddress address, Path dataDirectory, java.net.URI publicOrigin) throws IOException {
        if (publicOrigin != null && (!java.util.Set.of("http", "https").contains(publicOrigin.getScheme())
                || publicOrigin.getHost() == null || publicOrigin.getUserInfo() != null
                || publicOrigin.getQuery() != null || publicOrigin.getFragment() != null
                || !(publicOrigin.getPath().isEmpty() || publicOrigin.getPath().equals("/")))) {
            throw new IllegalArgumentException("publicOrigin must be an HTTP or HTTPS origin");
        }
        this.publicOrigin = publicOrigin;
        store = new Store(java.util.Objects.requireNonNull(dataDirectory, "dataDirectory"));
        accounts = new Accounts(store);
        try (InputStream in = Server.class.getResourceAsStream("/rejact-runtime.js")) {
            if (in == null) throw new IOException("rejact-runtime.js not found on classpath");
            runtimeJs = in.readAllBytes();
        }
        listener = new ServerSocket();
        try {
            listener.setReuseAddress(true);
            listener.bind(address);
        } catch (IOException | RuntimeException e) {
            listener.close();
            throw e;
        }
        port = listener.getLocalPort();
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
        final Map<String, Page> sessions = new LinkedHashMap<>(16, 0.75f, true);

        Route(PageFactory factory) {
            this.factory = factory;
        }

        Page resolve(String sessionKey) {
            synchronized (sessions) {
                Page page = sessions.computeIfAbsent(sessionKey, factory::create);
                var iterator = sessions.entrySet().iterator();
                while (sessions.size() > 512 && iterator.hasNext()) {
                    var entry = iterator.next();
                    if (!entry.getKey().equals(sessionKey) && entry.getValue().views().isEmpty()) iterator.remove();
                }
                return page;
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
    public synchronized Server registerPage(String path, PageFactory factory) {
        if (started || listener.isClosed()) throw new IllegalStateException("register routes before starting the server");
        java.util.Objects.requireNonNull(factory, "factory");
        String key = normalize(path);
        routes.put(key, new Route(session -> {
            Page page = factory.create(session);
            page.route(key);
            return page;
        }));
        return this;
    }

    /** Starts accepting requests. Repeated calls while running have no effect. */
    public synchronized void start() {
        if (listener.isClosed()) throw new IllegalStateException("server is closed");
        if (started) return;
        started = true;
        Thread acceptor = new Thread(() -> {
            while (!listener.isClosed()) {
                try {
                    Socket socket = listener.accept();
                    synchronized (Server.this) {
                        if (listener.isClosed() || !capacity.tryAcquire()) {
                            socket.close();
                            continue;
                        }
                        sockets.add(socket);
                        pool.execute(() -> {
                            try { handle(socket); }
                            finally { sockets.remove(socket); capacity.release(); }
                        });
                    }
                } catch (IOException e) {
                    if (!listener.isClosed()) {
                        System.getLogger(Server.class.getName()).log(System.Logger.Level.WARNING, "accept failed", e);
                    }
                }
            }
        }, "rejact-accept");
        acceptor.setDaemon(false);
        acceptor.start();
        keepalive.scheduleAtFixedRate(() -> views.values().forEach(View::sendPing), 30, 30, TimeUnit.SECONDS);
    }

    /** Stops accepting requests and closes all connections and server-owned workers. */
    @Override
    public void close() throws IOException {
        synchronized (this) {
            listener.close();
            keepalive.shutdownNow();
            for (Socket socket : sockets) {
                try { socket.close(); } catch (IOException ignored) { }
            }
            pool.shutdownNow();
        }
        views.values().forEach(View::close);
    }

    public int port() {
        return port;
    }

    /** Stores bytes and returns a URL the page can reference (e.g. for uploaded images). */
    public String addBlob(byte[] data, String mime) {
        String id = randomToken(16);
        if (mime == null || !mime.matches("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+")) {
            throw new IllegalArgumentException("invalid MIME type");
        }
        if (data.length > MAX_UPLOAD_BODY) throw new IllegalArgumentException("blob exceeds upload limit");
        blobs.put(id, data.clone());
        blobMimes.put(id, mime);
        return "/_rejact/blob/" + id;
    }

    /** Releases a blob previously returned by addBlob. */
    public boolean removeBlob(String url) {
        String prefix = "/_rejact/blob/";
        if (url == null || !url.startsWith(prefix)) return false;
        String id = url.substring(prefix.length());
        blobMimes.remove(id);
        return blobs.remove(id) != null;
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
            try {
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
            } catch (BadRequest | IllegalArgumentException e) {
                sendResponse(out, 400, "text/plain; charset=utf-8", null, bytes("bad request"));
            } catch (RuntimeException e) {
                System.getLogger(Server.class.getName()).log(System.Logger.Level.ERROR, "request failed", e);
                sendResponse(out, 500, "text/plain; charset=utf-8", null, bytes("internal server error"));
            }
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
        if (parts.length != 3 || !parts[2].equals("HTTP/1.1") || !parts[1].startsWith("/")) {
            throw new BadRequest("invalid request line");
        }
        Map<String, String> headers = new HashMap<>();
        String h;
        int total = line.length();
        while ((h = readLine(in)) != null && !h.isEmpty()) {
            total += h.length() + 2;
            if (total > MAX_HEADERS) throw new BadRequest("headers too large");
            int colon = h.indexOf(':');
            if (colon <= 0) throw new BadRequest("invalid header");
            String name = h.substring(0, colon).toLowerCase(Locale.ROOT);
            if (!name.matches("[!#$%&'*+.^_`|~0-9a-z-]+") || headers.putIfAbsent(name, h.substring(colon + 1).trim()) != null) {
                throw new BadRequest("duplicate or invalid header");
            }
        }
        if (h == null || !headers.containsKey("host") || headers.containsKey("transfer-encoding")) {
            throw new BadRequest("incomplete or unsupported HTTP request");
        }
        return new Request(parts[0], parts[1], headers);
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder(128);
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\r') {
                if (in.read() != '\n') throw new BadRequest("expected CRLF");
                return line.toString();
            }
            if ((b < 0x20 && b != '\t') || b == 0x7f) throw new BadRequest("invalid header character");
            line.append((char) b);
            if (line.length() > MAX_HEADERS) throw new BadRequest("header line too large");
        }
        if (!line.isEmpty()) throw new BadRequest("truncated header");
        return null;
    }

    private static byte[] readBody(InputStream in, Map<String, String> headers, int cap)
            throws IOException {
        String raw = headers.getOrDefault("content-length", "0");
        if (!raw.matches("[0-9]+")) throw new BadRequest("invalid content length");
        long len;
        try { len = Long.parseLong(raw); }
        catch (NumberFormatException e) { throw new BadRequest("invalid content length"); }
        if (len > cap) throw new BadRequest("body too large");
        byte[] body = in.readNBytes((int) len);
        if (body.length != len) throw new BadRequest("truncated body");
        return body;
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
        if (!knownSession(sid)) {
            sid = randomToken(32);
            store.app("sessions").set(sid, Long.toString(System.currentTimeMillis()));
            extra.put("Set-Cookie", "rj_sid=" + sid + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=31536000"
                    + (publicOrigin != null && "https".equals(publicOrigin.getScheme()) ? "; Secure" : ""));
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
        extra.put("Cache-Control", "private, max-age=31536000, immutable");
        extra.put("Content-Security-Policy", "sandbox; default-src 'none'");
        sendResponse(out, 200, blobMimes.getOrDefault(id, "application/octet-stream"), extra, data);
    }

    private void handleUpload(OutputStream out, Request req, byte[] body) throws IOException {
        Map<String, String> query = query(req.rawQuery());
        View view = views.get(query.getOrDefault("view", ""));
        if (view == null || !view.alive || !view.sessionKey.equals(cookies(req.headers()).get("rj_sid"))
                || !sameOrigin(req)) {
            sendResponse(out, 403, "text/plain; charset=utf-8", null, bytes("forbidden"));
            return;
        }
        synchronized (view.page) {
            String metaRaw = req.headers().get("x-rj-meta");
            Map<String, Object> meta = Json.obj(Json.parse(URLDecoder.decode(
                    metaRaw == null ? "{}" : metaRaw, StandardCharsets.UTF_8)));
            Ui.FileHandler handler = view.fileHandlers.remove(query.getOrDefault("el", ""));
            if (handler != null) {
                byte[] data = body == null ? new byte[0] : body;
                long size = data.length;
                long lastModified = meta.get("lastModified") instanceof Number n ? n.longValue() : 0L;
                view.page.batched(() -> handler.handle(new Ui(view, view.cookies),
                        new Ui.FileMeta(Json.str(meta, "name"), Json.str(meta, "type"), size,
                                lastModified), data));
            }
        }
        sendResponse(out, 204, null, null, new byte[0]);
    }

    // ---------- websocket ----------

    private void serveWs(Socket socket, InputStream in, OutputStream out, Request req)
            throws IOException {
        Map<String, String> query = query(req.rawQuery());
        String viewId = query.getOrDefault("view", "");
        String sid = cookies(req.headers()).get("rj_sid");
        if (!sameOrigin(req) || !knownSession(sid)) {
            sendResponse(out, 403, "text/plain; charset=utf-8", null, bytes("forbidden"));
            return;
        }
        String wsKey = req.headers().get("sec-websocket-key");
        if (!"GET".equals(req.method()) || !"13".equals(req.headers().get("sec-websocket-version"))
                || !java.util.Arrays.stream(req.headers().getOrDefault("connection", "").split(","))
                    .anyMatch(token -> token.trim().equalsIgnoreCase("upgrade"))
                || java.util.Base64.getDecoder().decode(wsKey).length != 16) {
            throw new BadRequest("invalid WebSocket handshake");
        }
        Route route = routes.get(normalize(query.getOrDefault("path", "/")));
        Page page = (route == null || !viewId.matches("[a-zA-Z0-9_-]{1,64}")) ? null : route.resolve(sid);
        if (page == null) {
            sendResponse(out, 400, "text/plain; charset=utf-8", null, bytes("bad ws request"));
            return;
        }
        View view = new View(viewId, page, cookies(req.headers()), out, sid, this);
        if (views.putIfAbsent(viewId, view) != null) {
            sendResponse(out, 409, "text/plain; charset=utf-8", null, bytes("view already connected"));
            return;
        }
        try {
            String accept = Ws.acceptKey(wsKey);
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            page.attach(view);
            ByteArrayOutputStream fragment = null;
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
                        if (fragment != null) throw new IOException("interleaved fragmented message");
                        if (frame.fin()) {
                            handleWsFrame(view, frame.payload());
                        } else {
                            fragment = new ByteArrayOutputStream();
                            fragment.write(frame.payload());
                        }
                    }
                    case Ws.OP_CONT -> {
                        if (fragment == null || fragment.size() + frame.payload().length > MAX_EVENT_BODY) {
                            throw new IOException("invalid or oversized fragmented message");
                        }
                        fragment.write(frame.payload());
                        if (frame.fin()) {
                            handleWsFrame(view, fragment.toByteArray());
                            fragment = null;
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
            synchronized (view.page) {
                Ui ui = new Ui(view, view.cookies);
                ui.captureViewport(msg.payload());
                // One inbound event, one outbound frame: the handler may touch a hundred
                // elements, but the browser applies the gesture in a single pass.
                view.page.batched(() -> dispatchFrame(view, ui, msg));
            }
        } catch (Exception e) {
            System.getLogger(Server.class.getName()).log(System.Logger.Level.WARNING, "event rejected", e);
            view.close();
        }
    }

    private void dispatchFrame(View view, Ui ui, Wire.Inbound msg) {
        switch (msg.type()) {
            case Wire.LOAD -> {
                for (Map.Entry<String, Object> e : Json.obj(msg.payload().get("v")).entrySet()) {
                    if (view.page.containsElement(e.getKey())) {
                        view.values.put(e.getKey(), String.valueOf(e.getValue()));
                    }
                }
                if (msg.payload().get("r") instanceof Number r && r.intValue() != 0) {
                    // Reconnect from the authoritative tree; oversized snapshots fail visibly.
                    view.send(view.page.snapshot());
                }
                view.page.fireLoad(ui);
                ui.enterFromUrl();
            }
            case Wire.BACK -> ui.enterFromUrl();
            case Wire.EVENT -> {
                Object value = msg.payload().get("value");
                if (value instanceof String s && view.page.containsElement(msg.el())) {
                    view.values.put(msg.el(), s);
                    String name = EventCodes.name(msg.evCode());
                    if ("input".equals(name) || "change".equals(name)) {
                        view.page.adoptTypedValue(msg.el(), s);
                    }
                }
                view.page.dispatch(msg.el(), EventCodes.name(msg.evCode()), ui, msg.payload());
            }
            case Wire.UNLOAD -> view.page.fireUnload(ui);
            default -> {
                // unknown message type: ignore
            }
        }
    }

    private boolean knownSession(String sid) {
        if (sid == null || !sid.matches("[a-zA-Z0-9_-]{16,64}")) return false;
        long issued = store.app("sessions").getLong(sid, 0);
        return issued > 0 && System.currentTimeMillis() - issued < TimeUnit.DAYS.toMillis(365);
    }

    private boolean sameOrigin(Request req) {
        String raw = req.headers().get("origin");
        if (raw == null) return false;
        try {
            java.net.URI origin = java.net.URI.create(raw);
            if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
                    || origin.getFragment() != null || !origin.getPath().isEmpty()) return false;
            if (publicOrigin != null) {
                return origin.getScheme().equals(publicOrigin.getScheme())
                        && origin.getRawAuthority().equalsIgnoreCase(publicOrigin.getRawAuthority());
            }
            return java.util.Set.of("http", "https").contains(origin.getScheme())
                    && origin.getRawAuthority().equalsIgnoreCase(req.headers().get("host"));
        } catch (IllegalArgumentException e) { return false; }
    }

    private static final class BadRequest extends IOException {
        BadRequest(String message) { super(message); }
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
        sb.append("X-Content-Type-Options: nosniff\r\n");
        sb.append("Referrer-Policy: same-origin\r\n");
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
            case 403 -> "Forbidden";
            case 409 -> "Conflict";
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
