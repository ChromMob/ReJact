package me.chrommob.builder.html.events;

public class EventBuilders {
    public static final EventBuilder perElementWithEventValue = new EventBuilder() {
        @Override
        public String build(EventTypes eventTypes, Object extraData) {
            return "for (var i = 0; i < " + eventTypes.name().toLowerCase() + ".length; i++) {\n" +
                        buildForName(eventTypes, eventTypes.name().toLowerCase() + "[i]", extraData) +
                    "}\n";
        }

        @Override
        public String buildForName(EventTypes eventTypes, String id, Object extraData) {
            String key = (String) extraData;
            return  "  document.getElementById(" + id + ").addEventListener('" + eventTypes.name().toLowerCase() + "', function (event) {\n" +
                    "    var message_type = \"event\";\n" +
                    "    var json = {\n" +
                    "      \"sourceId\": " + eventTypes.name().toLowerCase() + "[i],\n" +
                    "      \"id\": this.id,\n" +
                    "      \"type\": \"" + eventTypes.name().toLowerCase() + "\",\n" +
                    "      \"innerHtml\": this.innerHTML,\n" +
                    "      \"outerHtml\": this.outerHTML,\n" +
                    "      \"eventValue\": event." + key + ",\n" +
                    "      \"value\": this.value\n" +
                    "    };\n" +
                    "    sendMessage(message_type + \" \" + JSON.stringify(json));\n" +
                    "  });\n";
        }
    };

    public static final EventBuilder perElement = new EventBuilder() {
        @Override
        public String build(EventTypes eventTypes, Object extraData) {
            return "for (var i = 0; i < " + eventTypes.name().toLowerCase() + ".length; i++) {\n" +
                    buildForName(eventTypes, eventTypes.name().toLowerCase() + "[i]", extraData) +
                    "}\n";
        }

        @Override
        public String buildForName(EventTypes eventTypes, String id, Object extraData) {
            boolean preventDefault = (boolean) extraData;
            return "  document.getElementById(" + id + ").addEventListener('" + eventTypes.name().toLowerCase() + "', function (event) {\n" +
                    "    if (" + preventDefault + ") {\n" +
                    "      event.preventDefault();\n" +
                    "    }\n" +
                    "    var message_type = \"event\";\n" +
                    "    var json = {\n" +
                    "      \"sourceId\": this.id,\n" +
                    "      \"id\": this.id,\n" +
                    "      \"type\": \"" + eventTypes.name().toLowerCase() + "\",\n" +
                    "      \"innerHtml\": this.innerHTML,\n" +
                    "      \"outerHtml\": this.outerHTML,\n" +
                    "      \"eventValue\": \"\",\n" +
                    "      \"value\": this.value\n" +
                    "    };\n" +
                    "    sendMessage(message_type + \" \" + JSON.stringify(json));\n" +
                    "  });\n";
            }
    };
    public static final EventBuilder justRun = new EventBuilder() {
        @Override
        public String build(EventTypes eventTypes, Object extraData) {
            return "var message_type = \"event\";\n" +
                    "for (var i = 0; i < " + eventTypes.name().toLowerCase() + ".length; i++) {\n" +
                    buildForName(eventTypes, eventTypes.name().toLowerCase() + "[i]", extraData) +
                    " }\n";
        }

        @Override
        public String buildForName(EventTypes eventTypes, String id, Object extraData) {
            return "var json = {\n" +
                    "    \"sourceId\": " + id + ",\n" +
                    "    \"id\": " + id + ",\n" +
                    "    \"type\": \"" + eventTypes.name().toLowerCase() + "\",\n" +
                    "    \"innerHtml\": \"\",\n" +
                    "    \"outerHtml\": \"\",\n" +
                    "    \"eventValue\": \"\",\n" +
                    "    \"value\": \"\"\n" +
                    "};\n" +
                    "sendMessage(\"event \" + JSON.stringify(json));\n";
        }
    };
    public static final EventBuilder global = new EventBuilder() {
        @Override
        public String build(EventTypes eventTypes, Object extraData) {
            boolean preventDefault = (boolean) extraData;
            return  "var message_type = \"event\";\n" +
                    "window.addEventListener('" + eventTypes.name().toLowerCase() + "', function (event) {\n" +
                    "    if (" + preventDefault + ") {\n" +
                    "      event.preventDefault();\n" +
                    "    }\n" +
                    "    var message_type = \"event\";\n" +
                    "    for (var i = 0; i < " + eventTypes.name().toLowerCase() + ".length; i++) {\n" +
                    buildForName(eventTypes, eventTypes.name().toLowerCase() + "[i]", extraData) +
                    "    sendMessage(message_type + \" \" + JSON.stringify(json));\n" +
                    " }\n" +
                    "});\n";
        }

        @Override
        public String buildForName(EventTypes eventTypes, String id, Object extraData) {
            return "var json = {\n" +
                    "    \"sourceId\": " + id + ",\n" +
                    "    \"id\": " + id + ",\n" +
                    "    \"type\": \"" + eventTypes.name().toLowerCase() + "\",\n" +
                    "    \"innerHtml\": \"\",\n" +
                    "    \"outerHtml\": \"\",\n" +
                    "    \"eventValue\": \"\",\n" +
                    "    \"value\": \"\"\n" +
                    "};\n" +
                    "sendMessage(\"event \" + JSON.stringify(json));\n";
        }
    };

