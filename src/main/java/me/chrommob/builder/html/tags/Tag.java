package me.chrommob.builder.html.tags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.lang3.function.TriConsumer;

import me.chrommob.builder.html.File;
import me.chrommob.builder.html.FileProgress;
import me.chrommob.builder.html.HtmlElement;
import me.chrommob.builder.html.constants.GlobalAttributes;
import static me.chrommob.builder.html.constants.GlobalAttributes.HREF;
import static me.chrommob.builder.html.constants.GlobalAttributes.ID;
import me.chrommob.builder.html.constants.Internal;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.socket.Session;

public class Tag {
    private final Set<Consumer<Void>> dynamicDataHandlers = new HashSet<>();
    private final Map<String, Map<String, String>> cssAttributes = new HashMap<>();
    private final String elementName;
    private final boolean newLineStart;
    private final boolean newLineEnd;
    private final List<Tag> children = new ArrayList<>();
    private final List<Tag> dynamicChildren = new ArrayList<>();
    private final Map<GlobalAttributes, String> attributes = new HashMap<>();
    private String plainText;
    private final Map<EventTypes, BiConsumer<Session, HtmlElement>> events = new HashMap<>();
    private final Map<EventTypes, TriConsumer<Session, FileProgress, File>> fileEvents = new HashMap<>();

    public boolean hasDynamicDataHandlers() {
        return !dynamicDataHandlers.isEmpty();
    }

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

    public void callDynamicDataHandlers() {
        dynamicChildren.clear();
        dynamicDataHandlers.forEach(handler -> handler.accept(null));
    }

    public Tag addDynamicChild(Tag child) {
        dynamicChildren.add(child);
        return this;
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
        List<Tag> allChildren = new ArrayList<>();
        allChildren.addAll(children);
        allChildren.addAll(dynamicChildren);
        return allChildren;
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
        List<Tag> allChildren = new ArrayList<>(getChildren());
        for (Tag child : getChildren()) {
            allChildren.addAll(child.getAllChildren());
        }
        return allChildren;
    }

    public Map<GlobalAttributes, String> getAttributes() {
        return new HashMap<>(attributes);
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

    public String plainText() {
        return plainText;
    }

    public Map<String, Map<String, String>> getCssAttributes() {
        return new HashMap<>(cssAttributes);
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
        return new HashMap<>(events);
    }

    public Map<EventTypes, TriConsumer<Session, FileProgress, File>> getFileEvents() {
        return new HashMap<>(fileEvents);
    }

    public String id() {
        String id = attributes.get(ID);
        if (id == null) {
            id = Internal.generateRandomString(20);
            addAttribute(ID, id);
        }
        return id;
    }

    @Override
    public Tag clone() {
        Tag tag = new Tag(elementName, newLineStart, newLineEnd);
        for (Consumer<Void> handler : getDynamicDataHandlers()) {
            tag.addDynamicDataHandler(handler);
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
        this.events.putAll(events);
    }

    private void setFileEvents(Map<EventTypes, TriConsumer<Session, FileProgress, File>> fileEvents) {
        this.fileEvents.putAll(fileEvents);
    }

    public Map<Tag, Map<EventTypes, BiConsumer<Session, HtmlElement>>> getAllEvents() {
        Map<Tag, Map<EventTypes, BiConsumer<Session, HtmlElement>>> allEvents = new HashMap<>();
        allEvents.put(this, getEvents());
        getAllChildren().forEach(tag -> allEvents.put(tag, tag.getEvents()));
        return allEvents;

    }

    public Map<Tag, Map<EventTypes, TriConsumer<Session, FileProgress, File>>> getAllFileEvents() {
        Map<Tag, Map<EventTypes, TriConsumer<Session, FileProgress, File>>> allFileEvents = new HashMap<>();
        allFileEvents.put(this, getFileEvents());
        getAllChildren().forEach(tag -> allFileEvents.put(tag, tag.getFileEvents()));
        return allFileEvents;
    }

    public Tag addDynamicDataHandler(Consumer<Void> handler) {
        dynamicDataHandlers.add(handler);
        return this;
    }

    private Set<Consumer<Void>> getDynamicDataHandlers() {
        return dynamicDataHandlers;
    }

    public void removeChildByClass(Class<? extends Tag> class1) {
        children.removeIf(class1::isInstance);
    }

    public Set<String> getClassNames() {
        Set<String> classNames = new HashSet<>();
        String attribute = attributes.get(GlobalAttributes.CLASS);
        if (attribute != null && !attribute.isEmpty()) {
            classNames.addAll(Arrays.asList(attribute.split(" ")));
        }
        return classNames;
    }
}
