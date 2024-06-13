package me.chrommob;

import me.chrommob.builder.Page;
import me.chrommob.builder.WebPageBuilder;
import me.chrommob.builder.html.constants.GlobalAttributes;
import me.chrommob.builder.html.constants.HeadingLevel;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;
import me.chrommob.builder.socket.Session;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static me.chrommob.builder.html.constants.GlobalAttributes.*;

public class Main {
    static WebPageBuilder builder;
    public static final List<String> messages = new ArrayList<>();
    public static void main(String[] args) {
        new Main().buildPage();
    }

    public void buildPage() {
        int port = 9080;
        WebPageBuilder builder = new WebPageBuilder("wss.chrommob.fun", 9090, port, port);
        Main.builder = builder;
        Page homePage = builder.newPage("/");
        homePage.root().addChild(new HtmlTag().addAttribute(LANG, "en")
                        .addChild(new HeadTag().addChild(new TitleTag().plainText("ReJact"))
                                .addChild(new MetaTag().addAttribute(CHARSET, "utf-8"))
                                .addChild(new MetaTag().addAttribute(NAME, "viewport").addAttribute(CONTENT, "width=device-width, initial-scale=1"))
                        )
                .addChild(new BodyTag()
                        .addChild(new Header())
                        .addChild(new Chat(builder))
                        .addChild(new HeadingTag(HeadingLevel.H2).plainText("Users")
                                .css("margin-left", "20px")
                        )
                        .addChild(new DivTag().addAttribute(ID, "users"))
                ).event(EventTypes.BEFORELOAD, (sourceSession, htmlElement) -> {
                    syncOnlineUsers(sourceSession, false);
                }).event(EventTypes.BEFOREUNLOAD, (session, htmlElement) -> {
                    syncOnlineUsers(session, true);
                }).event(EventTypes.TENSECONDTIMER, (session, htmlElement) -> {
                    syncOnlineUsers(session, false);
                })
        );
        Page loginPage = builder.newPage("/login");
        loginPage.root().addChild(new HtmlTag().event(EventTypes.BEFORELOAD, (session, htmlElement) -> {
                    String username = session.getCookie("username");
                    if (username == null) {
                        return;
                    }
                    session.redirect("/");
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
                                .addChild(new ButtonTag().plainText("Login")
                                        .event(EventTypes.CLICK, (session, htmlElement) -> session.getHtmlElement("username", htmlElement1 -> {
                                            session.setCookie("username", htmlElement1.value());
                                            session.redirect("/");
                                        }))
                                )
                        )
                )
        );
        homePage.build();
        loginPage.build();
    }


    private void syncOnlineUsers(Session sourceSession, boolean ignoreCurrentUser) {
        String username = sourceSession.getCookie("username");
        if (username == null) {
            sourceSession.redirect("/login");
            return;
        }
        Tag sessionList = new ULTag().css("default", "display", "grid").css("grid-template-columns", "1fr 1fr").css("grid-gap", "10px");
        for (Session sessions : builder.getActiveSessions()) {
            if (ignoreCurrentUser && sourceSession == sessions) {
                continue;
            }
            if (sessions.getCookie("username") == null) {
                continue;
            }
            sessionList.addChild(new ParagraphTag().plainText(sessions.getCookie("username")));
        }
        for (Session sessions : builder.getActiveSessions()) {
            sessions.getHtmlElement("users", users -> {
                sessions.setInnerHtml(users, sessionList);
            });
        }
    }
}

class Header extends DivTag {
    public Header() {
        super();
        css("background-color", "#333").css("overflow", "hidden").css("display", "flex").css("align-items", "start")
                .addChild(new HeaderButton(true, "/", "ReJact"))
                .addChild(new HeaderButton(false,"/login", "Logout")
                        .event(EventTypes.CLICK, (session, htmlElement) -> session.clearCookies()));
    }

    static class HeaderButton extends ATag {
        public HeaderButton(boolean highlighted, String url, String text) {
            super();
            addAttribute(HREF, url).plainText(text)
                    .css(":hover", "background-color", "#ddd").css("hover", "color", "black")
                    .css("float", "left").css("color", "#f2f2f2").css("text-align", "center")
                    .css("padding", "14px 16px").css("font-size", "17px").css("text-decoration", "none");
            if (highlighted) {
                css("background-color", "#04AA6D").css("color", "#fff");
            }
        }
    }
}


class LoginPage extends Tag {
    public LoginPage() {
        super("div");
        css("background-color", "#fff").css("padding", "20px").css("border-radius", "5px")
                .css("box-shadow", "0 0 10px rgba(0, 0, 0, 0.1)");
        addChild(new HeadingTag(HeadingLevel.H2).plainText("Login"));
        addChild(new DivTag().css("display", "grid").css("grid-template-columns", "1fr 1fr").css("grid-gap", "10px")
                .addChild(new LabelTag().plainText("Username").addAttribute(FOR, "username")
                        .css(":hover", "text-decoration", "underline"))
                .addChild(new InputTag().addAttribute(TYPE, "text").addAttribute(NAME, "username").addAttribute(ID, "username")
                        .css("margin-bottom", "10px")
                        .event(EventTypes.KEYDOWN, (session, key) -> {
                            if (!key.eventValue().equalsIgnoreCase("enter")) {
                                return;
                            }
                            session.setCookie("username", key.value());
                            session.redirect("/");
                        })
                )
        );
    }
}

