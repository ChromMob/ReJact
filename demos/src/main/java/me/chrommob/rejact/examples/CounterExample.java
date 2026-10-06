package me.chrommob.rejact.examples;

import java.util.concurrent.atomic.AtomicInteger;

import me.chrommob.rejact.Page;
import me.chrommob.rejact.Server;
import me.chrommob.rejact.gen.tags.Body;
import me.chrommob.rejact.gen.tags.Button;
import me.chrommob.rejact.gen.tags.Div;
import me.chrommob.rejact.gen.tags.H1;
import me.chrommob.rejact.gen.tags.Head;
import me.chrommob.rejact.gen.tags.Input;
import me.chrommob.rejact.gen.tags.Meta;
import me.chrommob.rejact.gen.tags.Paragraph;
import me.chrommob.rejact.gen.tags.Style;
import me.chrommob.rejact.gen.tags.Title;

/**
 * The smallest interesting ReJact v2 app: a live counter and a live greeting.
 * No JavaScript, no selectors, no ids. Java objects are the only currency.
 * Run: ./gradlew :demos:run --args='8092'  (or main of this class on the :demos classpath)
 */
public final class CounterExample {
    public static void main(String[] args) throws Exception {
        Server server = new Server(args.length > 0 ? Integer.parseInt(args[0]) : 8092);
        Page page = new Page("/");

        // State and elements are plain Java objects.
        AtomicInteger n = new AtomicInteger();
        H1 count = new H1().text("0");
        Input name = new Input().placeholder("Your name").value("world");
        Div greeting = new Div().text("Hello, world");

        // Every handler is a typed lambda; events carry typed payloads.
        Button minus = new Button().text("-").onClick((ui, e) -> count.text(Integer.toString(n.decrementAndGet())));
        Button plus = new Button().text("+").onClick((ui, e) -> count.text(Integer.toString(n.incrementAndGet())));
        Button reset = new Button().text("reset").onClick((ui, e) -> {
            n.set(0);
            count.text("0");
        });
        name.onInput((ui, e) -> greeting.text("Hello, " + e.value()));

        page.root()
                .add(new Head()
                        .add(new Meta().charset("utf-8"))
                        .add(new Meta().name("viewport").content("width=device-width, initial-scale=1"))
                        .add(new Title().text("Counter"))
                        .add(new Style().raw("*{box-sizing:border-box}body{font-family:system-ui,sans-serif;"
                                + "background:#0f172a;color:#e2e8f0;display:grid;place-items:center;min-height:100vh;margin:0}"
                                + ".box{text-align:center;display:grid;gap:1rem;justify-items:center}"
                                + "h1{font-size:4rem;margin:0}.row{display:flex;gap:.5rem}"
                                + "button{font-size:1.25rem;width:3.5rem;height:3.5rem;border-radius:12px;border:none;"
                                + "background:#6366f1;color:#fff;cursor:pointer}"
                                + "input{font-size:16px;padding:.75rem 1rem;border-radius:12px;border:1px solid #334155;"
                                + "background:#1e293b;color:#e2e8f0;outline:none}")))
                .add(new Body().add(new Div().cssClass("box")
                        .add(new Paragraph().text("ReJact v2 counter"))
                        .add(count)
                        .add(new Div().cssClass("row").add(minus).add(reset).add(plus))
                        .add(name)
                        .add(greeting)));

        server.registerPage("/", page);
        server.start();
        System.out.println("counter on http://127.0.0.1:" + server.port());
        Thread.currentThread().join();
    }
}
