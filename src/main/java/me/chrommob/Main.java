package me.chrommob;

import me.chrommob.builder.Page;
import me.chrommob.builder.WebPageBuilder;
import me.chrommob.builder.html.constants.HeadingLevel;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;

import java.io.File;
import static me.chrommob.builder.html.constants.GlobalAttributes.*;

public class Main {
    public static void main(String[] args) {
        new Main().buildPage();
    }

    public void buildPage() {
        int port = 8030;
        File pageFolder = new File("page");
        pageFolder.mkdirs();
        WebPageBuilder builder = new WebPageBuilder("localhost", 8080, port, port);
        Page homePage = builder.newPage("/");
        homePage.root().addChild(new HtmlTag().addAttribute(LANG, "en")
                .event(EventTypes.LOAD, (session, htmlElement) -> {
                    String username = session.getCookie("username");
                    String password = session.getCookie("password");
                    if (username != null && password != null) {
                        if (!username.equals("admin") || !password.equals("admin")) {
                            session.redirect("/login");
                        }
                    } else {
                        session.redirect("/login");
                    }
                })
                .addChild(new BodyTag()
                        .addChild(new Header())
                )
        );
        Page loginPage = builder.newPage("/login");
        loginPage.root().addChild(new HtmlTag().event(EventTypes.BEFORELOAD, (session, htmlElement) -> {
                    String username = session.getCookie("username");
                    String password = session.getCookie("password");
                    System.out.println(username + " " + password);
                    if (username != null && password != null) {
                        if (username.equals("admin") && password.equals("admin")) {
                            session.redirect("/");
                        }
                    }
                })
                        .addAttribute(LANG, "en")
                        .addChild(new HeadTag().addChild(new TitleTag().plainText("Login"))
                                .addChild(new MetaTag().addAttribute(CHARSET, "utf-8"))
                                .addChild(new MetaTag().addAttribute(NAME, "viewport").addAttribute(CONTENT, "width=device-width, initial-scale=1"))
                        )
                .addChild(new BodyTag()
                        .css("font-family", "Arial, sans-serif").css("display", "flex").css("justify-content", "center").css("align-items", "center")
                        .css("height", "100vh").css("margin", "0").css("background-color", "#f4f4f4")
                        .addChild(new LoginPage()
                                .addChild(new ButtonTag().plainText("Login").event(EventTypes.CLICK, (session, htmlElement) -> session.getHtmlElement("username", htmlElement1 -> session.getHtmlElement("password", htmlElement2 -> {
                                    session.setCookie("username", htmlElement1.value());
                                    session.setCookie("password", htmlElement2.value());
                                    if (htmlElement1.value().equals("admin") && htmlElement2.value().equals("admin")) {
                                        session.redirect("/");
                                    } else {
                                        session.redirect("/login");
                                    }
                                })))
                                )
                                .addChild(new ParagraphTag().addAttribute(ID, "message").plainText("")
                                        .css("color", "#fff").css("background-color", "red").css("padding", "10px")
                                        .css("border-radius", "5px").css("margin-top", "10px").addClassName("hidden")
                                )
                        )
                )
        );
        homePage.build();
        loginPage.build();
    }
}

class Header extends DivTag {
    public Header() {
        super();
        css("background-color", "#333").css("overflow", "hidden").css("display", "flex").css("align-items", "start")
                .addChild(new ATag().addAttribute(HREF, "/").plainText("ReJact")
                        .css("float", "left").css("color", "#f2f2f2").css("text-align", "center")
                        .css("padding", "14px 16px").css("font-size", "17px").css("text-decoration", "none"))
                .addChild(new ATag().addAttribute(HREF, "/login").plainText("Login")
                        .css("float", "right").css("color", "#f2f2f2").css("text-align", "center")
                        .css("padding", "14px 16px").css("font-size", "17px").css("text-decoration", "none"));
    }
}


class LoginPage extends Tag {
    public LoginPage() {
        super("div");
        css("background-color", "#fff").css("padding", "20px").css("border-radius", "5px")
                .css("box-shadow", "0 0 10px rgba(0, 0, 0, 0.1)");
        addChild(new HeadingTag(HeadingLevel.H2).plainText("Login"));
        addChild(new FormTag().css("display", "grid").css("grid-template-columns", "1fr 1fr").css("grid-gap", "10px")
                .addChild(new LabelTag().plainText("Username").addAttribute(FOR, "username"))
                .addChild(new InputTag().addAttribute(TYPE, "text").addAttribute(NAME, "username").addAttribute(ID, "username")
                        .css("margin-bottom", "10px"))
                .addChild(new LabelTag().plainText("Password").addAttribute(FOR, "password"))
                .addChild(new InputTag().addAttribute(TYPE, "password").addAttribute(NAME, "password").addAttribute(ID, "password")
                        .css("margin-bottom", "10px").event(EventTypes.KEYDOWN, (session, key) -> {
                            if (key.value().equalsIgnoreCase("enter")) {
                                session.getHtmlElement("username", htmlElement1 -> session.getHtmlElement("password", htmlElement2 -> {
                                            session.setCookie("username", htmlElement1.value());
                                            session.setCookie("password", htmlElement2.value());
                                            if (htmlElement1.value().equals("admin") && htmlElement2.value().equals("admin")) {
                                                session.redirect("/");
                                            } else {
                                                session.redirect("/login");
                                            }
                                        }));
                                return;
                            }
                            String password = key.value() + key.eventValue();
                            if (key.eventValue().equalsIgnoreCase("backspace") || key.eventValue().equalsIgnoreCase("delete")) {
                                session.getHtmlElement("password", htmlElement2 -> {
                                    String value = htmlElement2.value();
                                    if (!value.equals("admin")) {
                                        session.show("message");
                                        session.setInnerHtml("message", "Wrong password");
                                    } else {
                                        session.hide("message");
                                    }
                                });
                            } else {
                                if (!password.equals("admin")) {
                                    session.show("message");
                                    session.setInnerHtml("message", "Wrong password");
                                } else {
                                    session.hide("message");
                                }
                            }
                        })
                )
        );
    }
}