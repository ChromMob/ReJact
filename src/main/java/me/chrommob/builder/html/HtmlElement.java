package me.chrommob.builder.html;

import java.util.Objects;

import me.chrommob.builder.utils.Internal;

public final class HtmlElement {
    private final String sourceId;
    private final String id;
    private final String type;
    private final String innerHtml;
    private final String outerHtml;
    private final String eventValue;
    private final String value;

    public HtmlElement(String sourceId, String id, String type, String innerHtml, String outerHtml, String eventValue, String value) {
        this.sourceId = sourceId;
        this.id = id;
        this.type = type;
        this.innerHtml = innerHtml;
        this.outerHtml = outerHtml;
        this.eventValue = eventValue;
        this.value = value;
    }

    public String sourceId() {
        return sourceId;
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    public String innerHtml() {
        return innerHtml;
    }

    public String outerHtml() {
        return outerHtml;
    }

    private String sanitisedEventValue;
    public String eventValue() {
        if (sanitisedEventValue == null) {
            sanitisedEventValue = Internal.sanitise(eventValue);
        }
        return sanitisedEventValue;
    }

    public String rawEventValue() {
        return eventValue;
    }


    public String rawValue() {
        return value;
    }

    private String sanitisedValue;
    public String value() {
        if (sanitisedValue == null) {
            sanitisedValue = Internal.sanitise(value);
        }
        return sanitisedValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (HtmlElement) obj;
        return Objects.equals(this.sourceId, that.sourceId) &&
                Objects.equals(this.id, that.id) &&
                Objects.equals(this.type, that.type) &&
                Objects.equals(this.innerHtml, that.innerHtml) &&
                Objects.equals(this.outerHtml, that.outerHtml) &&
                Objects.equals(this.eventValue, that.eventValue) &&
                Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceId, id, type, innerHtml, outerHtml, eventValue, value);
    }

    @Override
    public String toString() {
        return "HtmlElement[" +
                "sourceId=" + sourceId + ", " +
                "id=" + id + ", " +
                "type=" + type + ", " +
                "innerHtml=" + innerHtml + ", " +
                "outerHtml=" + outerHtml + ", " +
                "eventValue=" + eventValue + ", " +
                "value=" + value + ']';
    }

}
