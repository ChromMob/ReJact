package me.chrommob.builder;

import io.javalin.Javalin;
import jakarta.xml.bind.DatatypeConverter;
import me.chrommob.builder.html.events.EventTypes;
import me.chrommob.builder.html.tags.Tag;
import me.chrommob.builder.socket.Session;
import me.chrommob.builder.socket.WebSocketImpl;
import org.java_websocket.WebSocket;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WebPageBuilder {
    private final String ip;
    private final int clientPort;
    private final Javalin app;
    private final Map<String, WebSocket> webSockets = new ConcurrentHashMap<>();
    private final Map<WebSocket, Session> sessions = new ConcurrentHashMap<>();
    private final Map<EventTypes, List<Tag>> eventMap = new HashMap<>();
    private final Map<EventTypes, List<Tag>> fileEventMap = new HashMap<>();

    public WebPageBuilder(String ip, int webPort, int serverPort, int clientPort) {
        this.ip = ip;
        this.clientPort = clientPort;

        WebSocketImpl webSocket = new WebSocketImpl(serverPort, this);

        SSLContext context = getContext();

        if (context != null) {
            webSocket.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(getContext()));
        }

        webSocket.setConnectionLostTimeout(30);

        webSocket.start();

        app = Javalin.create().start(webPort);
        try (FileInputStream fis = new FileInputStream("favicon.ico")) {
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);
            app.get("/favicon.ico", ctx -> ctx.contentType("image/x-icon").result(buffer));
        } catch (Exception e) {
            e.printStackTrace();
        }

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (Session session : sessions.values()) {
                    if (session.getCloseTime() != 0) {
                        if (System.currentTimeMillis() - session.getCloseTime() > 10000 && session.getWebSocket().isClosed()) {
                            webSockets.remove(session.getInternalCookie());
                            sessions.remove(session.getWebSocket());
                        }
                    }
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String line = null;
                try {
                    line = reader.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (line != null) {
                    if (line.equals("exit")) {
                        webSocket.getConnections().forEach(ws -> ws.close());
                        try {
                            webSocket.stop();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        app.stop();
                        System.exit(0);
                    }
                }
            }
        }).start();
    }

    private static SSLContext getContext() {
        SSLContext context;
        String password = "CHANGEIT";
        String pathname = "/etc/letsencrypt/live/wss.chrommob.fun";
        try {
            context = SSLContext.getInstance("TLS");

            byte[] certBytes = parseDERFromPEM(getBytes(new File(pathname + File.separator + "cert.pem")),
                    "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
            byte[] keyBytes = parseDERFromPEM(
                    getBytes(new File(pathname + File.separator + "privkey.pem")),
                    "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----");

            X509Certificate cert = generateCertificateFromDER(certBytes);
            RSAPrivateKey key = generatePrivateKeyFromDER(keyBytes);

            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(null);
            keystore.setCertificateEntry("cert-alias", cert);
            keystore.setKeyEntry("key-alias", key, password.toCharArray(), new Certificate[]{cert});

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keystore, password.toCharArray());

            KeyManager[] km = kmf.getKeyManagers();

            context.init(km, null, null);
        } catch (Exception e) {
            context = null;
        }
        return context;
    }

    private static byte[] parseDERFromPEM(byte[] pem, String beginDelimiter, String endDelimiter) {
        String data = new String(pem);
        String[] tokens = data.split(beginDelimiter);
        tokens = tokens[1].split(endDelimiter);
        return DatatypeConverter.parseBase64Binary(tokens[0]);
    }

    private static RSAPrivateKey generatePrivateKeyFromDER(byte[] keyBytes)
            throws InvalidKeySpecException, NoSuchAlgorithmException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

        KeyFactory factory = KeyFactory.getInstance("RSA");

        return (RSAPrivateKey) factory.generatePrivate(spec);
    }

    private static X509Certificate generateCertificateFromDER(byte[] certBytes)
            throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");

        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    private static byte[] getBytes(File file) {
        byte[] bytesArray = new byte[(int) file.length()];

        FileInputStream fis;
        try {
            fis = new FileInputStream(file);
            fis.read(bytesArray); //read file into bytes[]
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytesArray;
    }



    public Page newPage(String path) {
        Page page = new Page(path, ip, clientPort, eventMap, fileEventMap);
        app.get(path, ctx -> ctx.html(page.getHtmlString()));
        return page;
    }

    public Session getSession(WebSocket conn, String[] cookie) {
        if (cookie != null) {
            for (String c : cookie) {
                WebSocket webSocket = webSockets.get(c);
                if (webSocket != null) {
                    sessions.put(conn, sessions.get(webSocket));
                    sessions.remove(webSocket);
                    webSockets.put(c, conn);
                }
            }
        }
        Session session = sessions.get(conn);
        if (session == null) {
            session = new Session(eventMap, fileEventMap, conn);
            sessions.put(conn, session);
            webSockets.put(session.getInternalCookie(), conn);
        } else {
            session.setWebSocket(conn);
        }
        return session;
    }

    public List<Session> getActiveSessions() {
        return this.sessions.values().stream().filter(session -> session.getWebSocket().isOpen()).collect(Collectors.toList());
    }
}
