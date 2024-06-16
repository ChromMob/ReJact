package me.chrommob.kasper.components;

import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;
import java.util.Set;
import java.util.function.Consumer;

import static me.chrommob.builder.html.constants.GlobalAttributes.*;

public class AdminComponent extends DivTag {
    private final Set<String> users;
    private final Consumer<User> onUserAdded;
    private final Consumer<User> onUserRemoved;
    public AdminComponent(Consumer<User> onUserAdded, Consumer<User> onUserRemoved, Set<String> users) {
        super();
        this.users = users;
        this.onUserAdded = onUserAdded;
        this.onUserRemoved = onUserRemoved;
        addAttribute(ID, "admin");
        addClassName("hidden");
        css("display", "grid").css("grid-template-columns", "1fr 1fr").css("grid-gap", "10px");
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
        addChild(new UserAdderComponent());
        addChild(new UserListComponent());
    }

    public record User(String username, String password) {}

    class UserAdderComponent extends DivTag {
        public UserAdderComponent() {
            super();
            addChild(new LabelTag().plainText("Username").addAttribute(FOR, "username"));
            addChild(new InputTag().addAttribute(TYPE, "text").addAttribute(NAME, "username").addAttribute(ID, "username"));
            addChild(new LabelTag().plainText("Password").addAttribute(FOR, "password"));
            addChild(new InputTag().addAttribute(TYPE, "password").addAttribute(NAME, "password").addAttribute(ID, "password"));
            addChild(new ButtonTag().plainText("Add").event(EventTypes.CLICK, (session, htmlElement) -> session.getHtmlElement("username", usernameElement -> session.getHtmlElement("password", passwordElement -> {
                String username = usernameElement.value();
                String password = passwordElement.value();
                onUserAdded.accept(new User(username, password));
                session.getHtmlElement("users", users -> session.addChild(users, new UserListComponent.UserComponent(username, onUserRemoved)));
            }))));
        }
    }

    class UserListComponent extends ULTag {
        public UserListComponent() {
            super();
            addAttribute(ID, "users");
            css("default", "display", "grid").css("grid-template-columns", "1fr 1fr").css("grid-gap", "10px");
            event(EventTypes.BEFORELOAD, (session, htmlElement) -> {
                for (String user : users) {
                    session.addChild(htmlElement, new UserComponent(user, onUserRemoved));
                }
            });
        }

        public static class UserComponent extends DivTag {
            public UserComponent(String user, Consumer<User> onUserRemoved) {
                super();
                addAttribute(ID, "username-" + user);
                addChild(new BoldTag().plainText(user));
                addChild(new ButtonTag().plainText("Remove").event(EventTypes.CLICK, (session, htmlElement) -> {
                    onUserRemoved.accept(new User(user, null));
                    session.removeElementById("username-" + user);
                }));
            }
        }
    }
}
