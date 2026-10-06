package me.chrommob.rejact.demo;

import me.chrommob.rejact.Element;
import me.chrommob.rejact.gen.tags.Anchor;
import me.chrommob.rejact.gen.tags.Body;
import me.chrommob.rejact.gen.tags.Button;
import me.chrommob.rejact.gen.tags.Div;
import me.chrommob.rejact.gen.tags.H1;
import me.chrommob.rejact.gen.tags.H2;
import me.chrommob.rejact.gen.tags.Head;
import me.chrommob.rejact.gen.tags.Link;
import me.chrommob.rejact.gen.tags.Meta;
import me.chrommob.rejact.gen.tags.Paragraph;
import me.chrommob.rejact.gen.tags.Span;
import me.chrommob.rejact.gen.tags.Style;
import me.chrommob.rejact.gen.tags.Title;

/**
 * One design language for every page: dark glass panels, Space Grotesk display type over Inter,
 * a sticky brand bar, and one shell rhythm (eyebrow, heading, lede) that every demo repeats.
 * Layout rules live here; content lives in {@link Examples}.
 */
public final class Theme {
    private Theme() {
    }

    static Head head(String title) {
        return new Head()
                .add(new Meta().charset("utf-8"))
                .add(new Meta().name("viewport").content("width=device-width, initial-scale=1, viewport-fit=cover"))
                .add(new Meta().name("theme-color").content("#0f1015"))
                .add(new Title().text(title))
                .add(new Link().rel("stylesheet")
                        .href("https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700"
                                + "&family=Space+Grotesk:wght@500;600;700&display=swap"))
                .add(new Style().raw(CSS));
    }

    static Div topbar(Element<?>... right) {
        Div bar = new Div().cssClass("topbar")
                .add(new Anchor().href("/").cssClass("brand")
                        .add(new Div().cssClass("mark").text("R"))
                        .add(new Span().text("ReJact")));
        for (Element<?> el : right) {
            bar.add(el);
        }
        return bar;
    }

    static Anchor backPill() {
        return linkPill("\u2190 demos", "/demos");
    }

    static Anchor linkPill(String label, String href) {
        return new Anchor().href(href).cssClass("backpill").text(label);
    }

    static Div shell(Element<?>... content) {
        return new Div().cssClass("shell").add(content);
    }

    static Div pageHead(String eyebrow, String heading, String lede) {
        return new Div().cssClass("pagehead")
                .add(new Span().cssClass("eyebrow").text(eyebrow))
                .add(new H1().text(heading))
                .add(new Paragraph().cssClass("lede").text(lede));
    }

    static Div card(Element<?>... content) {
        return new Div().cssClass("card").add(content);
    }

    static Div panel(String title, String desc, Element<?>... content) {
        Div panel = new Div().cssClass("card")
                .add(new H2().text(title))
                .add(new Paragraph().cssClass("lede").text(desc));
        for (Element<?> el : content) {
            panel.add(el);
        }
        return panel;
    }

    static Anchor gcard(String href, String thumb, String title, String desc) {
        return new Anchor().href(href).cssClass("gcard")
                .add(new Div().cssClass("thumb " + thumb))
                .add(new H2().text(title))
                .add(new Paragraph().text(desc));
    }

    static Paragraph foot(String text) {
        return new Paragraph().cssClass("foot").text(text);
    }

    static Button button(String label) {
        return new Button().text(label);
    }

    static Button ghost(String label) {
        return new Button().cssClass("ghost").text(label);
    }

    static Span pill(String text) {
        return new Span().cssClass("pill").text(text);
    }

