package me.chrommob.builder;

import io.javalin.Javalin;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.Tag;
import me.chrommob.builder.socket.Session;
import me.chrommob.builder.socket.WebSocketImpl;
import org.java_websocket.WebSocket;

import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WebPageBuilder {
    private final String ip;
    private final int clientPort;
    private final Javalin app;
    private final Map<String, WebSocket> webSockets = new ConcurrentHashMap<>();
    private final Map<WebSocket, Session> sessions = new ConcurrentHashMap<>();
    private final Map<EventTypes, List<Tag>> eventMap = new HashMap<>();

    public WebPageBuilder(String ip, int webPort, int serverPort, int clientPort) {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (Session session : sessions.values()) {
                    if (session.getCloseTime() != 0) {
                        if (System.currentTimeMillis() - session.getCloseTime() > 10000) {
                            webSockets.remove(session.getInternalCookie());
                            sessions.remove(session.getWebSocket());
                        }
                    }
                }
            }
        }).start();
        this.ip = ip;
        this.clientPort = clientPort;
        WebSocketImpl webSocket = new WebSocketImpl(serverPort, this);
        webSocket.start();
        app = Javalin.create().start(webPort);
        try (FileInputStream fis = new FileInputStream("src/main/resources/favicon.ico")) {
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);
            app.get("/favicon.ico", ctx -> ctx.contentType("image/x-icon").result(buffer));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Page newPage(String path) {
        Page page = new Page(path, ip, clientPort, eventMap);
        app.get(path, ctx -> ctx.html(page.getHtmlString()));
        return page;
    }

    public Session getSession(WebSocket conn, String[] cookie) {
        if (cookie != null) {
            for (String c : cookie) {
                WebSocket webSocket = webSockets.get(c);
                if (webSocket != null) {
                    sessions.put(conn, sessions.get(webSocket));
                    sessions.remove(webSocket);
                    webSockets.put(c, conn);
                    return sessions.get(conn);
                }
            }
        }
        Session session = sessions.get(conn);
        if (session == null) {
            session = new Session(eventMap, conn);
            sessions.put(conn, session);
            webSockets.put(session.getInternalCookie(), conn);
        } else {
            session.setWebSocket(conn);
        }
        return session;
    }
}
