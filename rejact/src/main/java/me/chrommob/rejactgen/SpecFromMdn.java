package me.chrommob.rejactgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import me.chrommob.rejact.Json;

/**
 * Builds the ReJact element/event spec from the vendored MDN browser-compat-data
 * reference ({@code spec/mdn/}) plus a thin ReJact policy overlay
 * ({@code spec/overlay.json}). Tag and attribute existence comes from MDN;
 * Java names, types, enums, and event payload shapes come from the overlay.
 *
 * <p>Output is the SpecGen JSON schema. Edit the reference or the overlay —
 * never generated code.
 */
public final class SpecFromMdn {
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "var", "yield", "record",
            "sealed", "permits", "_");

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: SpecFromMdn <spec/mdn/html.json> <spec/overlay.json> <output/elements.json>");
        }
        Map<String, Object> html = Json.obj(Json.parse(Files.readString(Path.of(args[0]))));
        Map<String, Object> overlay = Json.obj(Json.parse(Files.readString(Path.of(args[1]))));
        Path out = Path.of(args[2]);
        Map<String, Object> spec = build(html, overlay);
        Files.createDirectories(out.getParent());
        Files.writeString(out, writeValue(spec, 0) + "\n", StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        List<Object> elements = (List<Object>) spec.get("elements");
        @SuppressWarnings("unchecked")
        List<Object> events = (List<Object>) spec.get("events");
        System.out.println("spec from MDN: " + elements.size() + " tags, " + events.size()
                + " events -> " + out);
    }

    // ---------- build ----------

    static Map<String, Object> build(Map<String, Object> html, Map<String, Object> overlay) {
        Set<String> skip = stringSet(overlay.get("skipAttrs"));
        Map<String, String> methodNames = stringMap(overlay.get("methodNames"));
        Set<String> booleanAttrs = stringSet(overlay.get("booleanAttrs"));
        Set<String> intAttrs = stringSet(overlay.get("intAttrs"));
        Set<String> elementAttrs = stringSet(overlay.get("elementAttrs"));
        Map<String, String> types = stringMap(overlay.get("types"));
        Map<String, String> classNames = stringMap(overlay.get("classNames"));
        Set<String> voidTags = stringSet(overlay.get("voidTags"));
        @SuppressWarnings("unchecked")
        Map<String, Object> enums = overlay.get("enums") instanceof Map
                ? (Map<String, Object>) overlay.get("enums")
                : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> forceAttrs = overlay.get("forceAttrs") instanceof Map
                ? (Map<String, Object>) overlay.get("forceAttrs")
                : Map.of();

        @SuppressWarnings("unchecked")
        Map<String, Object> bcdElements = (Map<String, Object>) html.get("elements");

        List<Object> elements = new ArrayList<>();
        for (Map.Entry<String, Object> entry : new java.util.TreeMap<>(bcdElements).entrySet()) {
            String tag = entry.getKey();
            if (tag.startsWith("__")) {
                continue;
            }
            Map<String, Object> body = Json.obj(entry.getValue());
            Map<String, Object> element = new LinkedHashMap<>();
            element.put("name", tag);
            element.put("class", classNames.getOrDefault(tag, className(tag)));
            element.put("category", category(tag));
            if (voidTags.contains(tag)) {
                element.put("void", true);
            }
            List<Object> attrs = collectAttrs(tag, body, skip, methodNames, booleanAttrs, intAttrs,
                    elementAttrs, types, enums);
            mergeForced(attrs, tag, forceAttrs, enums);
            if (!attrs.isEmpty()) {
                element.put("attributes", attrs);
            }
            elements.add(element);
        }

        List<Object> globals = new ArrayList<>();
        for (Object o : asList(overlay.get("globalAttributes"))) {
            Map<String, Object> g = new LinkedHashMap<>(Json.obj(o));
            applyEnum(g, "global", g.get("name") == null ? "" : String.valueOf(g.get("name")), enums);
            globals.add(g);
        }

        List<Object> events = new ArrayList<>();
        for (Object o : asList(overlay.get("events"))) {
            Map<String, Object> e = Json.obj(o);
            Map<String, Object> event = new LinkedHashMap<>();
            String name = Json.str(e, "name");
            event.put("name", name);
            event.put("class", Json.str(e, "class"));
            event.put("preventDefault", Json.bool(e, "preventDefault"));
            List<Object> fields = new ArrayList<>();
            for (Object f : asList(e.get("fields"))) {
                List<?> pair = (List<?>) f;
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", String.valueOf(pair.get(0)));
                String type = String.valueOf(pair.get(1));
                field.put("type", type);
                // Optional 3rd slot: "target" reads from event.target (form value/checked).
                if (pair.size() > 2) {
                    field.put("source", String.valueOf(pair.get(2)));
                }
                fields.add(field);
            }
            event.put("fields", fields);
            event.put("js", jsExtractor(fields));
            events.add(event);
        }

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("comment",
                "Generated by SpecFromMdn from spec/mdn (MDN browser-compat-data) + spec/overlay.json. Do not edit.");
        spec.put("globalAttributes", globals);
        spec.put("elements", elements);
        spec.put("events", events);
        return spec;
    }

    private static List<Object> collectAttrs(String tag, Map<String, Object> body, Set<String> skip,
            Map<String, String> methodNames, Set<String> booleanAttrs, Set<String> intAttrs,
            Set<String> elementAttrs, Map<String, String> types, Map<String, Object> enums) {
        List<Object> attrs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String key : new TreeSet<>(body.keySet())) {
            if (key.equals("__compat") || key.startsWith("__") || skip.contains(key)) {
                continue;
            }
            // BCD feature groups are not real attributes.
            if (key.startsWith("type_") || key.endsWith("_computed_from_attributes")) {
                continue;
            }
            if (!seen.add(key)) {
                continue;
            }
            Map<String, Object> attr = new LinkedHashMap<>();
            attr.put("name", key);
            attr.put("method", methodNames.getOrDefault(key, methodName(key)));
            attr.put("type", attrType(key, types, booleanAttrs, intAttrs, elementAttrs));
            applyEnum(attr, tag, key, enums);
            attrs.add(attr);
        }
        return attrs;
    }

    private static void mergeForced(List<Object> attrs, String tag, Map<String, Object> forceAttrs,
            Map<String, Object> enums) {
        Object forced = forceAttrs.get(tag);
        if (forced == null) {
            return;
        }
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < attrs.size(); i++) {
            index.put(Json.str(Json.obj(attrs.get(i)), "name"), i);
        }
        for (Object o : asList(forced)) {
            Map<String, Object> src = Json.obj(o);
            Map<String, Object> attr = new LinkedHashMap<>(src);
            applyEnum(attr, tag, Json.str(src, "name"), enums);
            Integer at = index.get(Json.str(src, "name"));
            if (at != null) {
                attrs.set(at, attr);
            } else {
                index.put(Json.str(src, "name"), attrs.size());
                attrs.add(attr);
            }
        }
    }

    private static void applyEnum(Map<String, Object> attr, String tag, String name, Map<String, Object> enums) {
        Object values = enums.get(tag + "." + name);
        if (values == null) {
            values = enums.get(name);
        }
        if (values instanceof List<?> list && !list.isEmpty()) {
            List<Object> copy = new ArrayList<>();
            for (Object v : list) {
                copy.add(String.valueOf(v));
            }
            attr.put("values", copy);
            attr.putIfAbsent("type", "string");
        }
    }

    private static String attrType(String key, Map<String, String> types, Set<String> booleanAttrs,
            Set<String> intAttrs, Set<String> elementAttrs) {
        if (elementAttrs.contains(key)) {
            return "element";
        }
        if (types.containsKey(key)) {
            return types.get(key);
        }
        if (booleanAttrs.contains(key)) {
            return "boolean";
        }
        if (intAttrs.contains(key) && !key.equals("value") && !key.equals("min") && !key.equals("max")) {
            return "int";
        }
        return "string";
    }

    /** Compact "field,kind,…" table consumed by the generic puller in rejact-core.js. */
    private static String jsExtractor(List<Object> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            Map<String, Object> f = Json.obj(fields.get(i));
            if (i > 0) {
                sb.append(',');
            }
            String name = Json.str(f, "name");
            // "@" prefix = read from event.target (form controls), payload key stays bare.
            if ("target".equals(String.valueOf(f.getOrDefault("source", "")))) {
                sb.append('@');
            }
            sb.append(name).append(',').append(fieldKind(name, Json.str(f, "type")));
        }
        return sb.toString();
    }

    private static int kind(String type) {
        return switch (type) {
            case "int" -> 1;
            case "boolean" -> 2;
            case "double" -> 4;
            default -> 0;
        };
    }

    /**
     * Field kinds for the client puller:
     * 0 = raw string/value, 1 = int, 2 = boolean, 3 = collection length (touches),
     * 4 = double. Collection fields use kind 3 so the runtime never walks a TouchList.
     */
    static int fieldKind(String name, String type) {
        if (name.equals("touches") || name.equals("changedTouches") || name.equals("targetTouches")) {
            return 3;
        }
        return kind(type);
    }

    private static String methodName(String attr) {
        if (attr.contains("-")) {
            String[] parts = attr.split("-");
            StringBuilder sb = new StringBuilder(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
                }
            }
            return sanitize(sb.toString());
        }
        return sanitize(attr);
    }

    private static String sanitize(String name) {
        if (name.isEmpty()) {
            return "attr";
        }
        String cleaned = name.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isEmpty()) {
            return "attr";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "a" + cleaned;
        }
        if (JAVA_KEYWORDS.contains(cleaned)) {
            return cleaned + "Attr";
        }
        return cleaned;
    }

    private static String className(String tag) {
        StringBuilder sb = new StringBuilder();
        for (String part : tag.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        String name = sb.toString();
        if (name.isEmpty()) {
            name = "Tag";
        }
        if (Character.isDigit(name.charAt(0))) {
            name = "T" + name;
        }
        return name;
    }

    private static String category(String tag) {
        return switch (tag) {
            case "html", "head", "body", "title", "style", "meta", "link", "base" -> "document";
            case "div", "nav", "header", "footer", "section", "article", "aside",
                 "main", "dialog", "details", "summary", "figure", "figcaption", "search",
                 "hgroup", "address", "slot", "template" -> "container";
            case "p", "span", "h1", "h2", "h3", "h4", "h5", "h6", "a", "strong", "em", "pre",
                 "code", "br", "hr", "blockquote", "q", "cite", "abbr", "bdi", "bdo", "data",
                 "dfn", "i", "mark", "rb", "rp", "rt", "rtc", "ruby", "s", "samp", "small",
                 "sub", "sup", "time", "u", "wbr", "ins", "del", "b" -> "text";
            case "img", "picture", "source", "video", "audio", "track", "map", "area", "iframe",
                 "embed", "object", "canvas", "portal" -> "media";
            case "ul", "ol", "li", "menu", "dl", "dt", "dd" -> "list";
            case "form", "label", "button", "input", "textarea", "select", "option",
                 "optgroup", "fieldset", "legend", "output", "progress", "meter", "datalist" -> "form";
            case "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "col",
                 "colgroup" -> "table";
            default -> "other";
        };
    }

    // ---------- tiny JSON writer ----------

    private static String writeValue(Object v, int indent) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String s) {
            return jsonStr(s);
        }
        if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Double) {
            return String.valueOf(v);
        }
        if (v instanceof List<?> list) {
            if (list.isEmpty()) {
                return "[]";
            }
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(pad(indent + 1)).append(writeValue(list.get(i), indent + 1));
                sb.append(i + 1 < list.size() ? ",\n" : "\n");
            }
            sb.append(pad(indent)).append(']');
            return sb.toString();
        }
        if (v instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sb.append(pad(indent + 1)).append(jsonStr(String.valueOf(e.getKey()))).append(": ");
                sb.append(writeValue(e.getValue(), indent + 1));
                sb.append(++i < map.size() ? ",\n" : "\n");
            }
            sb.append(pad(indent)).append('}');
            return sb.toString();
        }
        return jsonStr(String.valueOf(v));
    }

    private static String pad(int n) {
        return "  ".repeat(n);
    }

    private static String jsonStr(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    private static Set<String> stringSet(Object o) {
        Set<String> out = new LinkedHashSet<>();
        for (Object v : asList(o)) {
            out.add(String.valueOf(v));
        }
        return out;
    }

    private static Map<String, String> stringMap(Object o) {
        Map<String, String> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        return out;
    }
}
