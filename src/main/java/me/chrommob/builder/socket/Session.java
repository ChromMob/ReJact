package me.chrommob.builder.socket;

import me.chrommob.builder.Page;
import me.chrommob.builder.WebPageBuilder;
import me.chrommob.builder.html.File;
import me.chrommob.builder.html.FileProgress;
import me.chrommob.builder.html.HtmlElement;
import me.chrommob.builder.html.constants.GlobalAttributes;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;
import me.chrommob.builder.utils.Internal;

import org.apache.commons.lang3.function.TriConsumer;
import org.java_websocket.WebSocket;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Session {
    private final List<String> messageQueue = new ArrayList<>();
    private final SessionData sessionData;
    private final Map<EventTypes, Set<Tag>> eventMap;
    private final Map<EventTypes, Set<Tag>> fileEventMap;
    private final Map<FileMessage, File> fileMap = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> htmlElementMap = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> hasElementMap = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> lastChildMap = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> lastChildMapExists = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> firstChildMap = new HashMap<>();
    private final Map<String, Consumer<HtmlElement>> firstChildMapExists = new HashMap<>();
    private final Map<String, BiConsumer<FileProgress, File>> fileMapCallback = new HashMap<>();
    private final Map<String, Consumer<File>> fileInfoMap = new HashMap<>();
    private WebSocket webSocket;
    private Page page;
    private final String internalCookie;
    private long closeTime;

    public Session(Page page, Map<EventTypes, Set<Tag>> eventMap, Map<EventTypes, Set<Tag>> fileEventMap, WebSocket webSocket) {
        this.page = page;
        this.eventMap = eventMap;
        this.fileEventMap = fileEventMap;
        this.webSocket = webSocket;
        internalCookie = Internal.generateRandomString(20);
        this.sessionData = WebPageBuilder.getSessionDataGetter().getData(internalCookie);
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
        Set<Tag> list = eventMap.get(eventTypes);
        for (Tag tag : list) {
            String id = tag.getAttributes().get(GlobalAttributes.ID);
            if (id.equals(htmlElement.id())) {
                tag.getEvents().get(eventTypes).accept(this, htmlElement);
                break;
            }
        }
    }

    public void callFileEvent(EventTypes eventTypes, FileProgress fileProgress, File file) {
        Set<Tag> list = fileEventMap.get(eventTypes);
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
            messageQueue.add(message);
            return;
        }
        for (String s : messageQueue) {
            webSocket.send(s);
        }
        messageQueue.clear();
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

    public void clearValue(String id) {
        String js = "document.getElementById('" + id + "').value = null;";
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
                        "    var arrayBuffer = await element.files[0].arrayBuffer();\n" +
                        "    var bytes = new Uint8Array(arrayBuffer);\n" +
                        "    var parts = Math.ceil(bytes.length / 102400);\n" +
                        "    var json = {\n" +
                        "        \"exists\": true,\n" +
                        "        \"parts\": parts,\n" +
                        "        \"id\": element.id,\n" +
                        "        \"eventType\": \"getFile\",\n" +
                        "        \"lastModified\": element.files[0].lastModified,\n" +
                        "        \"name\": element.files[0].name,\n" +
                        "        \"size\": element.files[0].size,\n" +
                        "        \"type\": element.files[0].type\n" +
                        "    };\n" +
                        "    sendMessage(message_type + \" \" + JSON.stringify(json));\n" +
                        "\n" +
                        "    var part = 0;\n" +
                        "    function sendNextPart() {\n" +
                        "        if (part < parts) {\n" +
                        "            var start = part * 102400;\n" +
                        "            var end = Math.min((part + 1) * 102400, bytes.length);\n" +
                        "            var bytesPart = bytes.slice(start, end);\n" +
                        "            var messageTypeBytesPart = new TextEncoder().encode(\"getFile\");\n" +
                        "            const newBytes = new Uint8Array(8 + idBytes.length + 4 + messageTypeBytes.length + bytesPart.length);\n" +
                        "            const view = new DataView(newBytes.buffer);\n" +
                        "            var index = 0;\n" +
                        "            view.setUint32(index, part + 1);\n" +
                        "            index += 4;\n" +
                        "            view.setUint32(index, idBytes.length, false);\n" +
                        "            index += 4;\n" +
                        "            newBytes.set(idBytes, index);\n" +
                        "            index += idBytes.length;\n" +
                        "            view.setUint32(index, messageTypeBytes.length, false);\n" +
                        "            index += 4;\n" +
                        "            newBytes.set(messageTypeBytes, index);\n" +
                        "            index += messageTypeBytes.length;\n" +
                        "            newBytes.set(bytesPart, index);\n" +
                        "            sendMessage(newBytes);\n" +
                        "            part++;\n" +
                        "            setTimeout(sendNextPart, 100)" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    sendNextPart();\n" +
                        "}\n" +
                        "\n" +
                        "getFile();";
        sendMessage(js);
    }

    public void getFileInfo(String file, Consumer<File> consumer) {
        fileInfoMap.put(file, consumer);
        String js =
                "    var message_type = \"file\";\n" +
                "    var element = document.getElementById('" + file + "');\n" +
                "    var exists = element.files != null && element.files.length > 0 && element.files[0] != undefined;\n" +
                "    if (exists) {\n" +
                "        var bytes = new Uint8Array(element.files[0].arrayBuffer());\n" +
                "        var parts = Math.ceil(bytes.length / 102400);\n" +
                "        var json = {\n" +
                "            \"exists\": true,\n" +
                "            \"parts\": parts,\n" +
                "            \"id\": element.id,\n" +
                "            \"eventType\": \"getFileInfo\",\n" +
                "            \"lastModified\": element.files[0].lastModified,\n" +
                "            \"name\": element.files[0].name,\n" +
                "            \"size\": element.files[0].size,\n" +
                "            \"type\": element.files[0].type\n" +
                "        };\n" +
                "        sendMessage(message_type + \" \" + JSON.stringify(json));\n" +
                "    } else {\n" +
                "        sendMessage(message_type + \" \" + JSON.stringify({exists: false, id: element.id, eventType: \"getFileInfo\"}));\n" +
                "    }\n";
        sendMessage(js);
    }

    public void hasElement(String username, Consumer<HtmlElement> consumer) {
        hasElementMap.put(username, consumer);
        String js = "var element = document.getElementById('" + username + "'); sendMessage(\"fetch \" + JSON.stringify({sourceId: \"" + username + "\", id: \"" + username + "\", type: \"hasEl\", innerHtml: \"\", outerHtml: \"\", eventValue: \"\", value: (element != undefined).toString()}));";
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
        String js = "var element = document.getElementById(\"" + id + "\"); sendMessage(\"fetch \" + JSON.stringify({sourceId: \"" + id + "\", id: \"" + id + "\", type: \"hasLastEl\", innerHtml: \"\", outerHtml: \"\", eventValue: \"\", value: (element.lastChild != undefined).toString()}));";
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
        String js = "var element = document.getElementById(\"" + id + "\"); sendMessage(\"fetch \" + JSON.stringify({sourceId: \"" + id + "\", id: \"" + id + "\", type: \"hasFirstEl\", innerHtml: \"\", outerHtml: \"\", eventValue: \"\", value: (element.firstChild != undefined).toString()}));";
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

    public void answerFileInfo(File file) {
        Consumer<File> consumer = fileInfoMap.get(file.id());
        if (consumer != null) {
            consumer.accept(file);
        }
        fileInfoMap.remove(file.id());
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
        sessionData.setCookie(key, value);
    }

    public void removeCookie(String key) {
        sessionData.removeCookie(key);
    }

    public String getCookie(String cookie) {
        return sessionData.getCookie(cookie);
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
        sessionData.clearCookies();
    }

    public void addChild(HtmlElement messages, Tag tag) {
        String id = messages.id();
        String jsEvents = handleRuntimeEventAdd(tag);
        String js = "document.getElementById('" + id + "').innerHTML += '" + tag.build(true) + "';";
        sendMessage(js + "\n" + jsEvents);
    }

    public void addChild(String id, Tag tag) {
        String jsEvents = handleRuntimeEventAdd(tag);
        String js = "document.getElementById('" + id + "').innerHTML += '" + tag.build(true) + "';";
        sendMessage(js + "\n" + jsEvents);
    }

    public void addChild(HtmlElement messages, String html) {
        String id = messages.id();
        String js = "document.getElementById('" + id + "').innerHTML += '" + html + "';";
        sendMessage(js);
    }

    public void addFirstChild(HtmlElement messages, Tag tag) {
        String id = messages.id();
        String jsEvents = handleRuntimeEventAdd(tag);
        String js = "var element = document.getElementById('" + id + "'); var innerHTML = element.innerHTML; element.innerHTML = '" + tag.build(true) + "'; element.innerHTML += innerHTML;";
        sendMessage(js + "\n" + jsEvents);
    }

    @NotNull
    private static Map<Tag, Set<EventTypes>> getTagSetMap(Tag tag) {
        Map<Tag, Set<EventTypes>> neededEventTypes = new HashMap<>();
        for (Map.Entry<Tag, Map<EventTypes, BiConsumer<Session, HtmlElement>>> entry : tag.getAllEvents().entrySet()) {
            Tag tag1 = entry.getKey();
            neededEventTypes.compute(tag1, (k, v) -> {
                if (v == null) {
                    return new HashSet<>(entry.getValue().keySet());
                } else {
                    v.addAll(entry.getValue().keySet());
                    return v;
                }
            });
        }
        for (Map.Entry<Tag, Map<EventTypes, TriConsumer<Session, FileProgress, File>>> entry : tag.getAllFileEvents().entrySet()) {
            Tag tag1 = entry.getKey();
            neededEventTypes.compute(tag1, (k, v) -> {
                if (v == null) {
                    return new HashSet<>(entry.getValue().keySet());
                } else {
                    v.addAll(entry.getValue().keySet());
                    return v;
                }
            });
        }
        return neededEventTypes;
    }

    private String handleRuntimeEventAdd(Tag tag) {
        StringBuilder jsEvents = new StringBuilder();
        jsEvents.append("setTimeout(function() {\n");
        Map<Tag, Set<EventTypes>> neededEventTypes = getTagSetMap(tag);
        for (Map.Entry<Tag, Set<EventTypes>> entry : neededEventTypes.entrySet()) {
            Tag tag1 = entry.getKey();
            Set<EventTypes> eventTypes = entry.getValue();
            for (EventTypes eventTypes1 : eventTypes) {
                eventMap.compute(eventTypes1, (k, v) -> {
                    if (v == null) {
                        v = new HashSet<>();
                    }
                    v.add(tag1);
                    return v;
                });
                jsEvents.append(eventTypes1.buildForName("\"" + tag1.id() + "\"")).append("\n");
            }
        }
        jsEvents.append("}, 500);\n");
        return jsEvents.toString();
    }

    private void handleRuntimeEventRemove(Tag tag) {
        Map<Tag, Set<EventTypes>> neededEventTypes = getTagSetMap(tag);
        for (Map.Entry<Tag, Set<EventTypes>> entry : neededEventTypes.entrySet()) {
            Tag tag1 = entry.getKey();
            Set<EventTypes> eventTypes = entry.getValue();
            for (EventTypes eventTypes1 : eventTypes) {
                eventMap.compute(eventTypes1, (k, v) -> {
                    if (v == null) {
                        v = new HashSet<>();
                    }
                    v.remove(tag1);
                    return v;
                });
            }
        }
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

    public Page getPage() {
        return page;
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

	public void setPage(Page page2) {
        page = page2;
	}
}