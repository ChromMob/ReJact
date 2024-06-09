package me.chrommob.builder.socket;

import me.chrommob.builder.html.HtmlElement;
import me.chrommob.builder.html.constants.GlobalAttributes;
import me.chrommob.builder.html.constants.Internal;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.Tag;
import org.java_websocket.WebSocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Session {
    private final Map<String, String> cookies = new HashMap<>();
    private final Map<EventTypes, List<Tag>> eventMap;
    private final Map<String, Consumer<HtmlElement>> htmlElementMap = new HashMap<>();
    private WebSocket webSocket;
    private final String internalCookie;
    private long closeTime;

    public Session(Map<EventTypes, List<Tag>> eventMap, WebSocket webSocket) {
        System.out.println("New session");
        this.eventMap = eventMap;
        this.webSocket = webSocket;
        internalCookie = Internal.generateRandomString(20);
        webSocket.send("document.cookie = \"" + internalCookie + "; SameSite=Lax; Secure; Path=/\";");
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    public void setCloseTime(long closeTime) {
        this.closeTime = closeTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public void callEvent(EventTypes eventTypes, HtmlElement htmlElement) {
        List<Tag> list = eventMap.get(eventTypes);
        for (Tag tag : list) {
            String id = tag.getAttributes().get(GlobalAttributes.ID);
            if (id.equals(htmlElement.id())) {
                tag.getEvents().get(eventTypes).accept(this, htmlElement);
                break;
            }
        }
    }

    private void sendMessage(String message) {
        webSocket.send(message);
    }

    public void setInnerHtml(String id, String innerHtml) {
        String js = "document.getElementById('" + id + "').innerHTML = '" + innerHtml + "';";
        sendMessage(js);
    }

    public void setValue(String id, String value) {
        String js = "document.getElementById('" + id + "').value = '" + value + "';";
        sendMessage(js);
    }

    public void setInnerHtml(HtmlElement htmlElement, String innerHtml) {
        String id = htmlElement.id();
        String js = "document.getElementById('" + id + "').innerHTML = '" + innerHtml + "';";
        sendMessage(js);
    }

    public void setValue(HtmlElement htmlElement, String value) {
        String id = htmlElement.id();
        String js = "document.getElementById('" + id + "').value = '" + value + "';";
        sendMessage(js);
    }

    public void getHtmlElement(String id, Consumer<HtmlElement> consumer) {
        htmlElementMap.put(id, consumer);
        sendMessage("var element = document.getElementById(\"" + id + "\"); ws.send(\"fetch \" + JSON.stringify({id: element.id, type: \"get\", innerHtml: element.innerHTML, outerHtml: element.outerHTML, value: element.value}));");
    }

    public void answerHtmlElement(HtmlElement htmlElement) {
        Consumer<HtmlElement> consumer = htmlElementMap.get(htmlElement.id());
        if (consumer != null) {
            consumer.accept(htmlElement);
        }
        htmlElementMap.remove(htmlElement.id());
    }

    /**
     * Redirects the client to the given url.
     * Be careful, this resets the session as the client will be redirected to a new page.
     * @param url the url to redirect to
     */
    public void redirect(String url) {
        String js = "window.location.href = \"" + url + "\";";
        sendMessage(js);
    }

    public String getInternalCookie() {
        return internalCookie;
    }

    public void setCookie(String key, String value) {
        cookies.put(key, value);
    }

    public void removeCookie(String key) {
        cookies.remove(key);
    }

    public String getCookie(String cookie) {
        return cookies.get(cookie);
    }

    public WebSocket getWebSocket() {
        return webSocket;
    }

    public void hide(String id) {
        String js = "document.getElementById('" + id + "').classList.add('hidden');";
        sendMessage(js);
    }

    public void hide(HtmlElement htmlElement1) {
        String id = htmlElement1.id();
        String js = "document.getElementById('" + id + "').classList.add('hidden');";
        sendMessage(js);
    }

    public void show(String id) {
        String js = "document.getElementById('" + id + "').classList.remove('hidden');";
        sendMessage(js);
    }

    public void show(HtmlElement htmlElement2) {
        String id = htmlElement2.id();
        String js = "document.getElementById('" + id + "').classList.remove('hidden');";
        sendMessage(js);
    }
}

