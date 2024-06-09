package me.chrommob.builder.socket;

import com.google.gson.Gson;
import me.chrommob.builder.WebPageBuilder;
import me.chrommob.builder.html.HtmlElement;
import me.chrommob.builder.html.events.EventTypes;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class WebSocketImpl extends WebSocketServer {
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
        Session session = webPageBuilder.getSession(conn, null);
        if (session != null) {
            session.setCloseTime(System.currentTimeMillis());
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("onMessage: " + message);
        String[] cookie = null;
        if (message.startsWith("session")) {
            String temp = message.substring(8);
            cookie = temp.split(";");
            for (int i = 0; i < cookie.length; i++) {
                cookie[i] = cookie[i].trim();
            }
        }
        Session session = webPageBuilder.getSession(conn, cookie);
        if (message.startsWith("event")) {
            message = message.substring(6);
            HtmlElement htmlElement = gson.fromJson(message, HtmlElement.class);
            EventTypes eventTypes = EventTypes.valueOf(htmlElement.type().toUpperCase());
            session.callEvent(eventTypes, htmlElement);
        }
        if (message.startsWith("fetch")) {
            message = message.substring(6);
            HtmlElement htmlElement = gson.fromJson(message, HtmlElement.class);
            session.answerHtmlElement(htmlElement);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {

    }

    @Override
    public void onStart() {

    }
}
