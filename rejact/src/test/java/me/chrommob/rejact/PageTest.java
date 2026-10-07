package me.chrommob.rejact;

import me.chrommob.rejact.gen.tags.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PageTest {
    @Test void escapesTextAndAttributes() {
        Div div = new Div().text("<script>evil</script>").attr("title", "\" onmouseover=\"evil");
        assertFalse(div.renderToString().contains("<script>"));
        assertTrue(div.renderToString().contains("&quot; onmouseover=&quot;evil"));
    }
    @Test void treeRejectsCyclesAndMultipleParentsBeforeMutation() {
        Div child = new Div();
        Div parent = new Div().add(child);
        assertThrows(IllegalArgumentException.class, () -> child.add(parent));
        assertThrows(IllegalArgumentException.class, () -> new Div().add(child));
        assertThrows(IllegalArgumentException.class, () -> parent.replaceChildren(child, child));
        parent.replaceChildren(child);
        Page page = new Page("/");
        page.root().add(parent);
        page.root().remove(parent);
        assertThrows(IllegalArgumentException.class, () -> child.add(parent));
        new Div().add(parent);
    }
    @Test void timersStopWhenLastViewDisconnectsAndResumeOnAttach() throws Exception {
        Page page = new Page("/");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1);
        page.every(Duration.ofMillis(10), ui -> { calls.incrementAndGet(); first.countDown(); });
        View view = new View("timer", page, new HashMap<>(), new ByteArrayOutputStream(), "session", null);
        page.attach(view);
        assertTrue(first.await(3, TimeUnit.SECONDS));
        view.close();
        int stopped = calls.get();
        Thread.sleep(60);
        assertEquals(stopped, calls.get());
        CountDownLatch resumed = new CountDownLatch(1);
        page.every(Duration.ofMillis(10), ui -> resumed.countDown());
        View next = new View("next", page, new HashMap<>(), new ByteArrayOutputStream(), "session", null);
        page.attach(next);
        assertTrue(resumed.await(3, TimeUnit.SECONDS));
        next.close();
    }
}
