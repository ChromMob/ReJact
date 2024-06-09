package me.chrommob.builder;

import in.wilsonl.minifyhtml.Configuration;
import in.wilsonl.minifyhtml.MinifyHtml;
import me.chrommob.builder.css.CSSBuilder;
import me.chrommob.builder.html.FileUtils;
import me.chrommob.builder.html.HtmlElement;
import me.chrommob.builder.html.constants.Internal;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;
import me.chrommob.builder.socket.Session;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static me.chrommob.builder.html.constants.GlobalAttributes.HREF;

public class Page {
    private final Configuration configuration = new Configuration.Builder().setMinifyJs(true).setMinifyCss(true).build();
    private final String path;
    private final String ip;
    private final int clientPort;
    private final Map<EventTypes, List<Tag>> eventMap;
    private final Map<EventTypes, List<Tag>> localEventMap = new HashMap<>();
    public Page(String path, String ip, int clientPort, Map<EventTypes, List<Tag>> eventMap) {
        this.path = path;
        this.ip = ip;
        this.clientPort = clientPort;
        this.eventMap = eventMap;
    }
    private final Tag root = new RootHtmlTag();
    private final CSSBuilder css = new CSSBuilder();

    private String cssString;
    private String htmlString;

    public void build() {
        cssString = buildCss();
        htmlString = buildHtml();
    }

    public String getHtmlString() {
        if (htmlString == null) {
            build();
        }
        return htmlString;
    }

    private String buildHtml() {
        List<Tag> tags = root.getAllChildren();
        for (Tag tag : tags) {
            for (Map.Entry<EventTypes, BiConsumer<Session, HtmlElement>> entry : tag.getEvents().entrySet()) {
                List<Tag> list = eventMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
                List<Tag> localList = localEventMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
                list.add(tag);
                localList.add(tag);
            }
        }
        root.addChild(new ScriptTag(buildEventScript()));
        List<Tag> htmlTags = root.getChildrenByClass(HtmlTag.class);
        if (htmlTags.isEmpty()) {
            root.addChild(new HtmlTag());
        }
        Tag htmlTag = htmlTags.getFirst();
        List<Tag> headTags = htmlTag.getChildrenByClass(HeadTag.class);
        Tag headTag;
        if (headTags.isEmpty()) {
            headTag = new HeadTag();
            htmlTag.addChild(headTag);
        } else {
            headTag = headTags.getFirst();
        }
        headTag.addChild(new BaseTag().addAttribute(HREF, path.endsWith("/") ? path : path + "/"));
        htmlTag.addChild(new StyleTag().plainText(cssString));
        return MinifyHtml.minify(root.build(), configuration);
    }

