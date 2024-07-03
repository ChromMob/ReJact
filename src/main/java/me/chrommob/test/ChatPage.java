package me.chrommob.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import me.chrommob.builder.Page;
import me.chrommob.builder.WebPageBuilder;
import me.chrommob.builder.html.constants.GlobalAttributes;
import static me.chrommob.builder.html.constants.GlobalAttributes.CHARSET;
import static me.chrommob.builder.html.constants.GlobalAttributes.CONTENT;
import static me.chrommob.builder.html.constants.GlobalAttributes.FOR;
import static me.chrommob.builder.html.constants.GlobalAttributes.HREF;
import static me.chrommob.builder.html.constants.GlobalAttributes.ID;
import static me.chrommob.builder.html.constants.GlobalAttributes.LANG;
import static me.chrommob.builder.html.constants.GlobalAttributes.NAME;
import static me.chrommob.builder.html.constants.GlobalAttributes.TYPE;
import me.chrommob.builder.html.constants.HeadingLevel;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.ATag;
import me.chrommob.builder.html.tags.BodyTag;
import me.chrommob.builder.html.tags.BoldTag;
import me.chrommob.builder.html.tags.ButtonTag;
import me.chrommob.builder.html.tags.DivTag;
import me.chrommob.builder.html.tags.HeadTag;
import me.chrommob.builder.html.tags.HeadingTag;
import me.chrommob.builder.html.tags.HtmlTag;
import me.chrommob.builder.html.tags.ImageTag;
import me.chrommob.builder.html.tags.InputTag;
import me.chrommob.builder.html.tags.LabelTag;
import me.chrommob.builder.html.tags.MetaTag;
import me.chrommob.builder.html.tags.ParagraphTag;
import me.chrommob.builder.html.tags.Tag;
import me.chrommob.builder.html.tags.TitleTag;
import me.chrommob.builder.html.tags.ULTag;
import me.chrommob.builder.socket.Session;
import me.chrommob.builder.utils.ImageOptimiser;
import me.chrommob.builder.utils.Internal;

public class ChatPage {
    private final WebPageBuilder builder;
    public static final List<Message> messages = new ArrayList<>();

    public ChatPage(WebPageBuilder builder) {
        this.builder = builder;
        build();
    }

    public record Message(String username, String message, String imageData) {
    }

    private void build() {
        Page homePage = builder.newPage("/");
        homePage.root().addChild(new HtmlTag().addAttribute(LANG, "en")
                .addChild(new HeadTag().addChild(new TitleTag().plainText("Chat Demo"))
                        .addChild(new MetaTag().addAttribute(CHARSET, "utf-8"))
                        .addChild(new MetaTag().addAttribute(NAME, "viewport").addAttribute(CONTENT,
                                "width=device-width, initial-scale=1")))
                .addChild(new BodyTag()
                        .addChild(new Header())
                        .addChild(new Chat(builder))
                        .addChild(new HeadingTag(HeadingLevel.H2).plainText("Users")
                                .css("margin-left", "20px"))
                        .addChild(new DivTag().addAttribute(ID, "users")))
                .event(EventTypes.BEFORELOAD, (sourceSession, htmlElement) -> {
                    syncOnlineUsers(sourceSession, false);
                }).event(EventTypes.BEFOREUNLOAD, (session, htmlElement) -> {
                    syncOnlineUsers(session, true);
                }).event(EventTypes.TENSECONDTIMER, (session, htmlElement) -> {
                    syncOnlineUsers(session, false);
                }));
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
                        .addChild(new MetaTag().addAttribute(NAME, "viewport").addAttribute(CONTENT,
                                "width=device-width, initial-scale=1")))
                .addChild(new BodyTag()
                        .css("font-family", "Arial, sans-serif").css("display", "flex").css("justify-content", "center")
                        .css("align-items", "center")
                        .css("height", "100vh").css("margin", "0").css("background-color", "#f4f4f4")
                        .addChild(new LoginPage()
                                .addChild(new ButtonTag().plainText("Login")
                                        .event(EventTypes.CLICK, (session, htmlElement) -> session
                                                .getHtmlElement("username", htmlElement1 -> {
                                                    session.setCookie("username", htmlElement1.value());
                                                    session.redirect("/");
                                                }))))));
