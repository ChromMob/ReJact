package me.chrommob.builder.html.tags;

import me.chrommob.builder.html.File;
import me.chrommob.builder.html.FileProgress;
import me.chrommob.builder.html.HtmlElement;
import me.chrommob.builder.html.constants.GlobalAttributes;
import me.chrommob.builder.html.constants.Internal;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.socket.Session;
import org.apache.commons.lang3.function.TriConsumer;

import java.util.*;
import java.util.function.BiConsumer;

import static me.chrommob.builder.html.constants.GlobalAttributes.HREF;
import static me.chrommob.builder.html.constants.GlobalAttributes.ID;

public abstract class Tag {
    private final Map<String, Map<String, String>> cssAttributes = new HashMap<>();
    private final String elementName;
    private final boolean newLineStart;
    private final boolean newLineEnd;
    private final List<Tag> children = new ArrayList<>();
    private final Map<GlobalAttributes, String> attributes = new HashMap<>();
    private String plainText;
    private final Map<EventTypes, BiConsumer<Session, HtmlElement>> events = new HashMap<>();
    private final Map<EventTypes, TriConsumer<Session, FileProgress, File>> fileEvents = new HashMap<>();

    public Tag(String elementName, boolean newLineStart, boolean newLineEnd) {
        this.elementName = elementName;
        this.newLineStart = newLineStart;
        this.newLineEnd = newLineEnd;
    }

    public Tag(String elementName) {
        this(elementName, false, true);
    }

    public Tag() {
        this(null, false, true);
    }

    public Tag addChild(Tag child) {
        children.add(child);
        return this;
    }

    public Tag addAttribute(GlobalAttributes attribute, String value) {
        if (attributes.containsKey(attribute)) {
            attributes.put(attribute, attributes.get(attribute) + " " + value);
        } else {
            attributes.put(attribute, value);
        }
        return this;
    }

    public List<String> classNames() {
        if (attributes.containsKey(GlobalAttributes.CLASS)) {
            String[] classNames = attributes.get(GlobalAttributes.CLASS).split(" ");
            return Arrays.asList(classNames);
        }
        return new ArrayList<>();
    }

    public Tag addClassName(String className) {
        if (className == null) {
            return this;
        }
        List<String> classNames = classNames();
        for (String name : classNames) {
            if (name.equals(className)) {
                return this;
            }
        }
        if (classNames.isEmpty()) {
            attributes.put(GlobalAttributes.CLASS, className);
        } else {
            attributes.put(GlobalAttributes.CLASS, attributes.get(GlobalAttributes.CLASS) + " " + className);
        }
        return this;
    }

    public void removeClassName(String className) {
        List<String> classNames = classNames();
        classNames.remove(className);
        StringBuilder builder = new StringBuilder();
        for (String name : classNames) {
            builder.append(name).append(" ");
        }
        attributes.put(GlobalAttributes.CLASS, builder.toString());
    }

    public Tag css(String selector, String property, String value) {
        Map<String, String> css = cssAttributes.computeIfAbsent(selector, k -> new HashMap<>());
        css.put(property, value);
        return this;
    }

    public Tag css(String attribute, String value) {
        css("default", attribute, value);
        return this;
    }

    public List<Tag> getChildren() {
        return children;
    }

    public List<Tag> getChildrenByClass(Class clazz) {
        List<Tag> list = new ArrayList<>();
        for (Tag child : getChildren()) {
            if (clazz.isInstance(child)) {
                list.add(child);
            }
        }
        return list;
    }

    public List<Tag> getAllChildren() {
        List<Tag> allChildren = new ArrayList<>();
        allChildren.addAll(getChildren());
        for (Tag child : getChildren()) {
            allChildren.addAll(child.getAllChildren());
        }
        return allChildren;
    }

    public Map<GlobalAttributes, String> getAttributes() {
        return attributes;
    }

    public String build(boolean noLineBreak) {
        StringBuilder builder = new StringBuilder();
        builder.append(renderFront(noLineBreak));
        if (plainText != null) {
            builder.append(plainText).append(getChildren().isEmpty() ? "" : (noLineBreak ? "" : System.lineSeparator()));
        }
        for (Tag child : getChildren()) {
            builder.append(child.build(noLineBreak));
        }
        builder.append(renderPost(noLineBreak));
        return builder.toString();
    }

    public String renderFront(boolean noLineBreak) {
        if (elementName == null) {
            return Internal.HEADER + (newLineStart ? noLineBreak ? "" : System.lineSeparator() : "");
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<").append(elementName);
        for (Map.Entry<GlobalAttributes, String> entry : getAttributes().entrySet()) {
            builder.append(" ");
            builder.append(entry.getKey().name().toLowerCase());
            builder.append("=\"");
            builder.append(entry.getValue());
            builder.append("\"");
        }
        builder.append(">");
        if (newLineStart && !noLineBreak) {
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    public String renderPost(boolean noLineBreak) {
        if (elementName == null) {
            return newLineEnd ? noLineBreak ? "" : System.lineSeparator() : "";
        }
        return "</" + elementName + ">" + (newLineEnd ? noLineBreak ? "" : System.lineSeparator() : "");
    }

    public Tag plainText(String plainText) {
        this.plainText = plainText;
        return this;
    }

    public Map<String, Map<String, String>> getCssAttributes() {
        return cssAttributes;
    }

    public Tag href(String href) {
        addAttribute(HREF, href);
        return this;
    }

    @Override
    public String toString() {
        return elementName;
    }

    public Tag event(EventTypes eventTypes, BiConsumer<Session, HtmlElement> htmlElementConsumer) {
        events.put(eventTypes, htmlElementConsumer);
        return this;
    }

    public Tag fileEvent(EventTypes eventTypes, TriConsumer<Session, FileProgress, File> htmlElementConsumer) {
        fileEvents.put(eventTypes, htmlElementConsumer);
        return this;
    }

    public Map<EventTypes, BiConsumer<Session, HtmlElement>> getEvents() {
        return events;
    }

    public Map<EventTypes, TriConsumer<Session, FileProgress, File>> getFileEvents() {
        return fileEvents;
    }

    public String id() {
        String id = attributes.get(ID);
        if (id == null) {
            id = Internal.generateRandomString(20);
            addAttribute(ID, id);
        }
        return id;
    }

    public Tag clone() {
        Class<? extends Tag> clazz = this.getClass();
        Tag tag = null;
        try {
            tag = clazz.getConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        tag.setAttributes(getAttributes());
        tag.setCssAttributes(getCssAttributes());
        for (Tag child : getChildren()) {
            tag.addChild(child.clone());
        }
        tag.setEvents(getEvents());
        tag.setFileEvents(getFileEvents());
        tag.plainText(plainText);
        return tag;
    }

    private void setCssAttributes(Map<String, Map<String, String>> cssAttributes) {
        cssAttributes.forEach(this.cssAttributes::put);
    }

    private void setAttributes(Map<GlobalAttributes, String> attributes) {
        attributes.forEach(this.attributes::put);
    }

    private void setEvents(Map<EventTypes, BiConsumer<Session, HtmlElement>> events) {
        events.forEach(this.events::put);
    }

    private void setFileEvents(Map<EventTypes, TriConsumer<Session, FileProgress, File>> fileEvents) {
        fileEvents.forEach(this.fileEvents::put);
    }
}
