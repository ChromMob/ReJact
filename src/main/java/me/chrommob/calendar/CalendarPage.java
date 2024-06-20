package me.chrommob.calendar;

import me.chrommob.builder.Page;
import me.chrommob.builder.WebPageBuilder;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;

import static me.chrommob.builder.html.constants.GlobalAttributes.*;
import static me.chrommob.builder.html.constants.GlobalAttributes.CONTENT;

public class CalendarPage {

    private final String RGB_BACKGROUND = "#1a2733";
    private final WebPageBuilder builder;

    public CalendarPage(WebPageBuilder builder) {
        this.builder = builder;
        build();
    }

    private void build() {
        Page homePage = builder.newPage("/");


        homePage.cssBuilder().addClass("active", "border", "1px solid white");

        homePage.root().addChild(new HtmlTag().event(EventTypes.BEFORELOAD, (session, htmlElement) -> {
                    String username = session.getCookie("username");
                    if (username != null) {
                        return;
                    }
                    session.redirect("/login");
                })
                .addAttribute(LANG, "en")
                .addChild(new HeadTag().addChild(new TitleTag().plainText("Login"))
                        .addChild(new MetaTag().addAttribute(CHARSET, "utf-8"))
                        .addChild(new MetaTag().addAttribute(NAME, "viewport").addAttribute(CONTENT,
                                "width=device-width, initial-scale=1")))
                .addChild(new BodyTag()
                        .addChild(new Navbar())
                        .addChild(new Calendar())
                )
        );

        homePage.build();
    }

    class Calendar extends DivTag {
        public Calendar() {
            super();
            css("background-color", RGB_BACKGROUND).css("display", "contents");
        }


    }


    class Navbar extends NavTag {
        public Navbar() {
            super();
            css("position", "fixed").css("top", "0").css("left", "0").css("right", "0").css("z-index", "9")
                    .css("display", "flex").css("flex-direction", "column").css("align-items", "center")
                    .css("justify-content", "center").css("background", RGB_BACKGROUND);

            addChild(new DivTag().css("display", "flex").css("flex-direction", "row").css("align-items", "center")
                    .css("justify-content", "center").css("width", "100%").css("height", "100%")
                    .css("background", RGB_BACKGROUND)
                    .addChild(new BoldTag().plainText("ReJact").css("color", "white").css("font-size", "30px")));
        }
    }
}
