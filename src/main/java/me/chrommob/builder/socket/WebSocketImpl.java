package me.chrommob.builder.socket;

import com.google.gson.Gson;
import me.chrommob.builder.WebPageBuilder;
import me.chrommob.builder.html.File;
import me.chrommob.builder.html.FileProgress;
import me.chrommob.builder.html.HtmlElement;
import me.chrommob.builder.html.events.EventTypes;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebSocketImpl extends WebSocketServer {
    private final Map<Session, Deque<String>> messageQueue = new HashMap<>();
    private final WebPageBuilder webPageBuilder;
    private final Gson gson = new Gson();
    public WebSocketImpl(int port, WebPageBuilder webPageBuilder) {
        super(new InetSocketAddress(port));
        this.webPageBuilder = webPageBuilder;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Session session = webPageBuilder.getSession(null, conn, null);
        if (session != null) {
            session.setCloseTime(System.currentTimeMillis());
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String[] cookie = null;
        String href = null;
        if (message.startsWith("session")) {
            String temp = message.substring(8);
            String[] data = temp.split(" ");
            cookie = data[0].split(";");
            for (int i = 0; i < cookie.length; i++) {
                cookie[i] = cookie[i].trim();
                if (cookie[i].contains("=")) {
                    cookie[i] = cookie[i].substring(cookie[i].indexOf("=") + 1);
                }
            }
            href = data[1].replaceAll("^.*://[^/]+", "");
        }
        Session session = webPageBuilder.getSession(href, conn, cookie);
        if (message.startsWith("event")) {
            message = message.substring(6);
            HtmlElement htmlElement = gson.fromJson(message, HtmlElement.class);
            EventTypes eventTypes = EventTypes.valueOf(htmlElement.type().toUpperCase());
            session.callEvent(eventTypes, htmlElement);
        }
        if (message.startsWith("fetch")) {
            message = message.substring(6);
            HtmlElement htmlElement = gson.fromJson(message, HtmlElement.class);
            if (htmlElement.type().equals("getEl")) {
                session.answerHtmlElement(htmlElement);
            }
            if (htmlElement.type().equals("hasEl")) {
                session.answerHasElement(htmlElement);
            }
            if (htmlElement.type().equals("getLastEl")) {
                session.answerLastChild(htmlElement);
            }
            if (htmlElement.type().equals("getFirstEl")) {
                session.answerFirstChild(htmlElement);
            }
            if (htmlElement.type().equals("hasLastEl")) {
                session.answerLastChildExists(htmlElement);
            }
            if (htmlElement.type().equals("hasFirstEl")) {
                session.answerFirstChildExists(htmlElement);
            }
        }
        if (message.startsWith("file")) {
            message = message.substring(5);
            File file = gson.fromJson(message, File.class);
            if (file.eventType().equals("getFileInfo")) {
                session.answerFileInfo(file);
                return;
            }
            session.getFileMap().put(new Session.FileMessage(file.id(), file.eventType()), file);
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        Session session = webPageBuilder.getSession(null, conn, null);
        if (session == null) {
            return;
        }
        int part = message.getInt();
        int idLength = message.getInt();
        String id = new String(message.array(), message.position(), idLength);
        message.position(message.position() + idLength);
        int typeLength = message.getInt();
        String type = new String(message.array(), message.position(), typeLength);
        message.position(message.position() + typeLength);
        File file = session.getFileMap().get(new Session.FileMessage(id, type));
        if (file == null) {
            return;
        }
        file.append(message.array(), message.position(), message.remaining());
        if (part == file.parts()) {
            session.getFileMap().remove(new Session.FileMessage(id, type));
        }
        if (type.equalsIgnoreCase("getFile")) {
            session.answerFile(new FileProgress(part, file.parts(), id), file);
            return;
        }
        session.callFileEvent(EventTypes.valueOf(type.toUpperCase()), new FileProgress(part, file.parts(), id), file);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {

    }

    @Override
    public void onStart() {
    }
}
