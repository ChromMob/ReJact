package me.chrommob.rejact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class StoreTest {
    @TempDir Path dir;

    @Test void namespacesDoNotAlias() {
        Store store = new Store(dir);
        store.account("a.b").set("secret", "first");
        store.account("ab").set("secret", "second");
        assertEquals("first", new Store(dir).account("a.b").get("secret", ""));
        assertEquals("second", new Store(dir).account("ab").get("secret", ""));
        assertThrows(IllegalArgumentException.class, () -> store.app("../outside"));
    }

    @Test void corruptStateIsNotSilentlyOverwritten() throws Exception {
        Path file = dir.resolve("app-broken.json");
        Files.writeString(file, "{not json");
        assertThrows(IllegalStateException.class, () -> new Store(dir).app("broken"));
        assertEquals("{not json", Files.readString(file));
    }

    @Test void failedWriteDoesNotChangeMemory() throws Exception {
        Store.Kv kv = new Store(dir).app("state");
        kv.set("key", "before");
        Files.createDirectory(dir.resolve("app-state.json.tmp"));
        assertThrows(IllegalStateException.class, () -> kv.set("key", "after"));
        assertEquals("before", kv.get("key", ""));
        assertEquals("before", new Store(dir).app("state").get("key", ""));
    }

    @Test void concurrentAdoptionDoesNotDeadlock() throws Exception {
        Store store = new Store(dir);
        Store.Kv a = store.app("a").set("a", "1");
        Store.Kv b = store.app("b").set("b", "2");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { for (int i = 0; i < 50; i++) a.adopt(b); });
            var second = pool.submit(() -> { for (int i = 0; i < 50; i++) b.adopt(a); });
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
}
