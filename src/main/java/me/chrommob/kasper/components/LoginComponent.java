package me.chrommob.kasper.components;

import jakarta.xml.bind.DatatypeConverter;
import me.chrommob.builder.html.constants.HeadingLevel;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;
import me.chrommob.builder.socket.Session;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static me.chrommob.builder.html.constants.GlobalAttributes.*;

public class LoginComponent extends DivTag {
    private final Map<String, String> usernamePasswordHashMap;
    public LoginComponent(Map<String, String> usernamePasswordHashMap) {
        super();
        this.usernamePasswordHashMap = usernamePasswordHashMap;
        css("background-color", "#fff").css("padding", "20px").css("border-radius", "5px")
                .css("box-shadow", "0 0 10px rgba(0, 0, 0, 0.1)");
        addChild(new HeadingTag(HeadingLevel.H2).plainText("Login"));
        addChild(new DivTag().css("display", "grid").css("grid-template-columns", "1fr 1fr").css("grid-gap", "10px")
                .addChild(new LabelTag().plainText("Username").addAttribute(FOR, "username")
                        .css(":hover", "text-decoration", "underline"))
                .addChild(new InputTag().addAttribute(TYPE, "text").addAttribute(NAME, "username").addAttribute(ID, "username")
                        .css("margin-bottom", "10px")
                )
                .addChild(new LabelTag().plainText("Password").addAttribute(FOR, "password")
                        .css(":hover", "text-decoration", "underline"))
                .addChild(new InputTag().addAttribute(TYPE, "password").addAttribute(NAME, "password").addAttribute(ID, "password")
                        .css("margin-bottom", "10px")
                        .event(EventTypes.KEYDOWN, (session, key) -> {
                            if (!key.eventValue().equalsIgnoreCase("enter")) {
                                return;
                            }
                            session.getHtmlElement("username", usernameElement -> {
                                String username = usernameElement.value();
                                String password = key.value();
                                login(session, username, password);
                            });
                        })
                )
                .addChild(new ButtonTag().plainText("Login")
                        .event(EventTypes.CLICK, (session, htmlElement) -> session.getHtmlElement("username", usernameElement -> session.getHtmlElement("password", passwordElement -> {
                            String username = usernameElement.value();
                            String password = passwordElement.value();
                            login(session, username, password);
                        })))
                )
                .addChild(new ParagraphTag().addAttribute(ID, "error").plainText("Wrong username or password").css("color", "red").addClassName("hidden"))
        );
    }

    private void login(Session session, String username, String password) {
        if (username == null) {
            return;
        }
        if (password == null) {
            return;
        }
        Map<String, String> usernamePasswordHashMap = new HashMap<>(this.usernamePasswordHashMap);
        if (!usernamePasswordHashMap.containsKey(username)) {
            wrongCredentials(session);
            return;
        }
        String passwordHash = usernamePasswordHashMap.get(username);
        try {
            String hash = DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-256").digest(password.getBytes()));
            if (!hash.equals(passwordHash)) {
                wrongCredentials(session);
                return;
            }
            session.setCookie("username", username);
            session.redirect("/kasper");
        } catch (NoSuchAlgorithmException e) {
            wrongCredentials(session);
        }
    }

    private void wrongCredentials(Session session) {
        session.show("error");
        session.setValue("username", "");
        session.setValue("password", "");
    }
}
