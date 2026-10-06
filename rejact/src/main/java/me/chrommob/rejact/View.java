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
        send(List.of(op));
    }

    void send(List<Ops.Op> ops) {
        if (!alive || ops.isEmpty()) {
            return;
        }
        try {
            Ws.write(out, Ws.OP_BINARY, Wire.encode(ops));
        } catch (IOException e) {
            close();
        }
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
        if (alive) {
            alive = false;
            page.detach(this);
        }
    }

    public Page page() {
        return page;
    }

    public String id() {
        return id;
    }
}
