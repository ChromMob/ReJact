package me.chrommob.builder.socket;

public interface SessionDataGetter {
    SessionData getData(String internalCookie);

    class DefaultImpl implements SessionDataGetter {
        @Override
        public SessionData getData(String internalCookie) {
            return new SessionData.DefaultImpl(internalCookie);
        }
    }
}
