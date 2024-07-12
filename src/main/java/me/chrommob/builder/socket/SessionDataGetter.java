package me.chrommob.builder.socket;

public interface SessionDataGetter {
    SessionData getData(String internalCookie);

    class DefaultInMemoryImpl implements SessionDataGetter {
        @Override
        public SessionData getData(String internalCookie) {
            return new SessionData.DefaultImpl(internalCookie);
        }
    }

    class DefaultFileImpl implements SessionDataGetter {
        @Override
        public SessionData getData(String internalCookie) {
            return new SessionData.DefaultFileImpl(internalCookie);
        }
    }
}