    private String buildEventScript() {
        StringBuilder builder = new StringBuilder();
        for (EventTypes eventTypes : localEventMap.keySet()) {
            List<Tag> list = localEventMap.get(eventTypes);
            List<String> ids = list.stream().map(Tag::id).toList();
            String idsString = ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
            builder.append("var ").append(eventTypes.name().toLowerCase()).append(" = [").append(idsString).append("];\n");
            if (eventTypes == EventTypes.BEFORELOAD) {
                builder.append("for (var i = 0; i < ").append(eventTypes.name().toLowerCase()).append(".length; i++) {\n");
               builder.append("   var json = {\n");
               builder.append("     \"id\": ").append(eventTypes.name().toLowerCase()).append("[i]\n,");
               builder.append("     \"type\": \"").append(eventTypes.name().toLowerCase()).append("\",\n");
               builder.append("     \"innerHtml\": \"\",\n");
               builder.append("     \"outerHtml\": \"\",\n");
               builder.append("     \"eventValue\": \"\",\n");
               builder.append("     \"value\": \"\"\n");
               builder.append("   };\n");
               builder.append("   if (ws.readyState == WebSocket.OPEN) {\n");
               builder.append("     ws.send(\"event\" + \" \" + JSON.stringify(json));\n");
               builder.append("   }\n");
               builder.append("   else {\n");
               builder.append("     messages.push(\"event\" + \" \" + JSON.stringify(json));\n");
               builder.append("   }\n");
               builder.append(" }\n");
            }
            if (eventTypes == EventTypes.KEYDOWN) {
                builder.append("for (var i = 0; i < ").append(eventTypes.name().toLowerCase()).append(".length; i++) {\n");
                builder.append("  document.getElementById(").append(eventTypes.name().toLowerCase()).append("[i]).addEventListener('").append(eventTypes.name().toLowerCase()).append("', function (event) {\n");
                builder.append("    var message_type = \"event\";\n");
                builder.append("    var json = {\n");
                builder.append("      \"id\": this.id,\n");
                builder.append("      \"type\": \"").append(eventTypes.name().toLowerCase()).append("\",\n");
                builder.append("      \"innerHtml\": this.innerHTML,\n");
                builder.append("      \"outerHtml\": this.outerHTML,\n");
                builder.append("      \"eventValue\": event.key,\n");
                builder.append("      \"value\": this.value\n");
                builder.append("    };\n");
                builder.append("    ws.send(message_type + \" \" + JSON.stringify(json));\n");
                builder.append("  });\n");
                builder.append("}\n");
            }
            if (eventTypes == EventTypes.LOAD) {
                builder.append("window.addEventListener('").append(eventTypes.name().toLowerCase()).append("', function () {\n");
                builder.append("    var message_type = \"event\";\n");
                builder.append("    for (var i = 0; i < ").append(eventTypes.name().toLowerCase()).append(".length; i++) {\n");
                builder.append("    var json = {\n");
                builder.append("      \"id\": ").append(eventTypes.name().toLowerCase()).append("[i]\n,");
                builder.append("      \"type\": \"").append(eventTypes.name().toLowerCase()).append("\",\n");
                builder.append("      \"innerHtml\": \"\",\n");
                builder.append("      \"outerHtml\": \"\",\n");
                builder.append("      \"eventValue\": \"\",\n");
                builder.append("      \"value\": \"\"\n");
                builder.append("    };\n");
                builder.append("    if (ws.readyState == WebSocket.OPEN) {\n");
                builder.append("    ws.send(message_type + \" \" + JSON.stringify(json));\n");
                builder.append("    }\n");
                builder.append("    else {\n");
                builder.append("      messages.push(message_type + \" \" + JSON.stringify(json));\n");
                builder.append("    }\n");
                builder.append(" }\n");
                builder.append("});\n");
            }
            if (eventTypes == EventTypes.CLICK || eventTypes == EventTypes.MOUSEOVER || eventTypes == EventTypes.MOUSEOUT) {
                builder.append("for (var i = 0; i < ").append(eventTypes.name().toLowerCase()).append(".length; i++) {\n");
                builder.append("  document.getElementById(").append(eventTypes.name().toLowerCase()).append("[i]).addEventListener('").append(eventTypes.name().toLowerCase()).append("', function () {\n");
                builder.append("    var message_type = \"event\";\n");
                builder.append("    var json = {\n");
                builder.append("      \"id\": this.id,\n");
                builder.append("      \"type\": \"").append(eventTypes.name().toLowerCase()).append("\",\n");
                builder.append("      \"innerHtml\": this.innerHTML,\n");
                builder.append("      \"outerHtml\": this.outerHTML,\n");
                builder.append("      \"eventValue\": \"\",\n");
                builder.append("      \"value\": this.value\n");
                builder.append("    };\n");
                builder.append("    ws.send(message_type + \" \" + JSON.stringify(json));\n");
                builder.append("  });\n");
                builder.append("}\n");
            }
        }
        return builder.toString();
    }

    private String buildCss() {
        List<Tag> tags = root.getAllChildren();
        Map<String, List<Tag>> tagMap = new HashMap<>();
        Map<Tag, List<String>> tagMap2 = new HashMap<>();
        for (Tag tag : tags) {
            for (Map.Entry<String, String> entry : tag.getCssAttributes().entrySet()) {
                String key = entry.getKey() + ":" + entry.getValue();
                if (!tagMap.containsKey(key)) {
                    tagMap.put(key, new ArrayList<>());
                }
                tagMap.get(key).add(tag);
                if (!tagMap2.containsKey(tag)) {
                    tagMap2.put(tag, new ArrayList<>());
                }
                tagMap2.get(tag).add(key);
            }
        }
        tagMap2.forEach((tag, keys) -> {
            String randomClass = Internal.generateRandomString(20);
            tag.addClassName(randomClass);
            for (String key : keys) {
                String attribute = key.substring(0, key.indexOf(":"));
                String value = key.substring(key.indexOf(":") + 1);
                css.addClass(randomClass, attribute, value);
            }
        });
        css.addClass("hidden", "display", "none");
//        List<Map.Entry<String, List<Tag>>> entryList = new ArrayList<>(tagMap.entrySet());
//        entryList.sort((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()));
//        for (Map.Entry<String, List<Tag>> entry : entryList) {
//            String randomClass = Internal.generateRandomString(20);
//            String key = entry.getKey();
//            String value = key.substring(0, key.indexOf(":"));
//            css.addClass(randomClass, value, key.substring(key.indexOf(":") + 1));
//            for (Tag tag : entry.getValue()) {
//                tag.addAttribute(GlobalAttributes.CLASS, randomClass);
//            }
//        }
        return css.build();
    }

    public Tag root() {
        return root;
    }

    public String getPath() {
        return path;
    }

    class ScriptTag extends Tag {
        public ScriptTag(String append) {
            super("script", true, true);
            String script = FileUtils.readFileToString(new File("src/main/resources/script.js"));
            script = script.replace("your-server-url", ip).replace("your-server-port", String.valueOf(clientPort));
            script = script + append;
            plainText(script);
        }
    }
}
