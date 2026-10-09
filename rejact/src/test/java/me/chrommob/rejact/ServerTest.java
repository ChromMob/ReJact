package me.chrommob.rejact;

import me.chrommob.rejact.gen.EventCodes;
import me.chrommob.rejact.gen.tags.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ServerTest {
    @TempDir Path dir;
    Server server;
    Button button;
    Input file;
    AtomicInteger clicks = new AtomicInteger();
    AtomicInteger uploads = new AtomicInteger();

    @BeforeEach void start() throws Exception {
        server = new Server(new InetSocketAddress("127.0.0.1", 0), dir, null);
        Page page = new Page("/");
        Div label = new Div().text("initial");
        button = new Button().text("click").onClick((ui, event) -> {
            label.text("clicked " + clicks.incrementAndGet());
            ui.readFile(file, (u, meta, bytes) -> uploads.set(bytes.length));
        });
        file = new Input().type(Input.Type.FILE);
        page.root().add(new Body().add(button, label, file));
        server.registerPage("/", page);
        server.start();
        server.start();
    }
    @AfterEach void stop() throws Exception { server.close(); }

    @Test void serverIssuesHttpOnlySessionAndDoesNotExposeItInHtml() throws Exception {
        String response = request("GET / HTTP/1.1\r\nCookie: rj_sid=attackerchosen000000000\r\n");
        assertTrue(response.startsWith("HTTP/1.1 200"));
        String cookie = cookie(response);
        assertFalse(cookie.contains("attackerchosen"));
        assertTrue(response.contains("HttpOnly"));
        assertFalse(response.substring(response.indexOf("\r\n\r\n")).contains(cookie.substring(7)));
        assertFalse(request("GET / HTTP/1.1\r\nCookie: " + cookie + "\r\n").contains("Set-Cookie:"));
        assertTrue(request("GET /_rejact/runtime.js HTTP/1.1\r\n").contains("RJ_PULL"));
    }

    @Test void rejectsCrossOriginAndQueryOnlySessions() throws Exception {
        String cookie = cookie(request("GET / HTTP/1.1\r\n"));
        assertTrue(handshake("a", cookie, "https://evil.example", "").startsWith("HTTP/1.1 403"));
        assertTrue(handshake("a", "", origin(), "&sid=" + cookie.substring(7)).startsWith("HTTP/1.1 403"));
        assertTrue(request("POST /_rejact/upload?view=a&el=x HTTP/1.1\r\nContent-Length: 0\r\n").startsWith("HTTP/1.1 403"));
    }

    @Test void eventTraversesWebsocketAndUploadRequiresOwningSession() throws Exception {
        String cookie = cookie(request("GET / HTTP/1.1\r\n"));
        try (Socket socket = connect()) {
            socket.getOutputStream().write(upgrade("tab", cookie, origin(), "").getBytes(StandardCharsets.US_ASCII));
            assertTrue(headers(socket.getInputStream()).startsWith("HTTP/1.1 101"));
            assertTrue(handshake("tab", cookie, origin(), "").startsWith("HTTP/1.1 409"));
            byte[] uid = button.uid().getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream event = new ByteArrayOutputStream();
            event.write(new byte[]{2, (byte)EventCodes.code("click"), (byte)uid.length});
            event.write(uid);
            event.write(new byte[]{2, 0, 0, 0, '{', '}'});
            masked(socket.getOutputStream(), 0x82, event.toByteArray());
            // One gesture, one frame: the handler's text write and its readFile command are
            // batched together rather than costing the browser two separate DOM passes.
            byte[] response = readServerFrame(socket.getInputStream());
            assertEquals(0xb2, response[0] & 255);
            assertEquals(2, response[1] & 255, "the click's two ops must share one frame");
            assertTrue(new String(response, StandardCharsets.UTF_8).contains("clicked 1"));
            String url = "/_rejact/upload?view=tab&el=" + file.uid();
            String other = cookie(request("GET / HTTP/1.1\r\n"));
            assertTrue(upload(url, other).startsWith("HTTP/1.1 403"));
            assertEquals(0, uploads.get());
            assertTrue(upload(url, cookie).startsWith("HTTP/1.1 204"));
            assertEquals(3, uploads.get());
        }
    }

    @Test void rejectsOversizedFragmentedMessage() throws Exception {
        String cookie = cookie(request("GET / HTTP/1.1\r\n"));
        try (Socket socket = connect()) {
            socket.getOutputStream().write(upgrade("fragment", cookie, origin(), "").getBytes(StandardCharsets.US_ASCII));
            assertTrue(headers(socket.getInputStream()).startsWith("HTTP/1.1 101"));
            masked(socket.getOutputStream(), 0x02, new byte[Wire.MAX_MESSAGE / 2]);
            masked(socket.getOutputStream(), 0x80, new byte[Wire.MAX_MESSAGE / 2 + 1]);
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test void malformedHttpReturnsBadRequestAndServerKeepsServing() throws Exception {
        for (String h : new String[]{"Content-Length: -1", "Content-Length: 999999999999999999999999",
                "Content-Length: 0\r\nContent-Length: 1", "Transfer-Encoding: chunked",
                "X-Large: " + "x".repeat(33000),
                "X-One: " + "x".repeat(17000) + "\r\nX-Two: " + "x".repeat(17000)}) {
            assertTrue(request("POST / HTTP/1.1\r\n" + h + "\r\n").startsWith("HTTP/1.1 400"));
        }
        assertTrue(request("GET / HTTP/1.1\r\n").startsWith("HTTP/1.1 200"));
    }

    @Test void closeReleasesListenerAndLiveConnections() throws Exception {
        int port = server.port();
        try (Socket socket = connect()) {
            server.close();
            // A connection accepted just before close is also closed by the accept loop.
            assertEquals(-1, socket.getInputStream().read());
        }
        server.close();
        assertThrows(IllegalStateException.class, server::start);
        try (ServerSocket rebound = new ServerSocket()) {
            rebound.setReuseAddress(true);
            rebound.bind(new InetSocketAddress("127.0.0.1", port));
        }
    }

    @Test void configuredHttpsOriginSetsSecureCookieAndControlsWebsocket() throws Exception {
        server.close();
        server = new Server(new InetSocketAddress("127.0.0.1", 0), dir, URI.create("https://public.example"));
        server.registerPage("/", new Page("/"));
        server.start();
        String response = request("GET / HTTP/1.1\r\n");
        assertTrue(response.contains("; Secure"));
        String cookie = cookie(response);
        assertTrue(handshake("external", cookie, origin(), "").startsWith("HTTP/1.1 403"));
        assertTrue(handshake("external", cookie, "https://public.example", "").startsWith("HTTP/1.1 101"));
    }

    @Test void blobsCopyInputRejectHeaderInjectionAndCanBeReleased() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        String url = server.addBlob(bytes, "text/plain");
        bytes[0] = 'X';
        String response = request("GET " + url + " HTTP/1.1\r\n");
        assertTrue(response.endsWith("hello"));
        assertTrue(response.contains("X-Content-Type-Options: nosniff"));
        assertTrue(response.contains("Content-Security-Policy: sandbox"));
        assertThrows(IllegalArgumentException.class, () -> server.addBlob(bytes, "text/plain\r\nInjected: yes"));
        assertTrue(server.removeBlob(url));
        assertTrue(request("GET " + url + " HTTP/1.1\r\n").startsWith("HTTP/1.1 404"));
    }

    private String upload(String url, String cookie) throws Exception {
        return request("POST " + url + " HTTP/1.1\r\nOrigin: " + origin() + "\r\nCookie: " + cookie
                + "\r\nContent-Length: 3\r\n", "abc");
    }
    private String origin() { return "http://127.0.0.1:" + server.port(); }
    private Socket connect() throws Exception {
        Socket socket = new Socket("127.0.0.1", server.port());
        socket.setSoTimeout(5000);
        return socket;
    }
    private String request(String start) throws Exception { return request(start, ""); }
    private String request(String start, String body) throws Exception {
        try (Socket socket = connect()) {
            socket.getOutputStream().write((start + "Host: 127.0.0.1:" + server.port() + "\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private String handshake(String view, String cookie, String origin, String query) throws Exception {
        try (Socket socket = connect()) {
            socket.getOutputStream().write(upgrade(view, cookie, origin, query).getBytes(StandardCharsets.US_ASCII));
            return headers(socket.getInputStream());
        }
    }
    private String upgrade(String view, String cookie, String origin, String query) {
        return "GET /_rejact/ws?path=/&view=" + view + query + " HTTP/1.1\r\nHost: 127.0.0.1:" + server.port()
                + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nOrigin: " + origin + "\r\nCookie: " + cookie + "\r\n\r\n";
    }
    private static String cookie(String response) {
        return response.lines().filter(line -> line.startsWith("Set-Cookie:")).findFirst().orElseThrow()
                .substring("Set-Cookie: ".length()).split(";", 2)[0];
    }
    private static String headers(InputStream in) throws Exception {
        StringBuilder result = new StringBuilder();
        while (!result.toString().endsWith("\r\n\r\n")) {
            int b = in.read();
            if (b < 0) throw new EOFException();
            result.append((char)b);
        }
        return result.toString();
    }
    private static void masked(OutputStream out, int first, byte[] payload) throws Exception {
        out.write(first);
        if (payload.length < 126) out.write(0x80 | payload.length);
        else if (payload.length < 65536) {
            out.write(0xfe); out.write(payload.length >>> 8); out.write(payload.length);
        } else {
            out.write(0xff);
            for (int i = 7; i >= 0; i--) out.write((int)((long)payload.length >>> (8 * i)));
        }
        out.write(new byte[4]);
        out.write(payload);
        out.flush();
    }
    private static byte[] readServerFrame(InputStream in) throws Exception {
        assertEquals(0x82, in.read());
        int length = in.read();
        if (length == 126) length = (in.read() << 8) | in.read();
        assertTrue(length >= 0 && length < 65536);
        return in.readNBytes(length);
    }
}
