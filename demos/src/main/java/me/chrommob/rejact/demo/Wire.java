package me.chrommob.rejact.demo;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import me.chrommob.rejact.Page;
import me.chrommob.rejact.Server;
import me.chrommob.rejact.Store;
import me.chrommob.rejact.Ui;
import me.chrommob.rejact.gen.tags.Anchor;
import me.chrommob.rejact.gen.tags.Body;
import me.chrommob.rejact.gen.tags.Button;
import me.chrommob.rejact.gen.tags.Div;
import me.chrommob.rejact.gen.tags.H2;
import me.chrommob.rejact.gen.tags.Input;
import me.chrommob.rejact.gen.tags.Paragraph;
import me.chrommob.rejact.gen.tags.Span;
import me.chrommob.rejact.gen.tags.Textarea;

/**
 * Wire: everything you follow in one live inbox. A real RSS/Atom reader for one person - feeds,
 * unread triage, stars, per-keystroke search, inline reading - where the architecture leans on
 * the framework instead of decorating it: the server polls feeds and new items arrive in open
 * tabs live (no client polling anywhere), every handler is plain Java, and subscriptions,
 * read and star state survive restarts on disk.
 */
public final class Wire {
    private Wire() {
    }

    private static final Path DATA = Path.of("data");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final int KEEP_PER_FEED = 80;

    private static final String[][] SEEDS = {
            { "Hacker News", "https://hnrss.org/frontpage" },
            { "BaritoneRemover releases", "https://github.com/ChromMob/BaritoneRemover/releases.atom" },
            { "arXiv cs.OS", "https://export.arxiv.org/rss/cs.OS" } };

    private static Server server;
    private static final Map<String, Person> PEOPLE = new ConcurrentHashMap<>();
    private static final Map<String, WireView> VIEWS = new ConcurrentHashMap<>();

    static void init(Server server) {
        Wire.server = server;
        startRefresher();
    }

    // ---------- model ----------

    static final class Entry {
        final String id;
        final String title;
        final String link;
        final String text;
        final long published;
        boolean read;
        boolean starred;

        Entry(String id, String title, String link, String text, long published) {
            this.id = id;
            this.title = title;
            this.link = link;
            this.text = text;
            this.published = published;
        }
    }

    static final class Feed {
        final String id;
        final String url;
        String title;
        String error = "";
        final List<Entry> entries = new ArrayList<>();

        Feed(String id, String url, String title) {
            this.id = id;
            this.url = url;
            this.title = title;
        }
    }

    static final class Person {
        final String key;
        String user;
        Store.Kv kv;
        final Map<String, Feed> feeds = new LinkedHashMap<>();
        final List<String> read = new CopyOnWriteArrayList<>();
        final List<String> starred = new CopyOnWriteArrayList<>();
        int seq;

        Person(String key, String user, Store.Kv kv) {
            this.key = key;
            this.user = user;
            this.kv = kv;
        }
    }

    // ---------- persistence: the durable store is the truth, memory is just its cache ----------
    // Keys: "feeds" (id\turl\ttitle lines), "read"/"starred" (entry-id lines), "e:<feedId>"
    // (entries cache, JSON array), "ui-*" (view state). Anonymous people live in the session
    // store, signed-in people in the account store shared by every device they sign in from.

    private static Person load(String key) {
        return PEOPLE.computeIfAbsent(key, k -> {
            String user = server.store.session(k).get("account", "");
            Store.Kv kv = user.isEmpty() ? server.store.session(k) : server.store.account(user);
            migrateLegacy(k, kv);
            Person person = new Person(k, user, kv);
            hydrate(person);
            if (person.feeds.isEmpty()) {
                for (String[] seed : SEEDS) {
                    person.seq++;
                    person.feeds.put("f" + person.seq, new Feed("f" + person.seq, seed[1], seed[0]));
                }
                save(person);
            }
            return person;
        });
    }

