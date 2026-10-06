package me.chrommob.rejact;

/** Server-side event handler. Receives the per-connection {@link Ui} and the typed payload. */
@FunctionalInterface
public interface Handler<E> {
    void handle(Ui ui, E event);
}
