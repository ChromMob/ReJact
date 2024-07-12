package me.chrommob.builder.socket;

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
}