    /** Rebuilds the in-memory model from the store (startup, sign-in, sign-out). */
    private static void hydrate(Person person) {
        person.feeds.clear();
        person.read.clear();
        person.starred.clear();
        person.seq = 0;
        for (String line : person.kv.get("feeds", "").split("\n")) {
            String[] parts = line.split("\t", 3);
            if (parts.length == 3) {
                person.seq = Math.max(person.seq, safeSeq(parts[0]));
                Feed feed = new Feed(parts[0], parts[1], parts[2]);
                loadEntries(person, feed);
                person.feeds.put(parts[0], feed);
            }
        }
        person.read.addAll(person.kv.getList("read"));
        person.starred.addAll(person.kv.getList("starred"));
        for (Feed feed : person.feeds.values()) {
            for (Entry entry : feed.entries) {
                entry.read = person.read.contains(entry.id);
                entry.starred = person.starred.contains(entry.id);
            }
        }
    }

    private static int safeSeq(String feedId) {
        try {
            return Integer.parseInt(feedId.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void save(Person person) {
        StringBuilder feeds = new StringBuilder();
        for (Feed feed : person.feeds.values()) {
            if (feeds.length() > 0) {
                feeds.append('\n');
            }
            feeds.append(feed.id).append('\t').append(feed.url).append('\t').append(feed.title);
        }
        person.kv.set("feeds", feeds.toString());
        person.kv.setList("read", person.read);
        person.kv.setList("starred", person.starred);
    }

    /** The entries cache is durable too: restart the server and the inbox is already there. */
    private static void persistEntries(Person person, Feed feed) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Entry entry : feed.entries) {
            if (rows.size() >= KEEP_PER_FEED) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("i", entry.id);
            row.put("t", entry.title);
            row.put("l", entry.link);
            row.put("p", entry.published);
            row.put("x", entry.text);
            rows.add(row);
        }
        person.kv.setJson("e:" + feed.id, rows);
    }

    private static void loadEntries(Person person, Feed feed) {
        try {
            Object raw = person.kv.getJson("e:" + feed.id);
            if (!(raw instanceof List<?> list)) {
                return;
            }
            for (Object item : list) {
                Map<String, Object> row = me.chrommob.rejact.Json.obj(item);
                feed.entries.add(new Entry(
                        String.valueOf(row.getOrDefault("i", "")),
                        String.valueOf(row.getOrDefault("t", "")),
                        String.valueOf(row.getOrDefault("l", "")),
                        String.valueOf(row.getOrDefault("x", "")),
                        Long.parseLong(String.valueOf(row.getOrDefault("p", "0")))));
            }
        } catch (Exception e) {
            // A bad cache entry must not keep the inbox from loading live.
        }
    }

    /** One-time import from the pre-store JSON files, so nothing existing is lost. */
    private static void migrateLegacy(String key, Store.Kv kv) {
        if (!kv.get("feeds", "").isEmpty()) {
            return;
        }
        try {
            String safe = key.replaceAll("[^a-zA-Z0-9_-]", "");
            Path file = DATA.resolve("wire-" + (safe.isEmpty() ? "anon" : safe) + ".json");
            if (!Files.exists(file)) {
                return;
            }
            Map<String, Object> json = me.chrommob.rejact.Json
                    .obj(me.chrommob.rejact.Json.parse(Files.readString(file)));
            for (Map.Entry<String, Object> e : json.entrySet()) {
                kv.set(e.getKey(), String.valueOf(e.getValue()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** First sign-in on an account: this session's subscriptions move to the account. */
    private static void adoptAccount(WireView view, String name) {
        Person person = view.person;
        Store.Kv account = server.store.account(name);
        if (account.get("feeds", "").isEmpty() && !person.kv.get("feeds", "").isEmpty()) {
            for (String key : person.kv.keys()) {
                account.set(key, person.kv.get(key, ""));
            }
        }
        person.user = name;
        person.kv = account;
        hydrate(person);
        save(person);
    }

    // ---------- the view: one per session, shared by all its tabs ----------

    static final class WireView {
        final String key;
        final Person person;
        final Div grid;
        final Div feedList;
        final Div entryList;
        final Div reading;
        final Div filterBar;
        final Div accountBox;
        final Span headline;
        String query = "";
        String filter = "all";
        String feedFilter = "";
        String openId = "";

        WireView(String key, Person person, Div grid, Div feedList, Div entryList, Div reading,
                Div filterBar, Div accountBox, Span headline) {
            this.key = key;
            this.person = person;
            this.grid = grid;
            this.feedList = feedList;
            this.entryList = entryList;
            this.reading = reading;
            this.filterBar = filterBar;
            this.accountBox = accountBox;
            this.headline = headline;
        }
    }

    static Page page(String session) {
        Page page = new Page("/");
        Person person = load(session);

        Span headline = new Span().cssClass("pill");
        Div grid = new Div().cssClass("wire").attr("data-view", "list");
        Div feedList = new Div().cssClass("list");
        Div entryList = new Div().cssClass("list");
        Div filterBar = new Div().cssClass("row");
        Div reading = new Div().cssClass("reading")
                .add(new Span().cssClass("label").text("Reading"))
                .add(new Paragraph().cssClass("lede").text("Pick something to read."));

        Div accountBox = new Div().cssClass("list");
        WireView view = new WireView(session, person, grid, feedList, entryList, reading, filterBar,
                accountBox, headline);
        view.filter = person.kv.get("ui-filter", "all");
        view.feedFilter = person.kv.get("ui-feed", "");
        view.openId = person.kv.get("ui-open", "");
        VIEWS.put(session, view);

        Input search = new Input().cssClass("field").placeholder("search your inbox\u2026");
        search.onInput((ui, e) -> {
            view.query = e.value().toLowerCase();
            renderEntries(view);
        });

        Input feedUrl = new Input().cssClass("field").placeholder("feed url \u2014 paste any rss/atom link");
        Button add = Theme.button("add feed");
        add.onClick((ui, e) -> {
            String url = ui.valueOf(feedUrl).trim();
            if (url.isEmpty()) {
                return;
            }
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }
            feedUrl.attr("value", "");
            String target = url;
            headline.text("adding\u2026");
            new Thread(() -> subscribe(view, target), "wire-add").start();
        });

        Button refresh = Theme.ghost("\u21bb refresh all");
        refresh.onClick((ui, e) -> {
            headline.text("refreshing\u2026");
            new Thread(() -> {
                refreshPerson(view.person);
                renderAll(view);
                headline.text("");
            }, "wire-refresh").start();
        });

        page.onLoad(ui -> {
            // Feed lists fill in live as the fetchers land: first paint is never the whole story.
            new Thread(() -> {
                refreshPerson(view.person);
                renderAll(view);
            }, "wire-load").start();
        });

        // Virtual sites: the phone's back gesture, the in-app button, forward, and shared links
        // re-enter these handlers by themselves - no history code anywhere.
        page.site("read", param -> {
            Entry entry = findEntry(view.person, param);
            if (entry != null) {
                open(view, entry);
            }
        });
        page.site("", param -> renderEntries(view));

        page.root().add(Theme.head("Wire \u2014 everything you follow, live"))
                .add(new Body()
                        .add(new Div().cssClass("topbar")
                                .add(new Anchor().href("/").cssClass("brand")
                                        .add(new Div().cssClass("mark").text("W"))
                                        .add(new Span().text("Wire")))
                                .add(headline)
                                .add(Theme.linkPill("demos \u2192", "/demos")))
                        .add(new Div().cssClass("shell wide").add(
                                grid.add(
                                        new Div().cssClass("card rail-card")
                                                .add(new Span().cssClass("label").text("Sources"))
                                                .add(new Div().cssClass("row").add(feedUrl).add(add))
                                                .add(feedList)
                                                .add(refresh)
                                                .add(accountBox),
                                        new Div().cssClass("card inbox-card")
                                                .add(new Div().cssClass("row")
                                                        .add(new Span().cssClass("label").text("Inbox"))
                                                        .add(filterBar))
                                                .add(search)
                                                .add(entryList),
                                        new Div().cssClass("card reading-card").add(reading)),
                                Theme.foot("Subscriptions and read state live on the server, tied to your session."))));

        renderAll(view);
        return page;
    }

    // ---------- rendering (every string goes through text(), so it is escaped by default) ----------

    private static void renderAll(WireView view) {
        renderFilters(view);
        renderFeeds(view);
        renderEntries(view);
        renderAccount(view);
        persistUi(view);
    }

    /** View state is durable like everything else: reopening finds the same filter and article. */
    private static void persistUi(WireView view) {
        view.person.kv.set("ui-filter", view.filter);
        view.person.kv.set("ui-feed", view.feedFilter);
        view.person.kv.set("ui-open", view.openId);
    }

    private static void renderAccount(WireView view) {
        Person person = view.person;
        if (!person.user.isEmpty()) {
            Button out = Theme.ghost("log out");
            out.onClick((ui, e) -> {
                ui.logout();
                person.user = "";
                person.kv = ui.sessionState();
                hydrate(person);
                renderAll(view);
            });
            view.accountBox.replaceChildren(
                    new Span().cssClass("label").text("Account"),
                    new Div().cssClass("row").add(new Div().cssClass("grow")
                            .add(new Div().text(person.user))
                            .add(new Div().cssClass("tiny").text("signed in \u00b7 state follows you")))
                            .add(out));
            return;
        }
        Input name = new Input().cssClass("field").placeholder("name");
        Input pass = new Input().cssClass("field").attr("type", "password").placeholder("password");
        Button signIn = Theme.button("sign in");
        Button register = Theme.ghost("register");
        signIn.onClick((ui, e) -> {
            if (ui.login(ui.valueOf(name), ui.valueOf(pass))) {
                adoptAccount(view, ui.valueOf(name).trim());
                view.headline.text("signed in");
                renderAll(view);
            } else {
                view.headline.text("could not sign in");
            }
        });
        register.onClick((ui, e) -> {
            if (ui.register(ui.valueOf(name), ui.valueOf(pass))) {
                adoptAccount(view, ui.valueOf(name).trim());
                view.headline.text("welcome, " + view.person.user);
                renderAll(view);
            } else {
                view.headline.text("name taken, bad format, or password outside 8-1024 chars");
            }
        });
        view.accountBox.replaceChildren(
                new Span().cssClass("label").text("Account"),
                name,
                pass,
                new Div().cssClass("row").add(signIn).add(register),
                new Div().cssClass("tiny").text("optional \u2014 sign in to follow your feeds across devices"));
    }

    private static void renderFilters(WireView view) {
        Button all = new Button().cssClass(view.filter.equals("all") ? "on" : "").text("all");
        Button unread = new Button().cssClass(view.filter.equals("unread") ? "on" : "").text("unread");
        Button starred = new Button().cssClass(view.filter.equals("starred") ? "on" : "").text("starred");
        all.onClick((ui, e) -> {
            view.filter = "all";
            view.feedFilter = "";
            renderAll(view);
        });
        unread.onClick((ui, e) -> {
            view.filter = "unread";
            renderAll(view);
        });
        starred.onClick((ui, e) -> {
            view.filter = "starred";
            renderAll(view);
        });
        view.filterBar.replaceChildren(all, unread, starred);
    }

    private static void renderFeeds(WireView view) {
        List<me.chrommob.rejact.Element<?>> rows = new ArrayList<>();
        for (Feed feed : view.person.feeds.values()) {
            int unread = 0;
            for (Entry entry : feed.entries) {
                if (!entry.read) {
                    unread++;
                }
            }
            String host = host(feed.url);
            Div mono = new Div().cssClass("monogram")
                    .text(String.valueOf(Character.toUpperCase(feed.title.isEmpty() ? '?' : feed.title.charAt(0))));
            if (!host.isEmpty()) {
                mono.add(new me.chrommob.rejact.gen.tags.Image()
                        .src("https://icons.duckduckgo.com/ip3/" + host + ".ico")
                        .attr("onerror", "this.remove()")
                        .attr("alt", ""));
            }
            Div name = new Div().cssClass("grow")
                    .add(new Div().text(feed.title))
                    .add(new Div().cssClass("tiny").text(feed.error.isEmpty() ? (host.isEmpty() ? feed.url : host) : feed.error));
            name.onClick((ui, e) -> {
                view.feedFilter = view.feedFilter.equals(feed.id) ? "" : feed.id;
                renderAll(view);
            });
            Button drop = new Button().cssClass("ghost").text("\u00d7");
            drop.onClick((ui, e) -> {
                view.person.feeds.remove(feed.id);
                save(view.person);
                renderAll(view);
            });
            Div row = new Div().cssClass(view.feedFilter.equals(feed.id) ? "feedrow on" : "feedrow")
                    .add(mono).add(name);
            if (unread > 0) {
                row.add(new Span().cssClass("badge").text(String.valueOf(unread)));
            }
            row.add(drop);
            rows.add(row);
        }
        if (rows.isEmpty()) {
            rows.add(new Paragraph().cssClass("lede").text("No feeds yet. Paste one above."));
        }
        view.feedList.replaceChildren(rows.toArray(new me.chrommob.rejact.Element<?>[0]));
    }

    private static void renderEntries(WireView view) {
        List<Entry> shown = new ArrayList<>();
        for (Feed feed : view.person.feeds.values()) {
            if (!view.feedFilter.isEmpty() && !view.feedFilter.equals(feed.id)) {
                continue;
            }
            for (Entry entry : feed.entries) {
                boolean ok = switch (view.filter) {
                    case "unread" -> !entry.read;
                    case "starred" -> entry.starred;
                    default -> true;
                };
                if (!ok) {
                    continue;
                }
                if (!view.query.isEmpty()
                        && !(entry.title + " " + entry.text).toLowerCase().contains(view.query)) {
                    continue;
                }
                shown.add(entry);
            }
        }
        shown.sort((a, b) -> Long.compare(b.published, a.published));

        List<me.chrommob.rejact.Element<?>> rows = new ArrayList<>();
        for (Entry entry : shown) {
            Entry it = entry;
            Div text = new Div().cssClass("grow")
                    .add(new Div().cssClass("etitle").text(entry.title))
                    .add(new Div().cssClass("emeta").text(feedOf(view, entry) + " \u00b7 " + ago(entry.published)));
            if (!entry.text.isEmpty()) {
                text.add(new Div().cssClass("esnip").text(snippet(entry.text)));
            }
            text.onClick((ui, e) -> {
                int width = ui.viewportWidth();
                if (width > 0 && width <= 760) {
                    ui.go("read", it.id);
                } else {
                    open(view, it);
                }
            });
            Button star = new Button().cssClass(entry.starred ? "star on" : "star")
                    .text(entry.starred ? "\u2605" : "\u2606");
            star.onClick((ui, e) -> {
                if (it.starred) {
                    it.starred = false;
                    view.person.starred.remove(it.id);
                } else {
                    it.starred = true;
                    if (!view.person.starred.contains(it.id)) {
                        view.person.starred.add(it.id);
                    }
                }
                save(view.person);
                renderEntries(view);
            });
            Div row = new Div().cssClass("entry" + (entry.read ? " read" : "")
                    + (view.openId.equals(entry.id) ? " sel" : ""));
            if (!entry.read) {
                row.add(new Div().cssClass("dot"));
            }
            rows.add(row.add(text).add(star));
        }
        if (rows.isEmpty()) {
            rows.add(new Paragraph().cssClass("lede").text("Nothing here right now."));
        }
        view.entryList.replaceChildren(rows.toArray(new me.chrommob.rejact.Element<?>[0]));
    }

    private static void open(WireView view, Entry entry) {
        entry.read = true;
        if (!view.person.read.contains(entry.id)) {
            view.person.read.add(entry.id);
        }
        save(view.person);
        view.openId = entry.id;

        Div prose = new Div().cssClass("prose");
        if (entry.text.isEmpty()) {
            prose.add(new Paragraph().cssClass("lede").text("(no content \u2014 open the original)"));
        } else {
            for (String chunk : entry.text.split("\n\n")) {
                if (!chunk.isBlank()) {
                    prose.add(new Paragraph().text(chunk.trim()));
                }
            }
        }
        Button unreadBtn = Theme.ghost("mark unread");
        unreadBtn.onClick((ui, e) -> {
            entry.read = false;
            view.person.read.remove(entry.id);
            save(view.person);
            renderEntries(view);
        });
        Button starBtn = Theme.ghost(entry.starred ? "\u2605 starred" : "\u2606 star");
        starBtn.onClick((ui, e) -> {
            if (entry.starred) {
                entry.starred = false;
                view.person.starred.remove(entry.id);
            } else {
                entry.starred = true;
                if (!view.person.starred.contains(entry.id)) {
                    view.person.starred.add(entry.id);
                }
            }
            save(view.person);
            open(view, entry);
        });
        Div actions = new Div().cssClass("row").add(starBtn).add(unreadBtn);
        if (!entry.link.isEmpty()) {
            actions.add(new Anchor().href(entry.link).cssClass("backpill")
                    .attr("target", "_blank").attr("rel", "noreferrer").text("open original \u2197"));
        }
        Button back = new Button().cssClass("ghost backbtn").text("\u2190 back to inbox");
        back.onClick((ui, e) -> ui.back());
        view.reading.replaceChildren(
                back,
                new Span().cssClass("rlabel").text(feedOf(view, entry) + " \u00b7 " + ago(entry.published)),
                new H2().cssClass("rtitle").text(entry.title),
                actions,
                prose);
        renderEntries(view);
        persistUi(view);
    }

    private static Entry findEntry(Person person, String id) {
        for (Feed feed : person.feeds.values()) {
            for (Entry entry : feed.entries) {
                if (entry.id.equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static String feedOf(WireView view, Entry entry) {
        for (Feed feed : view.person.feeds.values()) {
            for (Entry candidate : feed.entries) {
                if (candidate == entry) {
                    return feed.title;
                }
            }
        }
        return "";
    }

    private static String host(String url) {
        try {
            String h = java.net.URI.create(url).getHost();
            return h == null ? "" : h.replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return "";
        }
    }

    private static String snippet(String text) {
        String first = "";
        for (String line : text.split("\n")) {
            String candidate = line.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = candidate;
            }
            // URL-dump lines ("Article URL: ...", raw links) look like debug output in a list.
            if (!candidate.contains("://") && !candidate.matches("(?i)[A-Za-z #]+: *\\S.*")) {
                return candidate.length() > 140 ? candidate.substring(0, 138) + "\u2026" : candidate;
            }
            if (!candidate.contains("://")) {
                return candidate.length() > 140 ? candidate.substring(0, 138) + "\u2026" : candidate;
            }
        }
        return first.length() > 140 ? first.substring(0, 138) + "\u2026" : first;
    }

    private static String ago(long published) {
        if (published <= 0) {
            return "just now";
        }
        long s = Math.max(0, (System.currentTimeMillis() - published) / 1000);
        if (s < 60) {
            return "just now";
        }
        if (s < 3600) {
            return (s / 60) + "m ago";
        }
        if (s < 86400) {
            return (s / 3600) + "h ago";
        }
        return (s / 86400) + "d ago";
    }

    // ---------- fetching ----------

    private static void subscribe(WireView view, String url) {
        Feed feed = new Feed("f" + (++view.person.seq), url, url);
        fetchFeed(feed);
        if (!feed.error.isEmpty() && feed.entries.isEmpty()) {
            // auto-discovery, lite: common feed suffixes
            for (String suffix : new String[] { "/feed", "/rss.xml", "/atom.xml", "/index.xml" }) {
                feed = new Feed("f" + view.person.seq, url + suffix, url + suffix);
                fetchFeed(feed);
                if (feed.error.isEmpty() || !feed.entries.isEmpty()) {
                    break;
                }
            }
        }
        if (feed.error.isEmpty() || !feed.entries.isEmpty()) {
            feed.error = "";
            view.person.feeds.put(feed.id, feed);
            applyFlags(view.person, feed);
            save(view.person);
        }
        persistEntries(view.person, feed);
        renderAll(view);
        view.headline.text("");
    }

    private static void refreshPerson(Person person) {
        for (Feed feed : person.feeds.values()) {
            fetchFeed(feed);
            applyFlags(person, feed);
            persistEntries(person, feed);
        }
    }

    private static void applyFlags(Person person, Feed feed) {
        for (Entry entry : feed.entries) {
            entry.read = person.read.contains(entry.id);
            entry.starred = person.starred.contains(entry.id);
        }
    }

    private static void fetchFeed(Feed feed) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(feed.url))
                    .header("User-Agent", "ReJact-Wire/1.0 (+dev.chrommob.fun)")
                    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                    .timeout(Duration.ofSeconds(12))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode());
            }
            merge(feed, parse(response.body()));
            feed.error = "";
        } catch (Exception e) {
            if (feed.entries.isEmpty()) {
                feed.error = "can't load this feed right now";
            }
        }
    }

    private record ParsedFeed(String title, List<Entry> entries) {
    }

    private static void merge(Feed feed, ParsedFeed parsed) {
        if (parsed == null) {
            return;
        }
        if (parsed.title() != null && !parsed.title().isBlank()) {
            feed.title = parsed.title();
        }
        for (Entry incoming : parsed.entries()) {
            boolean known = false;
            for (Entry existing : feed.entries) {
                if (existing.id.equals(incoming.id)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                feed.entries.add(incoming);
            }
        }
        feed.entries.sort((a, b) -> Long.compare(b.published, a.published));
        while (feed.entries.size() > KEEP_PER_FEED) {
            feed.entries.remove(feed.entries.size() - 1);
        }
    }

    private static ParsedFeed parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();
            if (root == null) {
                return null;
            }
            if ("rss".equals(root.getTagName())) {
                return parseRss(root);
            }
            return parseAtom(root);
        } catch (Exception e) {
            return null;
        }
    }

    private static ParsedFeed parseRss(Element root) {
        Element channel = child(root, "channel");
        if (channel == null) {
            return null;
        }
        List<Entry> entries = new ArrayList<>();
        for (Element item : children(channel, "item")) {
            String link = text(item, "link");
            String id = text(item, "guid");
            if (id.isEmpty()) {
                id = link;
            }
            String content = text(item, "content:encoded");
            if (content.isEmpty()) {
                content = text(item, "description");
            }
            entries.add(new Entry(id, text(item, "title"), link, plain(content),
                    date(text(item, "pubDate"))));
        }
        return new ParsedFeed(text(channel, "title"), entries);
    }

    private static ParsedFeed parseAtom(Element root) {
        List<Entry> entries = new ArrayList<>();
        for (Element item : children(root, "entry")) {
            String link = "";
            for (Element l : children(item, "link")) {
                String rel = l.getAttribute("rel");
                if (rel.isEmpty() || "alternate".equals(rel)) {
                    link = l.getAttribute("href");
                    break;
                }
            }
            String id = text(item, "id");
            if (id.isEmpty()) {
                id = link;
            }
            String content = text(item, "content");
            if (content.isEmpty()) {
                content = text(item, "summary");
            }
            String when = text(item, "published");
            if (when.isEmpty()) {
                when = text(item, "updated");
            }
            entries.add(new Entry(id, text(item, "title"), link, plain(content), date(when)));
        }
        return new ParsedFeed(text(root, "title"), entries);
    }

    private static Element child(Element parent, String name) {
        for (Element e : children(parent, name)) {
            return e;
        }
        return null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element el && name.equals(el.getTagName())) {
                out.add(el);
            }
        }
        return out;
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        return e == null ? "" : e.getTextContent().trim();
    }

