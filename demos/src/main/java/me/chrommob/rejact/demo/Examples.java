package me.chrommob.rejact.demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.chrommob.rejact.Page;
import me.chrommob.rejact.Server;
import me.chrommob.rejact.Store;
import me.chrommob.rejact.Ui;
import me.chrommob.rejact.gen.tags.Body;
import me.chrommob.rejact.gen.tags.Button;
import me.chrommob.rejact.gen.tags.Div;
import me.chrommob.rejact.gen.tags.H2;
import me.chrommob.rejact.gen.tags.Image;
import me.chrommob.rejact.gen.tags.Input;
import me.chrommob.rejact.gen.tags.Paragraph;
import me.chrommob.rejact.Expr;
import me.chrommob.rejact.gen.tags.Span;

/**
 * The demo fleet. Each demo is a natural showcase of one thing the framework does best:
 * the pixel wall paints a single shared tree for everyone, the auction runs on the server's
 * clock with live bids, pulse streams live tallies, and the type race moves progress bars on
 * every screen per keystroke. Old demo URLs redirect to the gallery.
 */
public final class Examples {
    private Examples() {
    }

    /** Shared demo state lives in the durable store: a restart is a non-event for all of it. */
    private static Store.Kv STATE;

    private static void loadDemos() {
        java.util.Arrays.fill(WALL, "");
        String wall = STATE.get("wall", "");
        if (!wall.isEmpty()) {
            String[] parts = wall.split(",", -1);
            for (int i = 0; i < WALL.length && i < parts.length; i++) {
                WALL[i] = parts[i];
            }
        }
        loadAuction();
        loadVotes();
    }

    private static void saveWall() {
        STATE.set("wall", String.join(",", WALL));
    }