class Chat extends Tag {
    private final WebPageBuilder webPageBuilder;
    private final Map<String, Long> typingUsers = new HashMap<>();
    //
    public Chat(WebPageBuilder webPageBuilder) {
        super("div");
        this.webPageBuilder = webPageBuilder;

        Tag messages = new DivTag().css("overflow-y", "scroll").css("height", "600px").css("background-color", "#eee")
                        .css("overflow-x", "hidden").css("padding", "10px");
        css("background-color", "#fff").css("padding", "20px").css("border-radius", "5px")
                .css("box-shadow", "0 0 10px rgba(0, 0, 0, 0.1)");
        addChild(new HeadingTag(HeadingLevel.H2).plainText("Chat"));
        //Div which will display the messages for now add some dummy messages
        addChild(new DivTag().css("display", "grid").css("grid-template-columns", "1fr").css("grid-gap", "10px")
                .addChild(messages
                        .addAttribute(GlobalAttributes.ID, "messages")
                        .css(" *", "max-width", "100%").css(" *", "margin", "2px")
                        .event(EventTypes.BEFORELOAD, (session, htmlElement) -> {
                            for (int i = 0; i < Main.messages.size(); i++) {
                                String message = Main.messages.get(i);
                                boolean isLastMessage = i == Main.messages.size() - 1;
                                String username = message.substring(0, message.indexOf(": "));
                                String messageText = message.substring(message.indexOf(": ") + 2);
                                Tag messageTag = new DivTag()
                                        .addChild(new BoldTag().plainText(username + ": "))
                                        .addChild(new ParagraphTag().plainText(messageText));
                                if (isLastMessage) {
                                    messageTag.addAttribute(GlobalAttributes.ID, "lastMessage");
                                }
                                session.addChild(htmlElement, messageTag);
                            }
                        })
                        .event(EventTypes.LOAD, (session, htmlElement) -> {
                            session.scrollTo("lastMessage");
                        })
                )
                .addChild(new DivTag().css("display", "grid").css("grid-template-columns", "1fr 1fr").css("grid-gap", "10px").css("align-items", "center")
                        .addChild(new LabelTag().plainText("Message").addAttribute(FOR, "message")
                                .css(":hover", "text-decoration", "underline"))
                        .addChild(new InputTag().addAttribute(TYPE, "text").addAttribute(NAME, "message").addAttribute(ID, "message")
                            .css("margin-bottom", "10px")
                            .event(EventTypes.KEYDOWN, (session, key) -> {
                                if (!key.eventValue().equalsIgnoreCase("enter")) {
                                    return;
                                }
                                chat(session);
                            })
                        )
                        .addChild(new LabelTag().plainText("File").addAttribute(FOR, "file")
                                .css(":hover", "text-decoration", "underline"))
                        .addChild(new InputTag().addAttribute(TYPE, "file").addAttribute(NAME, "file").addAttribute(ID, "file"))
                        .addChild(new ParagraphTag().plainText("Uploading progress: ").addClassName("hidden").addAttribute(ID, "progressName"))
                        .addChild(new ParagraphTag().plainText("0%").addClassName("hidden").addAttribute(ID, "progress"))
                )
        );
        addChild(new ButtonTag().plainText("Send").event(EventTypes.CLICK, (session, htmlElement) -> chat(session)).css("margin-top", "10px"));
    }

    private void chat(Session sourceSession) {
        if (webPageBuilder.getActiveSessions() == null) {
            return;
        }
        sourceSession.getHtmlElement("message", message -> {
            sourceSession.getFile("file", (fileProgress, callBackFile) -> {
                sourceSession.setInnerHtml("progress", fileProgress.getPercentage());
                sourceSession.show("progressName");
                sourceSession.show("progress");
                if (!fileProgress.isComplete()) {
                    return;
                }
                sourceSession.hide("progressName");
                sourceSession.hide("progress");
                File file = new File(callBackFile.name());
                try (FileOutputStream writer = new FileOutputStream(file)) {
                    writer.write(callBackFile.data());
                    writer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                sourceSession.setValue("message", "");
                String username = sourceSession.getCookie("username");
                if (username == null) {
                    return;
                }
                Main.messages.add(username + ": " + message.value());
                for (Session session : webPageBuilder.getActiveSessions()) {
                    session.getHtmlElement("messages", messages -> {
                        session.hasLastChild(messages, exists -> {
                            if (exists.value().equals("true")) {
                                session.getLastChild(messages, lastMessageElement -> {
                                    session.removeAttribute(lastMessageElement, GlobalAttributes.ID);
                                });
                            }
                            Tag messageTag = new DivTag()
                                    .addChild(new BoldTag().plainText(username + ": "))
                                    .addChild(new ParagraphTag().plainText(message.value()));
                            session.addChild(messages, messageTag.addAttribute(ID, "lastMessage"));
                            session.scrollTo("lastMessage");
                        });

                    });
                }
            });
        });
    }
}