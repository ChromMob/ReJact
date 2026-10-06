package me.chrommob.rejact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable key-value state: nothing lives "only in memory". Every mutation is written through to
 * disk (temp file + atomic rename) before it returns, and every read answers from the same
 * file-backed map, so restarting the process is a non-event for app state.
 *
 * <p>Three namespaces, one file each:
 * <ul>
 *   <li>{@link #session(String)} - one visitor's private state (survives as long as their cookie),</li>
 *   <li>{@link #account(String)} - one signed-in user's state, shared across every device they use,</li>
 *   <li>{@link #app(String)} - state shared by every visitor (one store per app name).</li>
 * </ul>
 */
public final class Store {
    private final Path dir;
    private final Map<String, Kv> opened = new ConcurrentHashMap<>();

    public Store(Path dir) {
        this.dir = dir;
    }

    /** One visitor's private, permanent state. */
    public Kv session(String sessionId) {
        return kv("session-" + safe(sessionId));
    }

    /** One signed-in user's state: the same store from every device and every session. */
    public Kv account(String accountName) {
        return kv("account-" + safe(accountName));
    }

    /** State shared by every visitor. */
    public Kv app(String name) {
        return kv("app-" + safe(name));
    }

    private synchronized Kv kv(String base) {
        Kv existing = opened.get(base);
        if (existing != null) {
            return existing;
        }
        Kv fresh = new Kv(dir.resolve(base + ".json"));
        opened.put(base, fresh);
        return fresh;
    }

    private static String safe(String name) {
        String s = name == null ? "" : name.replaceAll("[^a-zA-Z0-9_-]", "");
        return s.isEmpty() ? "anon" : s;
    }

    /** A file-backed string map. Lists are newline-joined; numbers are stored as strings. */
    public static final class Kv {
        private final Path file;
        private final Map<String, String> map = new LinkedHashMap<>();

        Kv(Path file) {
            this.file = file;
            load();
        }

        private void load() {
            try {
                if (Files.exists(file)) {
                    for (Map.Entry<String, Object> e : Json.obj(Json.parse(Files.readString(file))).entrySet()) {
                        map.put(e.getKey(), String.valueOf(e.getValue()));
                    }
                }
            } catch (Exception e) {
                // A corrupt file must not take the app down: start empty, the next persist() heals it.
            }
        }

        public synchronized String get(String key, String def) {
            return map.getOrDefault(key, def);
        }

        public synchronized long getLong(String key, long def) {
            try {
                return Long.parseLong(map.getOrDefault(key, ""));
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /** A newline-joined list value ({@code setList}); missing keys read as an empty list. */
        public synchronized List<String> getList(String key) {
            String raw = map.get(key);
            return raw == null || raw.isEmpty()
                    ? new ArrayList<>()
                    : new ArrayList<>(Arrays.asList(raw.split("\n")));
        }

        /** A JSON-encoded value written with {@link #setJson(String, Object)}. */
        public Object getJson(String key) {
            String raw = get(key, "");
            return raw.isEmpty() ? new LinkedHashMap<String, Object>() : Json.parse(raw);
        }

        public synchronized Set<String> keys() {
            return new LinkedHashSet<>(map.keySet());
        }

        public synchronized Kv set(String key, String value) {
            map.put(key, value);
            persist();
            return this;
        }

        public synchronized Kv setLong(String key, long value) {
            return set(key, Long.toString(value));
        }

        public synchronized Kv setList(String key, List<String> values) {
            return set(key, String.join("\n", values));
        }

        public synchronized Kv setJson(String key, Object value) {
            return set(key, Json.write(value));
        }

        public synchronized Kv remove(String key) {
            if (map.remove(key) != null) {
                persist();
            }
            return this;
        }

        /** Copies every key of {@code other} into this store (e.g. session state adopting an account). */
        public synchronized void adopt(Kv other) {
            synchronized (other) {
                if (other.map.isEmpty()) {
                    return;
                }
                map.putAll(other.map);
                persist();
            }
        }

        /** Write-through: the mutation is on disk before this returns. */
        private void persist() {
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, Json.write(map), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new IllegalStateException("cannot persist " + file, e);
            }
        }
    }
}