    public static final EventBuilder timer = new EventBuilder() {
        @Override
        public String build(EventTypes eventTypes, Object extraData) {
            return "var timer = setInterval(function() {\n" +
                    "    var message_type = \"event\";\n" +
                    "    for (var i = 0; i < " + eventTypes.name().toLowerCase() + ".length; i++) {\n" +
                    buildForName(eventTypes, eventTypes.name().toLowerCase() + "[i]", extraData) +
                    "    if (ws.readyState == WebSocket.OPEN) {\n" +
                    "    sendMessage(message_type + \" \" + JSON.stringify(json));\n" +
                    "    }\n" +
                    "    else {\n" +
                    "      messages.push(message_type + \" \" + JSON.stringify(json));\n" +
                    "    }\n" +
                    " }\n" +
                    "}, " + extraData + ");\n";
        }

        @Override
        public String buildForName(EventTypes eventTypes, String id, Object extraData) {
            return "var json = {\n" +
                    "    \"sourceId\": " + id + ",\n" +
                    "    \"id\": " + id + ",\n" +
                    "    \"type\": \"" + eventTypes.name().toLowerCase() + "\",\n" +
                    "    \"innerHtml\": \"\",\n" +
                    "    \"outerHtml\": \"\",\n" +
                    "    \"eventValue\": \"\",\n" +
                    "    \"value\": \"\"\n" +
                    "};\n" +
                    "sendMessage(\"event \" + JSON.stringify(json));\n";
        }
    };

    public static final EventBuilder getFile = new EventBuilder() {
        @Override
        public String build(EventTypes eventTypes, Object extraData) {
            return  "var message_type = \"file\";\n" +
                    "for (var i = 0; i < " + eventTypes.name().toLowerCase() + ".length; i++) {\n" +
                    buildForName(eventTypes, eventTypes.name().toLowerCase() + "[i]", extraData) +
                    "}\n";
        }

        public String buildForName(EventTypes eventTypes, String id, Object extraData) {
            return "var element = document.getElementById(" + id + ");\n" +
                    "element.addEventListener('" + eventTypes.name().toLowerCase() + "', async function (event) {\n" +
                    "    var idBytes = new TextEncoder().encode(element.id);\n" +
                    "    var messageTypeBytes = new TextEncoder().encode(\"" + eventTypes.name().toLowerCase() + "\");\n" +
                    "    var bytes = await this.files[0].bytes();\n" +
                    "    var parts = Math.ceil(bytes.length / 1024);\n" +
                    "    var json = {\n" +
                    "      \"parts\": parts,\n" +
                    "      \"id\": element.id,\n" +
                    "      \"eventType\": \"" + eventTypes.name().toLowerCase() + "\",\n" +
                    "      \"lastModified\": element.files[0].lastModified,\n" +
                    "      \"name\": element.files[0].name,\n" +
                    "      \"size\": element.files[0].size,\n" +
                    "      \"type\": element.files[0].type\n" +
                    "    };\n" +
                    "    sendMessage(\"file \" + JSON.stringify(json));\n" +
                    "    const newBytes = new Uint8Array(8 + idBytes.length + 4 + messageTypeBytes.length + bytes.length);\n" +
                    "    const view = new DataView(newBytes.buffer);\n" +
                    "    var index = 0;\n" +
                    "    view.setUint32(index, idBytes.length, true);\n" +
                    "    index += 4;\n" +
                    "    newBytes.set(idBytes, index);\n" +
                    "    index += idBytes.length;\n" +
                    "    view.setUint32(index, messageTypeBytes.length, true);\n" +
                    "}\n";
        }
    };

    public abstract static class EventBuilder {
        public abstract String build(EventTypes eventTypes, Object extraData);

        public abstract String buildForName(EventTypes eventTypes, String id, Object extraData);
    }
}
