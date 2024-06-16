package me.chrommob.kasper;

import jakarta.xml.bind.DatatypeConverter;
import me.chrommob.builder.Page;
import me.chrommob.builder.WebPageBuilder;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.*;
import me.chrommob.kasper.components.AdminComponent;
import me.chrommob.kasper.components.LoginComponent;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static me.chrommob.builder.html.constants.GlobalAttributes.*;

public class KasperPage {
    private final Map<String, String> usernamePasswordHashMap = new HashMap<>();
    private final File usersFile = new File("kasper/users.txt");
    private final WebPageBuilder builder;
    private final File dataFolder = new File("kasper");
    public KasperPage(WebPageBuilder builder) {
        this.builder = builder;
        dataFolder.mkdirs();
        fillUsernamePasswordHashMap();
        build();
    }

    private void fillUsernamePasswordHashMap() {
        usernamePasswordHashMap.put("admin", "BACE040EFD88FB232DD4559E4BFB4AEB501497961AABBB79763529B50A23C127");
        try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] split = line.split(" ");
                usernamePasswordHashMap.put(split[0], split[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void build() {
        Page homePage = builder.newPage("/kasper");
        homePage.root().addChild(new HtmlTag().addAttribute(LANG, "en")
                .addChild(new HeadTag().addChild(new TitleTag().plainText("ReJact"))
                        .addChild(new MetaTag().addAttribute(CHARSET, "utf-8"))
                        .addChild(new MetaTag().addAttribute(NAME, "viewport").addAttribute(CONTENT, "width=device-width, initial-scale=1"))
                )
                .addChild(new BodyTag()
                )
        );

        Page adminPage = builder.newPage("/kasper/admin");
        adminPage.root().addChild(new HtmlTag().addAttribute(LANG, "en")
                .addChild(new HeadTag().addChild(new TitleTag().plainText("ReJact"))
                        .addChild(new MetaTag().addAttribute(CHARSET, "utf-8"))
                        .addChild(new MetaTag().addAttribute(NAME, "viewport").addAttribute(CONTENT, "width=device-width, initial-scale=1"))
                )
                .addChild(new BodyTag()
                        .addChild(new AdminComponent(user -> addUser(user.username(), user.password()), user -> removeUser(user.username())))
                )
        );

        Page loginPage = builder.newPage("/kasper/login");
        loginPage.root().addChild(new HtmlTag().event(EventTypes.BEFORELOAD, (session, htmlElement) -> {
            String username = session.getCookie("username");
            String password = session.getCookie("password");
            if (username == null || password == null) {
                return;
            }
            session.redirect("/kasper");
        })
                .addAttribute(LANG, "en")
                .addChild(new HeadTag().addChild(new TitleTag().plainText("Login"))
                        .addChild(new MetaTag().addAttribute(CHARSET, "utf-8"))
                        .addChild(new MetaTag().addAttribute(NAME, "viewport").addAttribute(CONTENT, "width=device-width, initial-scale=1"))
                )
                .addChild(new BodyTag()
                        .css("font-family", "Arial, sans-serif").css("display", "flex").css("justify-content", "center").css("align-items", "center")
                        .css("height", "100vh").css("margin", "0").css("background-color", "#f4f4f4")
                        .addChild(new LoginComponent(usernamePasswordHashMap))
                )
        );
        homePage.build();
        adminPage.build();
        loginPage.build();
    }

    private void addUser(String username, String password) {
        try {
            password = DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-256").digest(password.getBytes()));
        } catch (NoSuchAlgorithmException ignored) {
        }
        usernamePasswordHashMap.put(username, password);
        try {
            usersFile.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try (FileWriter writer = new FileWriter(usersFile)) {
            for (Map.Entry<String, String> entry : usernamePasswordHashMap.entrySet()) {
                if (entry.getValue().equals("admin")) {
                    continue;
                }
                writer.write(entry.getKey() + " " + entry.getValue() + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void removeUser(String username) {
        usernamePasswordHashMap.remove(username);
        try {
            usersFile.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try (FileWriter writer = new FileWriter(usersFile)) {
            for (Map.Entry<String, String> entry : usernamePasswordHashMap.entrySet()) {
                if (entry.getValue().equals("admin")) {
                    continue;
                }
                writer.write(entry.getKey() + " " + entry.getValue() + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
