package me.chrommob.builder.socket;

import com.google.gson.Gson;
import me.chrommob.builder.html.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class SessionData {
    private final String internalCookie;

    public SessionData(String internalCookie) {
        this.internalCookie = internalCookie;
    }

    public String getInternalCookie() {
        return internalCookie;
    }

    public abstract void setCookie(String key, String value);

    public abstract void removeCookie(String key);

    public abstract String getCookie(String cookie);

    public abstract void clearCookies();

    public static class DefaultImpl extends SessionData {
        private final Map<String, String> cookies = new HashMap<>();

        public DefaultImpl(String internalCookie) {
            super(internalCookie);
        }

        @Override
        public void setCookie(String key, String value) {
            cookies.put(key, value);
        }

        @Override
        public void removeCookie(String key) {
            cookies.remove(key);
        }

        @Override
        public String getCookie(String cookie) {
            return cookies.get(cookie);
        }

        @Override
        public void clearCookies() {
            cookies.clear();
        }
    }

    public static class DefaultFileImpl extends SessionData {
        private final File path = new File("internal/sessions");
        private final Gson gson = new Gson();

        public DefaultFileImpl(String internalCookie) {
            super(internalCookie);
            File file = new File(path, internalCookie + ".json");
            if (!file.exists()) {
                //Write empty map to file
                try {
                    file.createNewFile();
                    FileWriter writer = new FileWriter(file);
                    writer.write(gson.toJson(new HashMap<>()));
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void setCookie(String key, String value) {
            Map<String, String> cookies = gson.fromJson(FileUtils.readFileToString(new File(path, getInternalCookie() + ".json")), Map.class);
            cookies.put(key, value);
            try {
                FileWriter writer = new FileWriter(new File(path, getInternalCookie() + ".json"));
                writer.write(gson.toJson(cookies));
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void removeCookie(String key) {
            Map<String, String> cookies = gson.fromJson(FileUtils.readFileToString(new File(path, getInternalCookie() + ".json")), Map.class);
            cookies.remove(key);
            try {
                FileWriter writer = new FileWriter(new File(path, getInternalCookie() + ".json"));
                writer.write(gson.toJson(cookies));
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public String getCookie(String cookie) {
            Map<String, String> cookies = gson.fromJson(FileUtils.readFileToString(new File(path, getInternalCookie() + ".json")), Map.class);
            return cookies.get(cookie);
        }

        @Override
        public void clearCookies() {
            File file = new File(path, getInternalCookie() + ".json");
            try {
                FileWriter writer = new FileWriter(file);
                writer.write(gson.toJson(new HashMap<>()));
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