    static final String CSS = """
            :root {
              --bg: #0f1015;
              --panel: #161821;
              --panel-2: #1e2029;
              --sel: #262938;
              --line: #2b2e3a;
              --txt: #f3f4f6;
              --mut: #9ca3af;
              --mut-2: #6b7280;
              --body: #d1d5db;
              --acc: #7c5cfc;
              --acc-2: #a78bfa;
              --star: #ffb800;
              --danger: #fb7185;
              --r: 14px;
            }
            * { box-sizing: border-box; }
            html, body { margin: 0; }
            body {
              font-family: Inter, system-ui, sans-serif;
              color: var(--txt);
              background:
                radial-gradient(1100px 520px at 88% -12%, rgba(124, 92, 252, .12), transparent 60%),
                var(--bg);
              min-height: 100vh;
              min-height: 100dvh;
              display: flex;
              flex-direction: column;
              -webkit-font-smoothing: antialiased;
            }
            h1, h2, h3 { font-family: 'Space Grotesk', Inter, sans-serif; margin: 0; letter-spacing: -.02em; }
            h1 { font-size: clamp(1.7rem, 5.5vw, 2.4rem); line-height: 1.08; }
            h2 { font-size: 1.1rem; }
            p { margin: 0; }
            .label {
              color: var(--mut-2); font-size: 10px; font-weight: 600;
              letter-spacing: .08em; text-transform: uppercase;
            }
            .topbar {
              display: flex; align-items: center; gap: .8rem;
              padding: .8rem clamp(.9rem, 4vw, 2rem);
              padding-top: calc(.8rem + env(safe-area-inset-top));
              border-bottom: 1px solid var(--line);
              background: rgba(10, 14, 23, .72);
              backdrop-filter: blur(12px);
              position: sticky; top: 0; z-index: 5;
            }
            .brand {
              display: flex; align-items: center; gap: .6rem;
              font-family: 'Space Grotesk', Inter, sans-serif;
              font-weight: 700; font-size: 1.05rem;
              color: var(--txt); text-decoration: none;
            }
            .mark {
              width: 26px; height: 26px; border-radius: 8px;
              background: linear-gradient(135deg, var(--acc), var(--acc-2));
              display: flex; align-items: center; justify-content: center;
              font-size: .85rem; font-weight: 700; color: #fff;
            }
            .backpill, .pill {
              border: 1px solid var(--line); background: var(--panel);
              color: var(--txt); border-radius: 999px;
              padding: .55rem 1rem; font-size: .85rem; text-decoration: none;
              min-height: 40px; display: inline-flex; align-items: center;
            }
            .pill:empty { display: none; }
            .backpill { margin-left: auto; }
            .pill { min-height: 0; padding: .3rem .75rem; }
            .shell {
              width: 100%; max-width: 980px; margin: 0 auto;
              padding: clamp(1.4rem, 5vw, 3rem) clamp(.9rem, 4vw, 2rem) 3rem;
              display: flex; flex-direction: column; gap: 1.4rem; flex: 1;
            }
            .pagehead { display: flex; flex-direction: column; gap: .7rem; }
            .eyebrow {
              color: var(--acc-2); font-weight: 700; font-size: .74rem;
              letter-spacing: .16em; text-transform: uppercase;
            }
            .lede { color: var(--mut); line-height: 1.55; max-width: 56ch; }
            .card {
              background: linear-gradient(180deg, rgba(255, 255, 255, .035), rgba(255, 255, 255, 0) 42%), var(--panel);
              border: 1px solid var(--line);
              border-radius: var(--r); padding: clamp(1rem, 3vw, 1.4rem);
              display: flex; flex-direction: column; gap: .9rem;
              box-shadow: 0 12px 36px rgba(0, 0, 0, .28);
            }
            .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 1rem; }
            .gcard {
              display: flex; flex-direction: column; gap: .5rem;
              background: var(--panel); border: 1px solid var(--line);
              border-radius: 18px; padding: 1rem; text-decoration: none; color: inherit;
              transition: transform .12s ease, border-color .12s ease, background .12s ease;
            }
            .gcard:hover { transform: translateY(-3px); border-color: rgba(139, 124, 255, .55); background: var(--panel-2); }
            .gcard p { color: var(--mut); font-size: .88rem; line-height: 1.5; }
            .thumb { height: 130px; border-radius: 14px; }
            .t1 { background: linear-gradient(135deg, #7c6cff, #5eead4); }
            .t2 { background: linear-gradient(135deg, #f59e0b, #fb7185); }
            .t3 { background: linear-gradient(135deg, #38bdf8, #a78bfa); }
            .t4 { background: linear-gradient(135deg, #34d399, #22d3ee); }
            .hero { width: 100%; height: clamp(170px, 28vw, 300px); object-fit: cover; border-radius: 18px; }
            button {
              background: var(--acc);
              color: #fff; border: none; border-radius: 9px;
              padding: .8rem 1.3rem; font-family: Inter, sans-serif;
              font-size: .9rem; font-weight: 600; cursor: pointer; min-height: 44px;
              transition: background .1s ease, transform .1s ease;
            }
            button:hover { background: #8a6bff; }
            button:active { transform: translateY(1px); filter: brightness(.95); }
            button.ghost {
              background: var(--panel-2); color: var(--txt);
              border: 1px solid var(--line); font-weight: 500;
            }
            button.ghost:hover { background: var(--sel); }
            .field {
              background: var(--panel-2); border: 1px solid var(--line); color: var(--txt);
              border-radius: 9px; padding: .8rem 1rem; font-size: .9rem; flex: 1; min-width: 0;
              font-family: Inter, sans-serif;
            }
            .field::placeholder { color: var(--mut); }
            .field:focus { outline: none; border-color: var(--acc); }
            .row { display: flex; gap: .6rem; align-items: center; flex-wrap: wrap; }
            .row .field { flex: 1 1 200px; }
            .big {
              font-family: 'Space Grotesk', Inter, sans-serif;
              font-size: clamp(3rem, 12vw, 5rem); font-weight: 700;
              text-align: center; font-variant-numeric: tabular-nums;
            }
            .bigish {
              font-family: 'Space Grotesk', Inter, sans-serif;
              font-size: clamp(1.8rem, 7vw, 2.8rem); font-weight: 650;
              text-align: center; font-variant-numeric: tabular-nums;
            }
            .center { text-align: center; color: var(--mut); }
            .screen {
              background: rgba(0, 0, 0, .35); border: 1px solid var(--line);
              border-radius: 14px; padding: 1rem 1.1rem; font-size: 1rem;
              min-height: 3.1rem; display: flex; align-items: center;
              overflow-wrap: anywhere;
            }
            .track {
              flex: 1; height: 12px; border-radius: 999px;
              background: var(--panel-2); border: 1px solid var(--line); overflow: hidden;
            }
            .fill {
              height: 100%; width: 0%;
              background: linear-gradient(90deg, var(--acc), var(--acc-2));
              border-radius: 999px; transition: width .18s ease;
            }
            .list { display: flex; flex-direction: column; gap: .55rem; }
            .duo { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1rem; }
            textarea.field { min-height: 130px; resize: vertical; line-height: 1.5; }
            .chain { display: flex; gap: 5px; flex-wrap: wrap; }
            .day {
              width: 24px; height: 24px; border-radius: 7px;
              background: var(--panel-2); border: 1px solid var(--line);
            }
            .photo { max-width: 100%; border-radius: 14px; }
            .tiny { color: var(--mut); font-size: .8rem; }
            .grow { flex: 1; min-width: 0; cursor: pointer; }
            .shell.wide { max-width: 1440px; }
            .wire { display: grid; grid-template-columns: 260px 400px 1fr; gap: 12px; align-items: start; }
            .wire .card { min-width: 0; }
            .feedrow {
              display: flex; gap: .6rem; align-items: center;
              padding: .45rem .5rem; border-radius: 8px;
              border-left: 3px solid transparent; cursor: pointer;
            }
            .feedrow:hover { background: var(--panel-2); }
            .feedrow.on { background: var(--sel); border-left-color: var(--acc); }
            .feedrow .grow > div {
              white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
            }
            .feedrow button {
              min-height: 28px; padding: .15rem .5rem; font-size: .8rem;
              border-radius: 7px; opacity: .55;
            }
            .feedrow button:hover { opacity: 1; }
            .monogram {
              position: relative; width: 28px; height: 28px; border-radius: 7px;
              background: var(--sel); overflow: hidden; flex: none;
              display: flex; align-items: center; justify-content: center;
              font-size: .78rem; font-weight: 700; color: var(--acc-2);
            }
            .monogram img {
              position: absolute; inset: 0; width: 100%; height: 100%;
              object-fit: cover; background: var(--sel);
            }
            .badge {
              background: var(--acc); color: #fff; border-radius: 999px;
              padding: .12rem .5rem; font-size: .68rem; font-weight: 700; flex: none;
            }
            .seg {
              display: inline-flex; gap: 2px; padding: 3px;
              background: #11131a; border: 1px solid var(--line); border-radius: 9px;
            }
            .seg button {
              min-height: 30px; padding: .3rem .8rem; font-size: .8rem; font-weight: 500;
              border: none; border-radius: 6px; background: transparent; color: var(--mut);
            }
            .seg button:hover { background: var(--panel-2); color: var(--txt); }
            .seg button.on { background: var(--sel); color: var(--txt); }
            button.star.on { color: var(--star); }
            .entry {
              display: flex; gap: .65rem; align-items: center;
              border: 1px solid transparent; border-radius: 10px; padding: .7rem .75rem;
            }
            .entry:hover { background: var(--panel-2); border-color: var(--line); }
            .entry.sel { background: #1a1d28; border-color: var(--acc); box-shadow: inset 0 1px 0 rgba(255, 255, 255, .08); }
            .dot {
              width: 6px; height: 6px; border-radius: 50%;
              background: var(--acc); flex: none;
            }
            .entry button.star {
              min-height: 32px; width: 34px; padding: 0; font-size: 1rem;
              border-radius: 8px; color: var(--mut-2); background: transparent; border-color: transparent;
            }
            .entry button.star:hover { background: var(--sel); }
            .etitle {
              font-size: .94rem; line-height: 1.4; font-weight: 600; letter-spacing: -.01em;
            }
            .entry.read .etitle { font-weight: 500; color: var(--body); }
            .emeta {
              color: var(--mut-2); font-size: .72rem; letter-spacing: .05em;
              text-transform: uppercase; margin-top: .15rem;
            }
            .esnip {
              color: var(--mut); font-size: .8rem; line-height: 1.5; margin-top: .2rem;
              display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
              overflow: hidden;
            }
            .reading { display: flex; flex-direction: column; gap: 1rem; }
            .rlabel {
              color: var(--mut); font-size: .72rem; font-weight: 600;
              letter-spacing: .06em; text-transform: uppercase;
            }
            .rtitle { font-size: clamp(1.5rem, 3vw, 2.1rem); line-height: 1.15; letter-spacing: -.03em; }
            .prose { max-width: 720px; }
            .prose p { font-size: 1rem; line-height: 1.8; color: var(--body); margin: 1rem 0 0; overflow-wrap: anywhere; }
            .backbtn { display: none; }
            @media (max-width: 1100px) {
              .wire { grid-template-columns: 260px 1fr; }
              .wire .reading { grid-column: 1 / -1; }
            }
            @media (max-width: 760px) {
              .wire { grid-template-columns: 1fr; }
              .backbtn { display: inline-flex; }
              html:not([data-rj-site="read"]) .reading-card { display: none; }
              html[data-rj-site="read"] .rail-card,
              html[data-rj-site="read"] .inbox-card { display: none; }
            }
            .rrow { display: flex; gap: .8rem; align-items: center; }
            .rrow .who { width: 11ch; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--mut); font-size: .9rem; }
            .rrow .pct { width: 4.5ch; text-align: right; font-variant-numeric: tabular-nums; color: var(--mut); font-size: .9rem; }
            .brow {
              display: flex; justify-content: space-between;
              background: var(--panel-2); border: 1px solid var(--line);
              border-radius: 12px; padding: .6rem .9rem; font-size: .92rem;
            }
            .grid {
              display: grid; grid-template-columns: repeat(28, 1fr);
              gap: 4px; padding: 4px;
              background: rgba(0, 0, 0, .35); border: 1px solid var(--line); border-radius: 16px;
            }
            .cell {
              aspect-ratio: 1; border-radius: 5px; background: #131a2b; cursor: pointer;
              transition: transform .08s ease;
            }
            .cell:hover { transform: scale(1.18); }
            .swatches { display: flex; gap: .6rem; flex-wrap: wrap; }
            .swatch {
              width: 44px; height: 44px; border-radius: 12px; cursor: pointer;
              border: 2px solid var(--line); transition: transform .1s ease;
            }
            .swatch:hover { transform: scale(1.1); }
            .foot {
              color: var(--mut); font-size: .85rem; text-align: center;
              border-top: 1px solid var(--line); padding-top: 1.2rem;
            }
            @media (max-width: 640px) {
              .grid { gap: 3px; }
              .rrow .who { width: 8ch; }
            }
            """;
}