    private static long date(String raw) {
        if (raw == null || raw.isBlank()) {
            return System.currentTimeMillis();
        }
        try {
            return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(raw)).toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(raw).toEpochMilli();
        } catch (Exception ignored) {
        }
        return System.currentTimeMillis();
    }

    /** Flatten feed HTML to plain text - rendering then escapes it, so nothing executes. */
    private static String plain(String html) {
        if (html == null) {
            return "";
        }
        String s = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
        s = s.replaceAll("(?i)</(p|div|br|li|h[1-6]|tr)>", "\n\n");
        s = s.replaceAll("(?s)<[^>]*>", " ");
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&#8217;", "'")
                .replace("&#8211;", "-").replace("&nbsp;", " ").replace("&rsquo;", "'")
                .replace("&ldquo;", "\"").replace("&rdquo;", "\"");
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        // Long scheme prefixes read as debug noise in a reading pane: keep host and path.
        s = s.replace("https://", "").replace("http://", "").replace("www.", "");
        return s;
    }

    /** The server keeps watch: anything new arrives in open tabs by itself. */
    private static void startRefresher() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wire-watch");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            for (WireView view : VIEWS.values()) {
                Person person = view.person;
                boolean changed = false;
                for (Feed feed : person.feeds.values()) {
                    int before = feed.entries.size();
                    List<String> known = new ArrayList<>();
                    for (Entry entry : feed.entries) {
                        known.add(entry.id);
                    }
                    fetchFeed(feed);
                    for (Entry entry : feed.entries) {
                        if (!known.contains(entry.id)) {
                            entry.read = false;
                            changed = true;
                        }
                    }
                    applyFlags(person, feed);
                    if (feed.entries.size() != before) {
                        changed = true;
                    }
                }
                if (changed) {
                    renderAll(view);
                }
            }
        }, 10, 600, TimeUnit.SECONDS);
    }
}
