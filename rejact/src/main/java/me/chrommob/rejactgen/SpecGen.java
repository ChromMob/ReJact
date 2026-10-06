package me.chrommob.rejactgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.chrommob.rejact.Json;

/**
 * Reads spec/elements.json (the single source of truth) and generates:
 * <ul>
 * <li>typed tag classes with typed attribute methods (gen.tags)</li>
 * <li>typed event payload records (gen.events)</li>
 * <li>the typed event registration API and global attribute API (gen.Eventful, gen.GlobalAttrs)</li>
 * <li>the JavaScript event payload mappings the fixed client runtime uses (rejact-bindings.js)</li>
 * </ul>
 * Nothing under gen/ is hand-written; edit the spec instead.
 */
public final class SpecGen {
    private static final String TAG_PACKAGE = "me.chrommob.rejact.gen.tags";
    private static final String EVENT_PACKAGE = "me.chrommob.rejact.gen.events";
    private static final String GEN_PACKAGE = "me.chrommob.rejact.gen";

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: SpecGen <spec/elements.json> <output dir>");
        }
        Path specPath = Path.of(args[0]);
        Path outDir = Path.of(args[1]);
        Map<String, Object> spec = Json.obj(Json.parse(Files.readString(specPath)));

        Path javaRoot = outDir.resolve("java");
        Path resRoot = outDir.resolve("resources");
        clean(javaRoot);
        Files.createDirectories(resRoot);

        List<Object> events = asList(spec.get("events"));
        List<Object> elements = asList(spec.get("elements"));
        List<Object> globals = asList(spec.get("globalAttributes"));

        writeEventRecords(javaRoot, events);
        writeEventful(javaRoot, events);
        writeEventCodes(javaRoot, events);
        writeGlobalAttrs(javaRoot, globals);
        writeTags(javaRoot, elements);
        writeBindings(resRoot, events);

        System.out.println("generated " + elements.size() + " tags, " + events.size() + " events -> " + outDir);
    }

    // ---------- event payload records ----------

    private static void writeEventRecords(Path javaRoot, List<Object> events) throws IOException {
        for (Object o : events) {
            Map<String, Object> event = Json.obj(o);
            String cls = Json.str(event, "class");
            StringBuilder sb = new StringBuilder(512);
            sb.append("package ").append(EVENT_PACKAGE).append(";\n\n");
            sb.append("import java.util.Map;\n\nimport me.chrommob.rejact.EventPayload;\nimport me.chrommob.rejact.Json;\n\n");
            sb.append("/** Generated from spec/elements.json by SpecGen. Do not edit. */\n");
            List<String> components = new ArrayList<>();
            for (Object f : asList(event.get("fields"))) {
                Map<String, Object> field = Json.obj(f);
                components.add(javaType(Json.str(field, "type")) + " " + Json.str(field, "name"));
            }
            sb.append("public record ").append(cls).append('(').append(String.join(", ", components))
                    .append(") implements EventPayload {\n");
            sb.append("    public static ").append(cls).append(" fromJson(Map<String, Object> m) {\n");
            sb.append("        return new ").append(cls).append('(');
            List<String> reads = new ArrayList<>();
            for (Object f : asList(event.get("fields"))) {
                Map<String, Object> field = Json.obj(f);
                reads.add(jsonRead(Json.str(field, "type"), Json.str(field, "name")));
            }
            sb.append(String.join(", ", reads)).append(");\n");
            sb.append("    }\n}\n");
            write(javaRoot, EVENT_PACKAGE, cls + ".java", sb.toString());
        }
    }

    private static void writeEventful(Path javaRoot, List<Object> events) throws IOException {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("package ").append(GEN_PACKAGE).append(";\n\n");
        sb.append("import java.util.Map;\nimport java.util.function.Function;\n\n");
        sb.append("import me.chrommob.rejact.EventPayload;\nimport me.chrommob.rejact.Handler;\n");
        for (Object o : events) {
            sb.append("import ").append(EVENT_PACKAGE).append('.').append(Json.str(Json.obj(o), "class")).append(";\n");
        }
        sb.append("\n/**\n * Generated from spec/elements.json by SpecGen. Do not edit.\n");
        sb.append(" * Typed event registration: the handler argument type and the payload decoder are\n");
        sb.append(" * generated together from the spec, so they cannot drift apart.\n */\n");
        sb.append("public interface Eventful<S extends Eventful<S>> {\n\n");
        sb.append("    S listen(String event, Function<Map<String, Object>, ? extends EventPayload> parser,\n");
        sb.append("            Handler<? extends EventPayload> handler, boolean preventDefault);\n\n");
        for (Object o : events) {
            Map<String, Object> event = Json.obj(o);
            String name = Json.str(event, "name");
            String cls = Json.str(event, "class");
            boolean prevent = Json.bool(event, "preventDefault");
            sb.append("    default S ").append(eventMethod(name)).append("(Handler<").append(cls).append("> handler) {\n");
            sb.append("        return listen(\"").append(name).append("\", ").append(cls).append("::fromJson, handler, ")
                    .append(prevent).append(");\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n");
        write(javaRoot, GEN_PACKAGE, "Eventful.java", sb.toString());
    }

    // ---------- event wire codes ----------

    /**
     * Event codes for the binary protocol: spec order, 1-based. The Java side maps code to name
     * for dispatch, the JS side maps name to code for sending — both from this one list.
     */
    private static void writeEventCodes(Path javaRoot, List<Object> events) throws IOException {
        StringBuilder sb = new StringBuilder(512);
        sb.append("package ").append(GEN_PACKAGE).append(";\n\n");
        sb.append("/** Generated from spec/elements.json by SpecGen. Do not edit. */\n");
        sb.append("public final class EventCodes {\n");
        sb.append("    private EventCodes() {\n    }\n\n");
        sb.append("    /** Index = wire code (spec order, 1-based). */\n");
        sb.append("    public static final String[] TO_NAME = {\"\"");
        for (Object o : events) {
            sb.append(", \"").append(Json.str(Json.obj(o), "name")).append('"');
        }
        sb.append("};\n\n");
        sb.append("    public static int code(String name) {\n");
        sb.append("        for (int i = 1; i < TO_NAME.length; i++) {\n");
        sb.append("            if (TO_NAME[i].equals(name)) {\n");
        sb.append("                return i;\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return 0;\n");
        sb.append("    }\n\n");
        sb.append("    public static String name(int code) {\n");
        sb.append("        return code > 0 && code < TO_NAME.length ? TO_NAME[code] : \"\";\n");
        sb.append("    }\n");
        sb.append("}\n");
        write(javaRoot, GEN_PACKAGE, "EventCodes.java", sb.toString());
    }

    // ---------- global attributes ----------

    private static void writeGlobalAttrs(Path javaRoot, List<Object> globals) throws IOException {
        StringBuilder sb = new StringBuilder(512);
        sb.append("package ").append(GEN_PACKAGE).append(";\n\n");
        sb.append("/** Generated from spec/elements.json by SpecGen. Do not edit. */\n");
        sb.append("public interface GlobalAttrs<S extends GlobalAttrs<S>> {\n\n");
        sb.append("    S attr(String name, String value);\n\n");
        StringBuilder enums = new StringBuilder();
        for (Object o : globals) {
            Map<String, Object> attr = Json.obj(o);
            emitAttribute(sb, enums, attr, "S", true);
        }
        sb.append(enums);
        sb.append("}\n");
        write(javaRoot, GEN_PACKAGE, "GlobalAttrs.java", sb.toString());
    }

    // ---------- tags ----------

    private static void writeTags(Path javaRoot, List<Object> elements) throws IOException {
        for (Object o : elements) {
            Map<String, Object> element = Json.obj(o);
            String cls = Json.str(element, "class");
            String tag = Json.str(element, "name");
            boolean isVoid = Json.bool(element, "void");
            StringBuilder sb = new StringBuilder(512);
            StringBuilder enums = new StringBuilder(256);
            sb.append("package ").append(TAG_PACKAGE).append(";\n\n");
            sb.append("import me.chrommob.rejact.Element;\n\n");
            sb.append("/** Generated from spec/elements.json by SpecGen. Do not edit. */\n");
            sb.append("public final class ").append(cls).append(" extends Element<").append(cls).append("> {\n\n");
            sb.append("    public ").append(cls).append("() {\n");
            sb.append("        super(\"").append(tag).append("\", ").append(isVoid).append(");\n");
            sb.append("    }\n\n");
            for (Object a : asList(element.get("attributes"))) {
                emitAttribute(sb, enums, Json.obj(a), cls, false);
            }
            sb.append(enums);
            sb.append("}\n");
            write(javaRoot, TAG_PACKAGE, cls + ".java", sb.toString());
        }
    }

    private static void emitAttribute(StringBuilder sb, StringBuilder enums, Map<String, Object> attr,
            String returnType, boolean isDefault) {
        String name = Json.str(attr, "name");
        String method = Json.str(attr, "method");
        String type = Json.str(attr, "type");
        List<String> values = new ArrayList<>();
        for (Object v : asList(attr.get("values"))) {
            values.add(String.valueOf(v));
        }
        String prefix = isDefault ? "    default " : "    public ";
        if (!values.isEmpty()) {
            String enumName = capitalize(method);
            // String overload first: HTML attributes accept any value; the enum is sugar.
            sb.append(prefix).append(returnType).append(' ').append(method).append("(String value) {\n");
            sb.append("        return attr(\"").append(name).append("\", value);\n    }\n\n");
            sb.append(prefix).append(returnType).append(' ').append(method).append('(').append(enumName)
                    .append(" value) {\n");
            sb.append("        return attr(\"").append(name).append("\", value.html);\n    }\n\n");
            enums.append("    public enum ").append(enumName).append(" {\n");
            List<String> constants = new ArrayList<>();
            for (String v : values) {
                constants.add("        " + enumConstant(v) + "(\"" + v + "\")");
            }
            enums.append(String.join(",\n", constants)).append(";\n\n");
            enums.append("        public final String html;\n\n");
            enums.append("        ").append(enumName).append("(String html) {\n            this.html = html;\n        }\n");
            enums.append("    }\n\n");
            return;
        }
        String javaType = javaType(type);
        String read = switch (type) {
            case "int" -> "Integer.toString(value)";
            case "boolean" -> "Boolean.toString(value)";
            case "element" -> "value.uid()";
            default -> "value";
        };
        String argType = type.equals("element") ? "Element<?>" : javaType;
        sb.append(prefix).append(returnType).append(' ').append(method).append('(').append(argType)
                .append(" value) {\n");
        sb.append("        return attr(\"").append(name).append("\", ").append(read).append(");\n    }\n\n");
    }

    // ---------- javascript bindings ----------

    private static void writeBindings(Path resRoot, List<Object> events) throws IOException {
        StringBuilder sb = new StringBuilder(512);
        // Data-driven pullers: one tiny table per event shape, shared by identical shapes.
        Map<String, String> vars = new LinkedHashMap<>();
        List<String> entries = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Object o : events) {
            Map<String, Object> event = Json.obj(o);
            String body = Json.str(event, "js");
            String var = vars.get(body);
            if (var == null) {
                var = "_" + vars.size();
                vars.put(body, var);
            }
            entries.add("  \"" + Json.str(event, "name") + "\":" + var);
            names.add(Json.str(event, "name"));
        }
        for (Map.Entry<String, String> binding : vars.entrySet()) {
            sb.append("var ").append(binding.getValue()).append('=').append(binding.getKey()).append(";\n");
        }
        sb.append("var RJ_PULL={\n");
        sb.append(String.join(",", entries)).append("\n};\n");
        // Compact name->code map: one string list, assigned on the client.
        sb.append("var RJ_CODES={},RJ_N=");
        sb.append(jsonStr(String.join(" ", names))).append(".split(' ');\n");
        sb.append("for(var RJ_I=0;RJ_I<RJ_N.length;RJ_I++)RJ_CODES[RJ_N[RJ_I]]=RJ_I+1;\n");
        Files.createDirectories(resRoot);
        Files.writeString(resRoot.resolve("rejact-bindings.js"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String jsonStr(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    // ---------- helpers ----------

    private static String javaType(String type) {
        return switch (type) {
            case "string" -> "String";
            case "int" -> "int";
            case "double" -> "double";
            case "boolean" -> "boolean";
            case "element" -> "Element<?>";
            default -> throw new IllegalArgumentException("unknown field type: " + type);
        };
    }

    private static String jsonRead(String type, String name) {
        return switch (type) {
            case "string" -> "Json.str(m, \"" + name + "\")";
            case "int" -> "Json.i(m, \"" + name + "\")";
            case "double" -> "((Number) m.getOrDefault(\"" + name + "\", 0d)).doubleValue()";
            case "boolean" -> "Json.bool(m, \"" + name + "\")";
            default -> throw new IllegalArgumentException("unknown field type: " + type);
        };
    }

    private static String eventMethod(String eventName) {
        return "on" + capitalize(eventName);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String enumConstant(String value) {
        String cleaned = value.replaceAll("^_+", "").replaceAll("[^A-Za-z0-9]+", "_").toUpperCase();
        if (cleaned.isEmpty()) {
            cleaned = "EMPTY";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "V_" + cleaned;
        }
        return cleaned;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    private static void write(Path javaRoot, String pkg, String name, String content) throws IOException {
        Path dir = javaRoot.resolve(pkg.replace('.', '/'));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static void clean(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }
}
