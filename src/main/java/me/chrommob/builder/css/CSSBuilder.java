package me.chrommob.builder.css;

import java.util.HashMap;
import java.util.Map;

public class CSSBuilder {
    private final Map<String, Map<String, String>> styles = new HashMap<>();

    public CSSBuilder addClass(String className, String property, String value) {
        className = "." + className;
        Map<String, String> style = styles.computeIfAbsent(className, k -> new HashMap<>());
        style.put(property, value);
        return this;
    }

    public CSSBuilder addClass(String className, Map<String, String> style) {
        className = "." + className;
        styles.put(className, style);
        return this;
    }

    public String build() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> entry : styles.entrySet()) {
            builder.append(entry.getKey());
            builder.append(" {").append(System.lineSeparator());
            for (Map.Entry<String, String> style : entry.getValue().entrySet()) {
                builder.append(" ").append(style.getKey()).append(": ").append(style.getValue()).append(";");
                builder.append(System.lineSeparator());
            }
            builder.append("}");
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    public boolean hasClass(String className) {
        return styles.containsKey(className);
    }

    public CSSBuilder clone() {
        CSSBuilder builder = new CSSBuilder();
        for (Map.Entry<String, Map<String, String>> entry : styles.entrySet()) {
            builder.addClass(entry.getKey(), entry.getValue());
        }
        return builder;
    }
}
