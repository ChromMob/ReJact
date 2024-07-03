package me.chrommob.demo;

import me.chrommob.builder.Page;
import me.chrommob.builder.WebPageBuilder;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;

import static me.chrommob.builder.html.constants.GlobalAttributes.*;
import static me.chrommob.builder.html.constants.GlobalAttributes.CONTENT;

public class DraggableExamplePage {

    private final String RGB_BACKGROUND = "#1a2733";
    private final WebPageBuilder builder;

    public DraggableExamplePage(WebPageBuilder builder) {
        this.builder = builder;
        build();
    }

    private void build() {
        Page homePage = builder.newPage("/demo");


        homePage.cssBuilder().addClass("active", "border", "1px solid white");

        homePage.root().addChild(new HtmlTag()
                .addAttribute(LANG, "en")
                .addChild(new HeadTag().addChild(new TitleTag().plainText("Draggable Demo"))
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
            css("background-color", RGB_BACKGROUND).css("position", "fixed").css("width", "100%").css("height", "100%")
                            .css("display", "flex").css("left", "0");
            addChild(new DivTag().css("width", "33%").css("height", "100%").css("align-content", "center")
                    .css("position", "relative")
                    .css("padding", "10px").css("border", "1px solid white")
                            .event(EventTypes.DROP, (session, drop) -> {
                                String draggedId = session.getCookie("draggedId");
                                if (draggedId == null) {
                                    return;
                                }
                                session.getHtmlElement(draggedId, dragged -> {
                                    session.removeElementById(draggedId);
                                    session.addChild(drop, dragged.outerHtml());
                                });
                            })

                    .addAttribute(ID, "droppable1")
                            .noCallback(EventTypes.DRAGOVER)
            );
            addChild(new DivTag().css("width", "33%").css("height", "100%").css("align-content", "center")
                    .css("padding", "10px").css("border", "1px solid white")
                            .event(EventTypes.DROP, (session, drop) -> {
                                String draggedId = session.getCookie("draggedId");
                                if (draggedId == null) {
                                    return;
                                }
                                session.getHtmlElement(draggedId, dragged -> {
                                    session.removeElementById(draggedId);
                                    session.addChild(drop, dragged.outerHtml());
                                });
                            })
                    .addAttribute(ID, "droppable2")
                            .noCallback(EventTypes.DRAGOVER)
            );
            addChild(new DivTag().css("width", "33%").css("height", "100%").css("align-content", "center")
                    .css("position", "relative")
                    .css("padding", "10px").css("border", "1px solid white")
                    .event(EventTypes.DROP, (session, drop) -> {
                        String draggedId = session.getCookie("draggedId");
                        if (draggedId == null) {
                            return;
                        }
                        session.getHtmlElement(draggedId, dragged -> {
                            session.removeElementById(draggedId);
                            session.addChild(drop, dragged.outerHtml());
                        });
                    })
                    .addAttribute(ID, "droppable3")
                    .noCallback(EventTypes.DRAGOVER)
                    .addChild(new ImageTag()
                            .setImage("https://img.youtube.com/vi/G7lZBKFFnls/maxresdefault.jpg")
                            .join()
                            .css("width", "100%")
                            .addAttribute(DRAGGABLE, "true")
                            .event(EventTypes.DRAGSTART, (session, drag) -> {
                                session.setCookie("draggedId", drag.id());
                            })
                            .event(EventTypes.TOUCHSTART, (session, drag) -> {
                                session.setCookie("draggedId", drag.id());
                            })
                    )
            );
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
