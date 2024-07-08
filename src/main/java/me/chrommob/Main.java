package me.chrommob;

import me.chrommob.builder.WebPageBuilder;
import me.chrommob.config.ConfigKey;
import me.chrommob.config.ConfigManager;
import me.chrommob.config.ConfigWrapper;
import me.chrommob.demo.DraggableExamplePage;
import me.chrommob.test.ChatPage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        ConfigManager configManager = new ConfigManager(new File("./"));
        File configFile = new File("./config.yml");
        List<ConfigKey> configKeys = new ArrayList<>();
        configKeys.add(new ConfigKey("web-port", "8080", List.of("The port the web server will run on")));
        configKeys.add(new ConfigKey("websocket-server-ip", "wss.domain.com", List.of("The ip of the websocket server")));
        configKeys.add(new ConfigKey("websocket-server-port", "9090", List.of("The port of the websocket server")));
        configKeys.add(new ConfigKey("websocket-client-port", "9090", List.of("The port the client expects the websocket server to run on")));
        configKeys.add(new ConfigKey("ssl-certificate-path", "/etc/letsencrypt/live/wss.domain.com/", List.of("The path to the ssl certificate for the websocket server")));
        configKeys.add(new ConfigKey("ssl-certificate-password", "CHANGEIT", List.of("The password for the ssl certificate")));
        ConfigWrapper configWrapper = new ConfigWrapper("config", configKeys);
        configManager.addConfig(configWrapper);
        if (!configFile.exists()) {
            System.out.println("Config file not found, creating one");
            System.exit(0);
        }
        int port = configWrapper.getKey("web-port").getAsInt();
        String ip = configWrapper.getKey("websocket-server-ip").getAsString();
        int serverPort = configWrapper.getKey("websocket-server-port").getAsInt();
        int clientPort = configWrapper.getKey("websocket-client-port").getAsInt();
        String certificatePath = configWrapper.getKey("ssl-certificate-path").getAsString();
        String password = configWrapper.getKey("ssl-certificate-password").getAsString();
        new Main().buildPage(ip, port, serverPort, clientPort, certificatePath, password);
    }

    public void buildPage(String ip, int port, int serverPort, int clientPort, String certificatePath, String password) {
        WebPageBuilder builder = new WebPageBuilder(ip, port, serverPort, clientPort, certificatePath, password);
        new DraggableExamplePage(builder);
        new ChatPage(builder);
        builder.start();
    }
}
