package me.chrommob.builder.socket;

import me.chrommob.builder.html.File;
import me.chrommob.builder.html.FileProgress;
import me.chrommob.builder.html.HtmlElement;
import me.chrommob.builder.html.constants.GlobalAttributes;
import me.chrommob.builder.html.constants.Internal;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.Tag;
import org.java_websocket.WebSocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Session {
    private final Map<String, String> cookies = new HashMap<>();
    private final Map<EventTypes, List<Tag>> eventMap;
    private final Map<EventTypes, List<Tag>> fileEventMap;
    private final Map<FileMessage, File> fileMap = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> htmlElementMap = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> hasElementMap = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> lastChildMap = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> lastChildMapExists = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> firstChildMap = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> firstChildMapExists = new HashMap<>();
    private final Map<String, BiConsumer<FileProgress, File>> fileMapCallback = new HashMap<>();
    private WebSocket webSocket;
    private final String internalCookie;
    private long closeTime;

    public Session(Map<EventTypes, List<Tag>> eventMap, Map<EventTypes, List<Tag>> fileEventMap, WebSocket webSocket) {
        this.eventMap = eventMap;
        this.fileEventMap = fileEventMap;
        this.webSocket = webSocket;
        internalCookie = Internal.generateRandomString(20);
        String js = "document.cookie = \"session=" + internalCookie + "; SameSite=Lax; Secure; Path=/;\";";
        sendMessage(js);
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

    public void callFileEvent(EventTypes eventTypes, FileProgress fileProgress, File file) {
        List<Tag> list = fileEventMap.get(eventTypes);
        for (Tag tag : list) {
            String id = tag.getAttributes().get(GlobalAttributes.ID);
            if (id.equals(file.id())) {
                tag.getFileEvents().get(eventTypes).accept(this, fileProgress, file);
                break;
            }
        }
    }

    private void sendMessage(String message) {
        if (webSocket == null || !webSocket.isOpen()) {
            return;
        }
        webSocket.send(message);
    }

    public void setInnerHtml(String id, String innerHtml) {
        String js = "document.getElementById('" + id + "').innerHTML = '" + innerHtml + "';";
        sendMessage(js);
    }

    public void setInnerHtml(String id, Tag tag) {
        String js = "document.getElementById('" + id + "').innerHTML = '" + tag.build(true) + "';";
        sendMessage(js);
    }

    public void setInnerHtml(HtmlElement htmlElement, Tag tag) {
        String id = htmlElement.id();
        String newHtml = tag.build(true);
        if (newHtml.equals(htmlElement.innerHtml())) {
            return;
        }
        String js = "document.getElementById('" + id + "').innerHTML = '" + newHtml + "';";
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
        sendMessage("var element = document.getElementById(\"" + id + "\"); sendMessage(\"fetch \" + JSON.stringify({sourceId: \"" + id + "\", id: element.id, type: \"getEl\", innerHtml: element.innerHTML, outerHtml: element.outerHTML, value: element.value}));");
    }

    public void getFile(String id, BiConsumer<FileProgress,File> consumer) {
        fileMapCallback.put(id, consumer);
        String js =
                "async function getFile() {\n" +
                "    var message_type = \"file\";\n" +
                "    var element = document.getElementById('" + id + "');\n" +
                "    var idBytes = new TextEncoder().encode(element.id);\n" +
                "    var messageTypeBytes = new TextEncoder().encode(\"getFile\");\n" +
                "    var bytes = await element.files[0].bytes();\n" +
                "    var parts = Math.ceil(bytes.length / 1048576);\n" +
                "    var json = {\n" +
                "      \"parts\": parts,\n" +
                "      \"id\": element.id,\n" +
                "      \"eventType\": \"getFile\",\n" +
                "      \"lastModified\": element.files[0].lastModified,\n" +
                "      \"name\": element.files[0].name,\n" +
                "      \"size\": element.files[0].size,\n" +
                "      \"type\": element.files[0].type\n" +
                "    };\n" +
                "    sendMessage(message_type + \" \" + JSON.stringify(json));\n" +
                "    for (var part = 0; part < parts; part++) {\n" +
                "      var start = part * 1048576;\n" +
                "      var end = Math.min((part + 1) * 1048576, bytes.length);\n" +
                "      var bytesPart = bytes.slice(start, end);\n" +
                "      var messageTypeBytesPart = new TextEncoder().encode(\"getFile\");\n" +
                "      const newBytes = new Uint8Array(8 + idBytes.length + 4 + messageTypeBytes.length + bytesPart.length);\n" +
                "      const view = new DataView(newBytes.buffer);\n" +
                "      var index = 0;\n" +
                "      view.setUint32(index, part+1);\n" +
                "      index += 4;\n" +
                "      view.setUint32(index, idBytes.length, false);\n" +
                "      index += 4;\n" +
                "      newBytes.set(idBytes, index);\n" +
                "      index += idBytes.length;\n" +
                "      view.setUint32(index, messageTypeBytes.length, false);\n" +
                "      index += 4;\n" +
                "      newBytes.set(messageTypeBytes, index);\n" +
                "      index += messageTypeBytes.length;\n" +
                "      newBytes.set(bytesPart, index);\n" +
                "      ws.send(newBytes);\n" +
                "      await new Promise(resolve => setTimeout(resolve, 1000));\n" +
                "    }\n" +
                "}\n" +
                "getFile();\n";
        sendMessage(js);
    }

    public void hasElement(String username, Consumer<HtmlElement> consumer) {
        hasElementMap.put(username, consumer);
        String js = "var element = document.getElementById('" + username + "'); ws.send(\"fetch \" + JSON.stringify({sourceId: \"" + username + "\", id: \"" + username + "\", type: \"hasEl\", innerHtml: \"\", outerHtml: \"\", eventValue: \"\", value: element != undefined}));";
        sendMessage(js);
    }

    public void getLastChild(HtmlElement htmlElement, Consumer<HtmlElement> consumer) {
        //Gets the last of the htmlElement element
        lastChildMap.put(htmlElement.id(), consumer);
        sendMessage("var element = document.getElementById(\"" + htmlElement.id() + "\").lastChild; sendMessage(\"fetch \" + JSON.stringify({sourceId: \"" + htmlElement.id() + "\", id: element.id, type: \"getLastEl\", innerHtml: element.innerHTML, outerHtml: element.outerHTML, value: element.value}));");
    }

    public void hasLastChild(HtmlElement htmlElement, Consumer<HtmlElement> consumer) {
        lastChildMapExists.put(htmlElement.sourceId(), consumer);
        String id = htmlElement.id();
        String js = "var element = document.getElementById(\"" + id + "\"); ws.send(\"fetch \" + JSON.stringify({sourceId: \"" + id + "\", id: \"" + id + "\", type: \"hasLastEl\", innerHtml: \"\", outerHtml: \"\", eventValue: \"\", value: element.lastChild != undefined}));";
        sendMessage(js);
    }

    public void getFirstChild(HtmlElement htmlElement, Consumer<HtmlElement> consumer) {
        //Gets the first of the htmlElement element
        firstChildMap.put(htmlElement.id(), consumer);
        sendMessage("var element = document.getElementById(\"" + htmlElement.id() + "\").firstChild; sendMessage(\"fetch \" + JSON.stringify({sourceId: \"" + htmlElement.id() + "\", id: element.id, type: \"getFirstEl\", innerHtml: element.innerHTML, outerHtml: element.outerHTML, value: element.value}));");
    }

    public void hasFirstChild(HtmlElement htmlElement, Consumer<HtmlElement> consumer) {
        firstChildMapExists.put(htmlElement.id(), consumer);
        String id = htmlElement.id();
        String js = "var element = document.getElementById(\"" + id + "\"); ws.send(\"fetch \" + JSON.stringify({sourceId: \"" + id + "\", id: \"" + id + "\", type: \"hasFirstEl\", innerHtml: \"\", outerHtml: \"\", eventValue: \"\", value: element.firstChild != undefined}));";
        sendMessage(js);
    }

    public void answerLastChild(HtmlElement htmlElement) {
        Consumer<HtmlElement> consumer = lastChildMap.get(htmlElement.sourceId());
        if (consumer != null) {
            consumer.accept(htmlElement);
        }
        lastChildMap.remove(htmlElement.id());
    }

    public void answerLastChildExists(HtmlElement htmlElement) {
        Consumer<HtmlElement> consumer = lastChildMapExists.get(htmlElement.sourceId());
        if (consumer != null) {
            consumer.accept(htmlElement);
        }
        lastChildMapExists.remove(htmlElement.id());
    }

    public void answerHtmlElement(HtmlElement htmlElement) {
        Consumer<HtmlElement> consumer = htmlElementMap.get(htmlElement.sourceId());
        if (consumer != null) {
            consumer.accept(htmlElement);
        }
        htmlElementMap.remove(htmlElement.id());
    }

    public void answerFile(FileProgress fileProgress, File file) {
        BiConsumer<FileProgress, File> consumer = fileMapCallback.get(file.id());
        if (consumer != null) {
            consumer.accept(fileProgress, file);
        }
        if (fileProgress.isComplete()) {
            fileMap.remove(file.id());
        }
    }

    public void answerHasElement(HtmlElement htmlElement) {
        Consumer<HtmlElement> consumer = hasElementMap.get(htmlElement.sourceId());
        if (consumer != null) {
            consumer.accept(htmlElement);
        }
        hasElementMap.remove(htmlElement.id());
    }

    public void answerFirstChild(HtmlElement htmlElement) {
        Consumer<HtmlElement> consumer = firstChildMap.get(htmlElement.sourceId());
        if (consumer != null) {
            consumer.accept(htmlElement);
        }
        firstChildMap.remove(htmlElement.id());
    }

    public void answerFirstChildExists(HtmlElement htmlElement) {
        Consumer<HtmlElement> consumer = firstChildMapExists.get(htmlElement.id());
        if (consumer != null) {
            consumer.accept(htmlElement);
        }
        firstChildMapExists.remove(htmlElement.id());
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

    public void clearCookies() {
        cookies.clear();
    }

    public void addChild(HtmlElement messages, Tag tag) {
        String id = messages.id();
        String js = "document.getElementById('" + id + "').innerHTML += '" + tag.build(true) + "';";
        sendMessage(js);
    }

    public void addFirstChild(HtmlElement messages, Tag tag) {
        String id = messages.id();
        //Set the innnerHTML to the tag and append the element.innerHTML to it
        String js = "var element = document.getElementById('" + id + "'); var innerHTML = element.innerHTML; element.innerHTML = '" + tag.build(true) + "'; element.innerHTML += innerHTML;";
        sendMessage(js);
    }

    public void removeAttribute(HtmlElement lastMessageElement, GlobalAttributes id) {
        String js = "document.getElementById('" + lastMessageElement.id() + "').removeAttribute('" + id.name().toLowerCase() + "');";
        sendMessage(js);
    }

    public void removeElementById(String id) {
        String js = "document.getElementById('" + id + "').remove();";
        sendMessage(js);
    }

    public void scrollTo(String id) {
        String js = "document.getElementById('" + id + "').scrollIntoView({behavior: 'smooth'});";
        sendMessage(js);
    }

    public Map<FileMessage, File> getFileMap() {
        return fileMap;
    }

    public static class FileMessage {
        private final String id;
        private final String eventType;

        public FileMessage(String id, String eventType) {
            this.id = id;
            this.eventType = eventType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FileMessage that = (FileMessage) o;
            return id.equals(that.id) && eventType.equals(that.eventType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, eventType);
        }
    }
}