    private static void saveAuction() {
        STATE.set("auction", LOT.index + "\t" + LOT.endMs + "\t" + LOT.topBid + "\t"
                + String.valueOf(LOT.topKey) + "\t" + String.valueOf(LOT.topName) + "\t"
                + (LOT.sold ? 1 : 0));
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String[] row : BIDS) {
            lines.add(row[0] + "\t" + row[1]);
        }
        STATE.setList("auction-bids", lines);
    }

    private static void loadAuction() {
        String raw = STATE.get("auction", "");
        if (!raw.isEmpty()) {
            String[] p = raw.split("\t", 6);
            if (p.length == 6) {
                LOT.index = Integer.parseInt(p[0]);
                LOT.item = LOT.index >= 0 && LOT.index < ITEMS.length ? ITEMS[LOT.index] : ITEMS[0];
                LOT.endMs = Long.parseLong(p[1]);
                LOT.topBid = Integer.parseInt(p[2]);
                LOT.topKey = "null".equals(p[3]) ? null : p[3];
                LOT.topName = "null".equals(p[4]) ? null : p[4];
                LOT.sold = "1".equals(p[5]);
            }
        }
        for (String line : STATE.getList("auction-bids")) {
            String[] b = line.split("\t", 2);
            BIDS.add(new String[] { b[0], b.length > 1 ? b[1] : "" });
        }
    }

    private static void saveVotes() {
        STATE.set("votes", VOTES[0] + "," + VOTES[1] + "," + VOTES[2]);
    }

    private static void loadVotes() {
        String raw = STATE.get("votes", "");
        if (!raw.isEmpty()) {
            String[] p = raw.split(",");
            for (int i = 0; i < VOTES.length && i < p.length; i++) {
                VOTES[i] = Integer.parseInt(p[i].trim());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server(args.length > 0 ? Integer.parseInt(args[0]) : 8080);
        String hero = server.addBlob(readAsset("hero.jpg"), "image/jpeg");
        STATE = server.store.app("demos");
        loadDemos();

        Wire.init(server);
        server.registerPage("/", Wire::page);
        server.registerPage("/demos", gallery(hero));
        server.registerPage("/wall", wallPage());
        server.registerPage("/auction", Examples::auctionPage);
        server.registerPage("/pulse", Examples::pulsePage);
        server.registerPage("/race", Examples::racePage);
        for (String old : new String[] { "/chat", "/login", "/counter", "/clock", "/todo", "/notes", "/party" }) {
            server.registerPage(old, Examples::moved);
        }
        startAuctionClock();
        server.start();
    }

    // ---------- gallery ----------

    private static Page gallery(String heroUrl) {
        Page page = new Page("/");
        page.root().add(Theme.head("ReJact \u2014 live interfaces in pure Java"))
                .add(new Body()
                        .add(Theme.topbar(Theme.linkPill("your desk \u2192", "/")))
                        .add(Theme.shell(
                                new Image().src(heroUrl).cssClass("hero")
                                        .alt("A machine and a llama at a desk, painting a wall together"),
                                Theme.pageHead("ReJact", "Live interfaces, pure Java.",
                                        "Everything here is one small Java program talking to every open screen. "
                                                + "Open a second screen and watch the two stay in step \u2014 nothing reloads, nothing polls."),
                                new Div().cssClass("cards").add(
                                        Theme.gcard("/wall", "t1", "Pixel wall",
                                                "Paint a wall with strangers. Every stroke lands everywhere, instantly."),
                                        Theme.gcard("/auction", "t2", "Auction house",
                                                "A live auction. The clock never stops and bids land the moment they are made."),
                                        Theme.gcard("/pulse", "t3", "Pulse",
                                                "Ask the room a question and watch the answers arrive live."),
                                        Theme.gcard("/race", "t4", "Type race",
                                                "Race everyone on one line. Progress bars move as people type.")),
                                Theme.foot("Four demos, one server \u2014 whatever happens here happens for everyone at once."))));
        return page;
    }

    // ---------- pixel wall: one shared tree, painted live ----------

    private static final String[] PALETTE = { "#8b7cff", "#5eead4", "#f472b6", "#fbbf24", "#60a5fa", "#a3e635" };
    private static final int COLS = 28;
    private static final int ROWS = 14;
    private static final String[] WALL = new String[COLS * ROWS];

    private static String paintColor(Ui ui) {
        String cookie = ui.cookie("pcolor");
        for (String color : PALETTE) {
            if (color.equals(cookie)) {
                return color;
            }
        }
        return PALETTE[0];
    }

    private static Page wallPage() {
        Page page = new Page("/wall");
        Div grid = new Div().cssClass("grid");
        for (int i = 0; i < COLS * ROWS; i++) {
            final int idx = i;
            Div cell = new Div().cssClass("cell");
            if (WALL[idx] != null && !WALL[idx].isEmpty()) {
                cell.style("background", WALL[idx]);
            }
            cell.onClick((ui, e) -> {
                String color = paintColor(ui);
                WALL[idx] = color;
                saveWall();
                cell.style("background", color);
            });
            grid.add(cell);
        }
        Div swatches = new Div().cssClass("swatches");
        for (String color : PALETTE) {
            Div swatch = new Div().cssClass("swatch").style("background", color);
            swatch.onClick((ui, e) -> ui.setCookie("pcolor", color));
            swatches.add(swatch);
        }
        page.root().add(Theme.head("Pixel wall \u2014 ReJact"))
                .add(new Body()
                        .add(Theme.topbar(Theme.backPill()))
                        .add(Theme.shell(
                                Theme.pageHead("Pixel wall", "Paint the wall together.",
                                        "One shared canvas. Pick a colour, tap a cell \u2014 your stroke lands on every "
                                                + "open screen at the same instant."),
                                Theme.card(grid),
                                Theme.card(swatches, new Paragraph().cssClass("center")
                                        .text("Tap a colour, then paint. The wall belongs to everyone.")),
                                Theme.foot("There is no \u201cyour version\u201d of this page: one wall, one truth."))));
        return page;
    }

    // ---------- auction house: server clock, live bids, per-view status ----------

    private static final String[] ITEMS = {
            "A rare mechanical keyboard", "A canvas by an unknown genius", "The last slice of pizza" };

    private static final class Lot {
        int index = -1;
        String item = "";
        long endMs;
        int topBid;
        String topKey;
        String topName;
        boolean sold;
    }

    private static final Lot LOT = new Lot();
    private static final Object AUCTION_LOCK = new Object();
    private static long nextSyncMs;

    /** Remaining milliseconds, aged per connection so client clock skew cannot lie. */
    private static Expr rem() {
        return Expr.var("rem").minus(Expr.age("rem")).max(Expr.lit(0));
    }

    private static final Expr CLOCK = Expr.cat(
            rem().div(Expr.lit(60_000)),
            Expr.str(":"),
            rem().div(Expr.lit(1000)).mod(Expr.lit(60)).pad2(),
            Expr.str("."),
            rem().div(Expr.lit(100)).mod(Expr.lit(10)));

    private record AuctionView(String key, Paragraph lot, H2 top, Paragraph countdown, Paragraph status,
            Div history) {
    }

    private static final CopyOnWriteArrayList<AuctionView> AUCTION_VIEWS = new CopyOnWriteArrayList<>();
    private static final List<String[]> BIDS = new CopyOnWriteArrayList<>();

    private static Page auctionPage(String session) {
        Page page = new Page("/auction");
        Paragraph lot = new Paragraph().cssClass("bigish");
        H2 top = new H2();
        Paragraph countdown = new Paragraph().cssClass("bigish");
        Paragraph status = new Paragraph().cssClass("center");
        Div history = new Div().cssClass("list");
        for (String[] row : BIDS) {
            history.add(new Div().cssClass("brow")
                    .add(new Span().text(row[0]))
                    .add(new Span().text(row[1])));
        }
        synchronized (AUCTION_LOCK) {
            if (LOT.index < 0) {
                nextLot();
            }
            lot.text(LOT.item);
            top.text(topLine());
            countdown.text(LOT.sold ? "SOLD" : "0:00.0");
            status.text(LOT.topBid > 0
                    ? "Top: " + LOT.topBid + " by " + LOT.topName + " \u2014 outbid it!"
                    : "Place the first bid.");
        }
        Input bid = new Input().cssClass("field").placeholder("your bid").attr("inputmode", "numeric");
        Button bidBtn = Theme.button("bid");
        Input name = new Input().cssClass("field").placeholder("your name");
        Button nameBtn = Theme.ghost("save name");
        AUCTION_VIEWS.add(new AuctionView(session, lot, top, countdown, status, history));

        bidBtn.onClick((ui, e) -> placeBid(ui, session, bid));
        bid.onKeydown((ui, e) -> {
            if ("Enter".equals(e.key())) {
                placeBid(ui, session, bid);
            }
        });
        nameBtn.onClick((ui, e) -> {
            String value = ui.valueOf(name).trim();
            if (!value.isEmpty()) {
                ui.setCookie("name", value);
                status.text("Saved. You bid as " + value + ".");
            }
        });

        // The whole ticking clock is app-level pure Java: bind the display to an expression and
        // sync the timestamp every so often - no framework opcodes, no per-tick traffic.
        page.onLoad(ui -> {
            synchronized (AUCTION_LOCK) {
                if (!LOT.sold) {
                    countdown.bindText(CLOCK);
                    ui.setVar("rem", LOT.endMs - System.currentTimeMillis());
                }
            }
        });

        page.root().add(Theme.head("Auction house \u2014 ReJact"))
                .add(new Body()
                        .add(Theme.topbar(Theme.backPill()))
                        .add(Theme.shell(
                                Theme.pageHead("Auction house", "Going once, going twice\u2026",
                                        "The countdown runs on the server's clock and every bid lands the moment it is "
                                                + "made. Outbid the room \u2014 or watch it happen."),
                                Theme.card(lot, top, countdown, status,
                                        new Div().cssClass("row").add(bid).add(bidBtn),
                                        new Div().cssClass("row").add(name).add(nameBtn)),
                                Theme.card(new H2().text("Bid history"), history),
                                Theme.foot("The hammer falls when the server says so."))));
        return page;
    }

    private static void placeBid(Ui ui, String session, Input bid) {
        int value;
        try {
            value = Integer.parseInt(ui.valueOf(bid).trim());
        } catch (NumberFormatException bad) {
            return;
        }
        String name = ui.cookie("name") == null ? "someone" : ui.cookie("name");
        synchronized (AUCTION_LOCK) {
            if (LOT.sold || value <= LOT.topBid) {
                for (AuctionView view : AUCTION_VIEWS) {
                    if (view.key().equals(session)) {
                        view.status().text("Too low \u2014 the top bid is " + LOT.topBid + ".");
                    }
                }
                return;
            }
            LOT.topBid = value;
            LOT.topKey = session;
            LOT.topName = name;
            BIDS.add(new String[] { name, String.valueOf(value) });
            saveAuction();
            bid.attr("value", "");
            for (AuctionView view : AUCTION_VIEWS) {
                view.top().text(topLine());
                view.status().text(view.key().equals(session)
                        ? "You're winning at " + value + "."
                        : "Outbid by " + name + " \u2014 " + value + ".");
                view.history().add(new Div().cssClass("brow")
                        .add(new Span().text(name))
                        .add(new Span().text(String.valueOf(value))));
            }
        }
    }

    private static String topLine() {
        return LOT.topBid > 0 ? "Top: " + LOT.topBid + " by " + LOT.topName : "No bids yet";
    }

    private static void nextLot() {
        LOT.index = (LOT.index + 1) % ITEMS.length;
        LOT.item = ITEMS[LOT.index];
        LOT.endMs = System.currentTimeMillis() + 60_000;
        LOT.topBid = 0;
        LOT.topKey = null;
        LOT.topName = null;
        LOT.sold = false;
        saveAuction();
    }

    private static void startAuctionClock() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auction-clock");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            synchronized (AUCTION_LOCK) {
                long now = System.currentTimeMillis();
                boolean justSold = !LOT.sold && now >= LOT.endMs;
                if (justSold) {
                    LOT.sold = true;
                    saveAuction();
                }
                for (AuctionView view : AUCTION_VIEWS) {
                    if (justSold) {
                        view.countdown().text("SOLD");
                        view.lot().text(LOT.item);
                        view.top().text(LOT.topBid > 0
                                ? "SOLD \u2014 " + LOT.topName + " won it for " + LOT.topBid
                                : "SOLD \u2014 nobody bid");
                        view.status().text(LOT.topBid > 0 && view.key().equals(LOT.topKey)
                                ? "It's yours. Enjoy."
                                : "The hammer is down. Next lot incoming\u2026");
                    }
                }
                if (LOT.sold && now >= LOT.endMs + 8000) {
                    nextLot();
                    for (AuctionView view : AUCTION_VIEWS) {
                        view.countdown().bindText(CLOCK);
                        view.countdown().setVar("rem", LOT.endMs - now);
                        view.lot().text(LOT.item);
                        view.top().text(topLine());
                        view.status().text("New lot. Go.");
                        view.history().clear();
                    }
                } else if (!LOT.sold && now >= nextSyncMs) {
                    // Timestamps, not ticks: clients count down on their own, so the wire only
                    // carries the occasional resync that keeps everyone's clock honest.
                    nextSyncMs = now + 10_000;
                    for (AuctionView view : AUCTION_VIEWS) {
                        view.countdown().setVar("rem", LOT.endMs - now);
                    }
                }
            }
        }, 200, 200, TimeUnit.MILLISECONDS);
    }

    // ---------- pulse: live tallies for everyone ----------

    private static final String[] OPTS = { "Tabs", "Spaces", "Chaos" };
    private static final int[] VOTES = new int[OPTS.length];

    private record PollView(Span[] counts, Div[] fills) {
    }

    private static final CopyOnWriteArrayList<PollView> POLL_VIEWS = new CopyOnWriteArrayList<>();

    private static Page pulsePage(String session) {
        Page page = new Page("/pulse");
        Span[] counts = new Span[OPTS.length];
        Div[] fills = new Div[OPTS.length];
        Span mine = new Span().cssClass("pill").text("no vote yet");
        int[] my = { -1 };
        Div rows = new Div().cssClass("list");
        for (int i = 0; i < OPTS.length; i++) {
            counts[i] = new Span().cssClass("pct").text("0");
            fills[i] = new Div().cssClass("fill");
            Button option = Theme.ghost(OPTS[i]);
            final int choice = i;
            option.onClick((ui, e) -> {
                synchronized (VOTES) {
                    if (my[0] == choice) {
                        return;
                    }
                    if (my[0] >= 0) {
                        VOTES[my[0]]--;
                    }
                    VOTES[choice]++;
                    my[0] = choice;
                }
                saveVotes();
                mine.text("You picked " + OPTS[choice] + ".");
                syncPoll();
            });
            rows.add(new Div().cssClass("rrow")
                    .add(option)
                    .add(new Div().cssClass("track").add(fills[i]))
                    .add(counts[i]));
        }
        POLL_VIEWS.add(new PollView(counts, fills));
        syncPoll();
        page.root().add(Theme.head("Pulse \u2014 ReJact"))
                .add(new Body()
                        .add(Theme.topbar(Theme.backPill()))
                        .add(Theme.shell(
                                Theme.pageHead("Pulse", "Tabs, spaces, or chaos?",
                                        "Vote and watch the room answer. The bars move the instant anyone, anywhere "
                                                + "picks \u2014 and you can change your mind."),
                                Theme.card(rows, new Div().cssClass("row").add(mine)),
                                Theme.foot("Every vote lands live \u2014 no refresh, no waiting."))));
        return page;
    }

    private static void syncPoll() {
        int total = 0;
        for (int votes : VOTES) {
            total += votes;
        }
        for (PollView view : POLL_VIEWS) {
            for (int i = 0; i < OPTS.length; i++) {
                view.counts()[i].text(String.valueOf(VOTES[i]));
                int pct = total == 0 ? 0 : (int) Math.round(100.0 * VOTES[i] / total);
                view.fills()[i].style("width", Math.max(pct, 2) + "%");
            }
        }
    }

    // ---------- type race: per-keystroke progress, live for the room ----------

    private static final String TARGET = "the quick brown fox jumps over the lazy dog";

    private record RacerRow(Span name, Div fill, Span pct) {
    }

    private static final class Racer {
        final List<RacerRow> rows = new CopyOnWriteArrayList<>();
        int pct;
    }

    private static final List<Racer> RACERS = new CopyOnWriteArrayList<>();
    private static final List<Div> BOARDS = new CopyOnWriteArrayList<>();

    private static Page racePage(String session) {
        Page page = new Page("/race");
        Racer me = new Racer();
        me.pct = (int) STATE.getLong("race:" + session, 0);
        Div board = new Div().cssClass("list");
        for (Racer racer : RACERS) {
            addRow(board, racer);
        }
        RACERS.add(me);
        BOARDS.add(board);
        addRow(board, me);
        for (Div other : BOARDS) {
            if (other != board) {
                addRow(other, me);
            }
        }
        String guest = "guest-" + session.substring(0, Math.min(4, session.length()));
        Paragraph note = new Paragraph().cssClass("center").text("Start typing to race.");
        Input typing = new Input().cssClass("field").placeholder("type here\u2026").attr("autocomplete", "off");
        int[] last = { -1 };
        typing.onInput((ui, e) -> {
            String typed = e.value();
            int done = 0;
            while (done < typed.length() && done < TARGET.length()
                    && Character.toLowerCase(typed.charAt(done)) == Character.toLowerCase(TARGET.charAt(done))) {
                done++;
            }
            int pct = (int) Math.round(100.0 * done / TARGET.length());
            if (pct == last[0]) {
                return;
            }
            last[0] = pct;
            me.pct = pct;
            STATE.setLong("race:" + session, pct);
            for (RacerRow row : me.rows) {
                row.fill().style("width", Math.max(pct, 2) + "%");
                row.pct().text(pct + "%");
            }
            note.text(pct >= 100 ? "Finished! Clear the field for another run." : "Go, go, go\u2026");
        });
        page.onLoad(ui -> {
            String name = ui.cookie("name");
            if (name != null && !name.isBlank()) {
                for (RacerRow row : me.rows) {
                    row.name().text(name);
                }
            }
        });
        page.root().add(Theme.head("Type race \u2014 ReJact"))
                .add(new Body()
                        .add(Theme.topbar(Theme.backPill()))
                        .add(Theme.shell(
                                Theme.pageHead("Type race", "Type the line, beat the room.",
                                        "Everyone gets the same sentence. Every keystroke moves your bar on every "
                                                + "screen \u2014 and theirs moves yours."),
                                Theme.card(new Div().cssClass("screen").add(new Span().text(TARGET)),
                                        typing, note),
                                Theme.card(new H2().text("The room"), board),
                                Theme.foot("Set your name in the auction house and it follows you here."))));
        return page;
    }

    private static void addRow(Div board, Racer racer) {
        Span name = new Span().cssClass("who").text("guest");
        Div fill = new Div().cssClass("fill");
        Span pct = new Span().cssClass("pct").text(racer.pct + "%");
        fill.style("width", Math.max(racer.pct, 2) + "%");
        racer.rows.add(new RacerRow(name, fill, pct));
        board.add(new Div().cssClass("rrow")
                .add(name)
                .add(new Div().cssClass("track").add(fill))
                .add(pct));
    }

    // ---------- old demo URLs ----------

    private static Page moved(String session) {
        Page page = new Page("/moved");
        page.onLoad(ui -> ui.redirect("/"));
        page.root().add(Theme.head("ReJact"))
                .add(new Body()
                        .add(Theme.topbar())
                        .add(Theme.shell(
                                Theme.pageHead("Moved", "This demo moved on.",
                                        "The old demo set is gone \u2014 the new ones are better. Back to the gallery."),
                                Theme.foot("Redirecting\u2026"))));
        return page;
    }

    private static byte[] readAsset(String name) throws IOException {
        try (InputStream in = Examples.class.getResourceAsStream("/" + name)) {
            if (in != null) {
                return in.readAllBytes();
            }
        }
        for (String candidate : new String[] { "assets/" + name, "v2/assets/" + name }) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                return Files.readAllBytes(path);
            }
        }
        throw new IOException("asset not found: " + name);
    }
}
