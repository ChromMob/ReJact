package me.chrommob.kasper.components;

import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;

import java.util.function.Consumer;

import static me.chrommob.builder.html.constants.GlobalAttributes.*;

public class AdminComponent extends DivTag {

    public AdminComponent(Consumer<User> onUserAdded, Consumer<User> onUserRemoved) {
        super();
        addAttribute(ID, "admin");
        addClassName("hidden");
        event(EventTypes.BEFORELOAD, (session, htmlElement) -> {
            if (session.getCookie("username") == null) {
                session.redirect("/kasper/login");
                return;
            }
            if (session.getCookie("username").equals("admin")) {
                session.show("admin");
                return;
            }
            session.redirect("/kasper");
        });
        addChild(new LabelTag().plainText("Username").addAttribute(FOR, "username"));
        addChild(new InputTag().addAttribute(TYPE, "text").addAttribute(NAME, "username").addAttribute(ID, "username"));
        addChild(new LabelTag().plainText("Password").addAttribute(FOR, "password"));
        addChild(new InputTag().addAttribute(TYPE, "password").addAttribute(NAME, "password").addAttribute(ID, "password"));
        addChild(new ButtonTag().plainText("Add").event(EventTypes.CLICK, (session, htmlElement) -> session.getHtmlElement("username", usernameElement -> session.getHtmlElement("password", passwordElement -> {
            String username = usernameElement.value();
            String password = passwordElement.value();
            onUserAdded.accept(new User(username, password));
        }))));
        addChild(new ButtonTag().plainText("Remove").event(EventTypes.CLICK, (session, htmlElement) -> session.getHtmlElement("username", usernameElement -> {
            String username = usernameElement.value();
            onUserRemoved.accept(new User(username, null));
        })));
    }

    public record User(String username, String password) {}
}
