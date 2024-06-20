package me.chrommob.builder;

import in.wilsonl.minifyhtml.Configuration;
import me.chrommob.builder.css.CSSBuilder;
import me.chrommob.builder.html.FileProgress;
import me.chrommob.builder.html.FileUtils;
import me.chrommob.builder.html.HtmlElement;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;
import me.chrommob.builder.socket.Session;
import me.chrommob.builder.utils.Internal;

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
    private final Map<EventTypes, Set<Tag>> eventMap;
    private final Map<EventTypes, Set<Tag>> fileEventMap;
    private final Map<EventTypes, Set<Tag>> localEventMap = new HashMap<>();
    private final Map<EventTypes, Set<Tag>> localFileEventMap = new HashMap<>();

    public Page(String path, String ip, int clientPort, Map<EventTypes, Set<Tag>> eventMap,
            Map<EventTypes, Set<Tag>> fileEventMap) {
        this.path = path;
        this.ip = ip;
        this.clientPort = clientPort;
        this.eventMap = eventMap;
        this.fileEventMap = fileEventMap;
    }
    
    private final Tag root = new RootHtmlTag();
    private final CSSBuilder rootCssBuilder = new CSSBuilder();
    private CSSBuilder cssBuilder;

    private String cssString;
    private String htmlString;
    private boolean hasDynamicDataHandlers;

    public void build() {
        cssBuilder = rootCssBuilder.clone();

        localEventMap.clear();
        localFileEventMap.clear();

        List<Tag> tags = root.getAllChildren();

        hasDynamicDataHandlers = tags.stream().anyMatch(Tag::hasDynamicDataHandlers);
        tags.forEach(Tag::callDynamicDataHandlers);

        tags = root.getAllChildren();

        cssString = buildCss(tags);
        htmlString = buildHtml(tags);
    }

    public String getHtmlString() {
        if (htmlString == null || hasDynamicDataHandlers) {
            build();
        }
        return htmlString;
    }

    private String buildHtml(List<Tag> tags) {
        for (Tag tag : tags) {
            for (Map.Entry<EventTypes, BiConsumer<Session, HtmlElement>> entry : tag.getEvents().entrySet()) {
                Set<Tag> list = eventMap.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
                Set<Tag> localList = localEventMap.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
                list.add(tag);
                localList.add(tag);
            }
            for (Map.Entry<EventTypes, TriConsumer<Session, FileProgress, me.chrommob.builder.html.File>> entry : tag
                    .getFileEvents().entrySet()) {
                Set<Tag> list = fileEventMap.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
                Set<Tag> localList = localFileEventMap.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
                list.add(tag);
                localList.add(tag);
            }
        }
        root.removeChildByClass(ScriptTag.class);
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
        List<Tag> baseTags = headTag.getChildrenByClass(BaseTag.class);
        String path = this.path.endsWith("/") ? this.path : this.path + "/";
        if (baseTags.isEmpty()) {
            headTag.addChild(new BaseTag().addAttribute(HREF, path));
        } else {
            //Find if any has href attribute
            Tag baseTag = baseTags.stream().filter(tag -> tag.getAttributes().get(HREF) != null).findFirst()
                    .orElse(null);
            if (baseTag == null) {
                headTag.addChild(new BaseTag().addAttribute(HREF, path));
            }
        }
        htmlTag.removeChildByClass(StyleTag.class);
        htmlTag.addChild(new StyleTag().plainText(cssString));
        return root.build(false);
    }

    private String buildEventScript() {
        StringBuilder builder = new StringBuilder();
        for (EventTypes eventTypes : localEventMap.keySet()) {
            Set<Tag> list = localEventMap.get(eventTypes);
            List<String> ids = list.stream().map(Tag::id).toList();
            String idsString = ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
            builder.append("var ").append(eventTypes.name().toLowerCase()).append(" = [").append(idsString).append("];\n");
            builder.append(eventTypes.build());
        }
        for (EventTypes eventTypes : localFileEventMap.keySet()) {
            Set<Tag> list = localFileEventMap.get(eventTypes);
            List<String> ids = list.stream().map(Tag::id).toList();
            String idsString = ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
            builder.append("var ").append(eventTypes.name().toLowerCase()).append(" = [").append(idsString).append("];\n");
            builder.append(eventTypes.build());
        }
        return builder.toString();
    }

    private String buildCss(List<Tag> tags) {
        Set<String> classNames = new HashSet<>();
        cssBuilder.addClass("hidden", "display", "none");
        for (Tag tag : tags) {
            boolean ownClassName = false;
            String usedClassName;
            Set<String> classNames1 = tag.getClassNames();
            if (classNames1.isEmpty()) {
                usedClassName = Internal.generateRandomString(20);
            } else {
                Iterator<String> iterator = classNames1.iterator();
                String candidate = iterator.next();
                while ((classNames.contains(candidate) || cssBuilder.hasClass(candidate)) && iterator.hasNext()) {
                    candidate = iterator.next();
                }
                if (candidate == null || classNames.contains(candidate) || cssBuilder.hasClass(candidate)) {
                    usedClassName = Internal.generateRandomString(20);
                } else {
                    usedClassName = candidate;
                    ownClassName = true;
                }
            }
            classNames.add(usedClassName);
            if (!ownClassName) {
                tag.addClassName(usedClassName);
            }
            for (Map.Entry<String, Map<String, String>> entry : tag.getCssAttributes().entrySet()) {
                String selector = entry.getKey();
                if (selector.equals("default")) {
                    selector = null;
                }
                Map<String, String> css = entry.getValue();
                cssBuilder.addClass(usedClassName + (selector == null ? "" : selector), css);
            }
        }

        return cssBuilder.build();
    }

    public Tag root() {
        return root;
    }

    public CSSBuilder cssBuilder() {
        return rootCssBuilder;
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
