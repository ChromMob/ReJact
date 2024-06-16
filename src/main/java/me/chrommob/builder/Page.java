package me.chrommob.builder;

import in.wilsonl.minifyhtml.Configuration;
import me.chrommob.builder.css.CSSBuilder;
import me.chrommob.builder.html.FileProgress;
import me.chrommob.builder.html.FileUtils;
import me.chrommob.builder.html.HtmlElement;
import me.chrommob.builder.html.constants.Internal;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;
import me.chrommob.builder.socket.Session;
import org.apache.commons.lang3.function.TriConsumer;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static me.chrommob.builder.html.constants.GlobalAttributes.HREF;

public class Page {
    private final Configuration configuration = new Configuration.Builder().setMinifyJs(true).setMinifyCss(true).build();
    private final String path;
    private final String ip;
    private final int clientPort;
    private final Map<EventTypes, List<Tag>> eventMap;
    private final Map<EventTypes, List<Tag>> fileEventMap;
    private final Map<EventTypes, List<Tag>> localEventMap = new HashMap<>();
    private final Map<EventTypes, List<Tag>> localFileEventMap = new HashMap<>();
    public Page(String path, String ip, int clientPort, Map<EventTypes, List<Tag>> eventMap, Map<EventTypes, List<Tag>> fileEventMap) {
        this.path = path;
        this.ip = ip;
        this.clientPort = clientPort;
        this.eventMap = eventMap;
        this.fileEventMap = fileEventMap;
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
            for (Map.Entry<EventTypes, TriConsumer<Session, FileProgress, me.chrommob.builder.html.File>> entry : tag.getFileEvents().entrySet()) {
                List<Tag> list = fileEventMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
                List<Tag> localList = localFileEventMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
                list.add(tag);
                localList.add(tag);
            }
        }
        root.addChild(new ScriptTag(buildEventScript()));
        List<Tag> htmlTags = root.getChildrenByClass(HtmlTag.class);
        if (htmlTags.isEmpty()) {
            root.addChild(new HtmlTag());
        }
        Tag htmlTag = htmlTags.stream().findFirst().orElse(null);
        List<Tag> headTags = htmlTag.getChildrenByClass(HeadTag.class);
        Tag headTag;
        if (headTags.isEmpty()) {
            headTag = new HeadTag();
            htmlTag.addChild(headTag);
        } else {
            headTag = headTags.stream().findFirst().orElse(null);
        }
        headTag.addChild(new BaseTag().addAttribute(HREF, path.endsWith("/") ? path : path + "/"));
        htmlTag.addChild(new StyleTag().plainText(cssString));
        return root.build(false);
    }

    private String buildEventScript() {
        StringBuilder builder = new StringBuilder();
        for (EventTypes eventTypes : localEventMap.keySet()) {
            List<Tag> list = localEventMap.get(eventTypes);
            List<String> ids = list.stream().map(Tag::id).toList();
            String idsString = ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
            builder.append("var ").append(eventTypes.name().toLowerCase()).append(" = [").append(idsString).append("];\n");
            builder.append(eventTypes.build());
        }
        for (EventTypes eventTypes : localFileEventMap.keySet()) {
            List<Tag> list = localFileEventMap.get(eventTypes);
            List<String> ids = list.stream().map(Tag::id).toList();
            String idsString = ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
            builder.append("var ").append(eventTypes.name().toLowerCase()).append(" = [").append(idsString).append("];\n");
            builder.append(eventTypes.build());
        }
        return builder.toString();
    }

    private String buildCss() {
        List<Tag> tags = root.getAllChildren();
        for (Tag tag : tags) {
            String className = Internal.generateRandomString(20);
            tag.addClassName(className);
            for (Map.Entry<String, Map<String, String>> entry : tag.getCssAttributes().entrySet()) {
                String selector = entry.getKey();
                if (selector.equals("default")) {
                    selector = null;
                }
                Map<String, String> css = entry.getValue();
                this.css.addClass(className + (selector == null ? "" : selector), css);
            }
        }

        css.addClass("hidden", "display", "none");
        return css.build();
    }

    public Tag root() {
        return root;
    }

    public String getPath() {
        return path;
    }

    public Set<Tag> getChildByClass(Class<? extends Tag> clazz) {
        Set<Tag> set = new HashSet<>();
        root.getAllChildren().forEach(tag -> {
            if (clazz.isInstance(tag)) {
                set.add(tag);
            }
        });
        return set;
    }

    public class ScriptTag extends Tag {
        public ScriptTag(String append) {
            super("script", true, true);
            String script = FileUtils.readFileToString(new File("script.js"));
            script = script.replace("your-server-url", ip).replace("your-server-port", String.valueOf(clientPort));
            script = script + append;
            plainText(script);
        }
    }
}
