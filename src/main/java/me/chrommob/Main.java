package me.chrommob;

import me.chrommob.builder.WebPageBuilder;
import me.chrommob.calendar.CalendarPage;
import me.chrommob.test.TestPage;

public class Main {
    public static void main(String[] args) {
        new Main().buildPage();
    }

    public void buildPage() {
        int port = 9080;
        WebPageBuilder builder = new WebPageBuilder("wss.chrommob.fun", 9090, port, port);
        new CalendarPage(builder);
        new TestPage(builder);
    }
}