//        homePage.build();
        loginPage.build();
    }

    private void syncOnlineUsers(Session sourceSession, boolean ignoreCurrentUser) {
        String username = sourceSession.getCookie("username");
        if (username == null) {
            sourceSession.redirect("/login");
            return;
        }
        Tag sessionList = new ULTag().css("default", "display", "grid").css("grid-template-columns", "1fr 1fr")
                .css("grid-gap", "10px");
        for (Session sessions : builder.getActiveSessionsByPage("/")) {
            if (ignoreCurrentUser && sourceSession == sessions) {
                continue;
            }
            if (sessions.getCookie("username") == null) {
                continue;
            }
            sessionList.addChild(new ParagraphTag().plainText(sessions.getCookie("username")));
        }
        for (Session sessions : builder.getActiveSessionsByPage("/")) {
            sessions.getHtmlElement("users", users -> {
                sessions.setInnerHtml(users, sessionList);
            });
        }
    }

    static class Chat extends Tag {
        private final WebPageBuilder webPageBuilder;

        //
        public Chat(WebPageBuilder webPageBuilder) {
            super("div");
            this.webPageBuilder = webPageBuilder;

            Tag messages = new DivTag().css("overflow-y", "scroll").css("height", "600px")
                    .css("background-color", "#eee")
                    .css("overflow-x", "hidden").css("padding", "10px");
            messages.addDynamicDataHandler(v -> {
                for (int i = 0; i < ChatPage.messages.size(); i++) {
                    Message message = ChatPage.messages.get(i);
                    Tag messageTag = new ChatPage.MessageTag(message.username(), message.message(),
                            message.imageData());
                    boolean isLastMessage = i == ChatPage.messages.size() - 1;
                    if (isLastMessage) {
                        messageTag.addAttribute(GlobalAttributes.ID, "lastMessage");
                    }
                    messages.addDynamicChild(messageTag);
                }
            });
            css("background-color", "#fff").css("padding", "20px").css("border-radius", "5px")
                    .css("box-shadow", "0 0 10px rgba(0, 0, 0, 0.1)");
            addChild(new HeadingTag(HeadingLevel.H2).plainText("Chat"));
            // Div which will display the messages for now add some dummy messages
            addChild(new DivTag().css("display", "grid").css("grid-template-columns", "1fr").css("grid-gap", "10px")
                    .addChild(messages
                            .addAttribute(GlobalAttributes.ID, "messages")
                            .css(" *", "max-width", "100%").css(" *", "margin", "2px")
                            .event(EventTypes.LOAD, (session, htmlElement) -> {
                                session.scrollTo("lastMessage");
                            }))
                    .addChild(new DivTag().css("display", "grid").css("grid-template-columns", "1fr 1fr")
                            .css("grid-gap", "10px").css("align-items", "center")
                            .addChild(new LabelTag().plainText("Message").addAttribute(FOR, "message")
                                    .css(":hover", "text-decoration", "underline"))
                            .addChild(new InputTag().addAttribute(TYPE, "text").addAttribute(NAME, "message")
                                    .addAttribute(ID, "message")
                                    .css("margin-bottom", "10px")
                                    .event(EventTypes.KEYDOWN, (session, key) -> {
                                        if (!key.eventValue().equalsIgnoreCase("enter")) {
                                            return;
                                        }
                                        chat(session);
                                    }))
                            .addChild(new LabelTag().plainText("File").addAttribute(FOR, "file")
                                    .css(":hover", "text-decoration", "underline"))
                            .addChild(new InputTag().addAttribute(TYPE, "file").addAttribute(NAME, "file")
                                    .addAttribute(ID, "file"))
                            .addChild(new ParagraphTag().plainText("Uploading progress: ").addClassName("hidden")
                                    .addAttribute(ID, "progressName"))
                            .addChild(new ParagraphTag().plainText("0%").addClassName("hidden").addAttribute(ID,
                                    "progress"))));
            addChild(new ButtonTag().plainText("Send").event(EventTypes.CLICK, (session, htmlElement) -> chat(session))
                    .css("margin-top", "10px"));
        }

        private void chat(Session sourceSession) {
            if (webPageBuilder.getActiveSessionsByPage("/") == null) {
                return;
            }
            sourceSession.getFileInfo("file", fileData -> {
                if (fileData.exists() && Internal.isImage(fileData.type())
                        && !fileData.isBiggerThan(10, Internal.SIZE_UNITS.MB)) {
                    sourceSession.getFile("file", (fileProgress, callBackFile) -> {
                        sourceSession.setInnerHtml("progress", fileProgress.getPercentage());
                        sourceSession.show("progressName");
                        sourceSession.show("progress");
                        if (!fileProgress.isComplete()) {
                            return;
                        }
                        sourceSession.hide("progressName");
                        sourceSession.hide("progress");
                        postMessage(sourceSession, callBackFile.data(), fileData.type().contains("gif"));
                        sourceSession.clearValue("file");
                    });
                    return;
                }
                postMessage(sourceSession, null, false);
            });
        }

        private void postMessage(Session sourceSession, byte[] imageData, boolean isGif) {
            sourceSession.getHtmlElement("message", message -> {
                String messageValue = message.value();
                sourceSession.setValue("message", "");
                String username = sourceSession.getCookie("username");
                if (username == null) {
                    return;
                }
                MessageTag messageTag = new MessageTag(username, messageValue, imageData, isGif);
                messageTag.getImageLink().thenAccept(link -> {
                    if (link != null) {
                        ImageTag image = (ImageTag) messageTag.getChildrenByClass(ImageTag.class).stream().findFirst().orElse(null);
                        assert image != null;
                        image.setInternalImageUrl(link);
                    }

                    ChatPage.messages.add(new ChatPage.Message(username, messageValue, link));

                    for (Session session : webPageBuilder.getActiveSessionsByPage("/")) {
                        session.getHtmlElement("messages", messages -> {
                            session.hasLastChild(messages, exists -> {
                                if (exists.value().equals("true")) {
                                    session.getLastChild(messages, lastMessageElement -> {
                                        session.removeAttribute(lastMessageElement, GlobalAttributes.ID);
                                    });
                                }
                                session.addChild(messages, messageTag.addAttribute(ID, "lastMessage"));
                                session.scrollTo("lastMessage");
                            });
                        });
                    }
                });
            });
        }
    }

    public static class Header extends DivTag {
        public Header() {
            super();
            css("background-color", "#333").css("overflow", "hidden").css("display", "flex").css("align-items", "start")
                    .addChild(new HeaderButton(true, "/", "Chat Demo"))
                    .addChild(new HeaderButton(false, "/demo/", "Demo")
                            .event(EventTypes.CLICK, (session, htmlElement) -> {
                                session.redirect("/demo");
                            }))
                    .addChild(new HeaderButton(false, "/login", "Logout")
                            .event(EventTypes.CLICK, (session, htmlElement) -> session.clearCookies()));
        }

        public static class HeaderButton extends ATag {
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

    static class LoginPage extends Tag {
        public LoginPage() {
            super("div");
            css("background-color", "#fff").css("padding", "20px").css("border-radius", "5px")
                    .css("box-shadow", "0 0 10px rgba(0, 0, 0, 0.1)");
            addChild(new HeadingTag(HeadingLevel.H2).plainText("Login"));
            addChild(new DivTag().css("display", "grid").css("grid-template-columns", "1fr 1fr").css("grid-gap", "10px")
                    .addChild(new LabelTag().plainText("Username").addAttribute(FOR, "username")
                            .css(":hover", "text-decoration", "underline"))
                    .addChild(new InputTag().addAttribute(TYPE, "text").addAttribute(NAME, "username")
                            .addAttribute(ID, "username")
                            .css("margin-bottom", "10px")
                            .event(EventTypes.KEYDOWN, (session, key) -> {
                                if (!key.eventValue().equalsIgnoreCase("enter")) {
                                    return;
                                }
                                session.setCookie("username", key.value());
                                session.redirect("/");
                            })));
        }
    }

    public static class MessageTag extends DivTag {
        private CompletableFuture<String> imageLink;
        public MessageTag(String username, String message, byte[] imageData, boolean isGif) {
            super();
            addChild(new BoldTag().plainText(username + ": "));
            addChild(new ParagraphTag().plainText(message));
            if (imageData != null) {
                ImageTag image = new ImageTag();
                addChild(image);
                imageLink = CompletableFuture.supplyAsync(() -> ImageOptimiser.generateInternalUrl(imageData, isGif));
            }
        }

        public CompletableFuture<String> getImageLink() {
            if (imageLink == null) {
                return CompletableFuture.completedFuture(null);
            }
            return imageLink;
        }

        public MessageTag(String username, String message, String imageLink) {
            super();
            addChild(new BoldTag().plainText(username + ": "));
            addChild(new ParagraphTag().plainText(message));
            if (imageLink != null) {
                addChild(new ImageTag().setInternalImageUrl(imageLink));
            }
        }
    }
}
