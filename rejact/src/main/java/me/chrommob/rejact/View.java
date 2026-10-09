package me.chrommob.rejact;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One connected browser tab, held by its WebSocket connection. Carries the downstream op channel
 * and per-view state: cached input values (synced once at connect and kept fresh by change
 * traffic) and pending file reads.
 *
 * <p><b>Batching.</b> One user gesture usually mutates many elements. Writing a frame per
 * mutation makes the browser apply, and lay out, the gesture in dozens of steps - and costs a
 * syscall each. While a batch is open every op queues instead; the close coalesces redundant
 * writes and emits one frame (or as few as the 255-op protocol limit allows). The browser then
 * sees the whole gesture as a single atomic update.
 */
public final class View {
    final String id;
    final Page page;
    final Map<String, String> cookies;
    final String sessionKey;
    final Server server;
    final Map<String, String> values = new ConcurrentHashMap<>();
    final Map<String, String> attrs = new ConcurrentHashMap<>();
    final Map<String, Ui.FileHandler> fileHandlers = new ConcurrentHashMap<>();
    private final OutputStream out;
    volatile boolean alive = true;
    /** Ops queued while a batch is open, in emission order. Guarded by {@code this}. */
    private final List<Ops.Op> pending = new java.util.ArrayList<>();
    /** Open-batch nesting depth; the outermost close is the one that flushes. */
    private int batchDepth;

    View(String id, Page page, Map<String, String> cookies, OutputStream out,
            String sessionKey, Server server) {
        this.id = id;
        this.page = page;
        this.cookies = cookies;
        this.out = out;
        this.sessionKey = sessionKey;
        this.server = server;
    }

    public void send(Ops.Op op) {
        synchronized (this) {
            if (batchDepth > 0) {
                if (alive) {
                    pending.add(op);
                }
                return;
            }
        }
        send(List.of(op));
    }

    void send(List<Ops.Op> ops) {
        if (!alive || ops.isEmpty()) {
            return;
        }
        // The protocol caps a batch at 255 ops; a gesture that exceeds that ships as
        // back-to-back frames rather than failing.
        try {
            for (int from = 0; from < ops.size(); from += Wire.MAX_BATCH) {
                Ws.write(out, Ws.OP_BINARY,
                        Wire.encode(ops.subList(from, Math.min(ops.size(), from + Wire.MAX_BATCH))));
            }
        } catch (IOException e) {
            close();
        }
    }

    /** Starts queueing ops instead of writing them. Nestable; pair with {@link #closeBatch}. */
    synchronized void openBatch() {
        batchDepth++;
    }

    /**
     * Ends a batch. The outermost close coalesces the queue and writes it as one frame. Called
     * from a finally block, so it must not throw: a write failure closes the view instead.
     */
    void closeBatch() {
        List<Ops.Op> flush;
        synchronized (this) {
            if (batchDepth == 0) {
                return;
            }
            if (--batchDepth > 0 || pending.isEmpty()) {
                return;
            }
            flush = coalesce(pending);
            pending.clear();
        }
        send(flush);
    }

    /**
     * Drops writes that a later op in the same batch makes invisible. Every rule below is a
     * strict no-op on the resulting DOM, which is what makes this safe to do behind the app's
     * back:
     *
     * <ul>
     *   <li>two {@code Set}s of the same path on the same element - only the last is observable;</li>
     *   <li>a full-content replace or a removal - earlier content writes to that same element
     *       cannot be seen, though its attributes survive the replace and are kept.</li>
     * </ul>
     *
     * <p>Ops are scanned back to front so "last wins" is a first-sighting test, and the result is
     * re-reversed to preserve emission order - order still matters, since a replace must land
     * before the attribute writes that style the subtree it created.
     */
    private static List<Ops.Op> coalesce(List<Ops.Op> ops) {
        java.util.Set<String> seenSet = new java.util.HashSet<>();
        java.util.Set<String> wiped = new java.util.HashSet<>();
        List<Ops.Op> out = new java.util.ArrayList<>(ops.size());
        for (int i = ops.size() - 1; i >= 0; i--) {
            Ops.Op op = ops.get(i);
            switch (op) {
                case Ops.Set set -> {
                    // "*" is a content write, so a later replace/remove subsumes it; a property
                    // or attribute write survives one.
                    if ("*".equals(set.path()) && wiped.contains(set.el())) {
                        continue;
                    }
                    if (!seenSet.add(set.el() + "\u0000" + set.path())) {
                        continue;
                    }
                }
                case Ops.Html html -> {
                    if (wiped.contains(html.el())) {
                        continue;
                    }
                    if (html.replace()) {
                        wiped.add(html.el());
                    }
                }
                case Ops.Del del -> wiped.add(del.el());
                case Ops.Bind bind -> {
                    if (wiped.contains(bind.el())) {
                        continue;
                    }
                }
                default -> {
                    // Call and Var are side effects, never redundant: always kept.
                }
            }
            out.add(op);
        }
        java.util.Collections.reverse(out);
        return out;
    }

    void sendPing() {
        if (!alive) {
            return;
        }
        try {
            Ws.write(out, Ws.OP_PING, new byte[0]);
        } catch (IOException e) {
            close();
        }
    }

    void close() {
        synchronized (page) {
            if (alive) {
                alive = false;
                fileHandlers.clear();
                page.detach(this);
                try { out.close(); } catch (IOException ignored) { }
            }
        }
    }

    public Page page() {
        return page;
    }

    public String id() {
        return id;
    }
}
