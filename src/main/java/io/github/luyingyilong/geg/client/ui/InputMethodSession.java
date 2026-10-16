package io.github.luyingyilong.geg.client.ui;

import java.util.function.Supplier;

/** One native text-input owner; stale blur callbacks must not release a newer owner. */
final class InputMethodSession<T> {
    interface Connection extends AutoCloseable {
        default void update() { }
        default InputPreedit preedit() { return InputPreedit.EMPTY; }
        default boolean composing() { return preedit().active(); }
        default boolean inlinePreedit() { return true; }
        default void cancelComposition() { }
        default void locateFocus() { }
        @Override void close();
    }

    private T owner;
    private Connection connection;

    boolean owns(T candidate) { return owner == candidate && connection != null; }

    void focus(T candidate, Supplier<Connection> factory) {
        if (owns(candidate)) return;
        clear();
        Connection opened = factory.get();
        connection = opened;
        owner = candidate;
    }

    void update(T candidate, boolean valid) {
        if (!owns(candidate)) return;
        if (!valid) release(candidate);
        else connection.update();
    }

    boolean composing(T candidate) { return owns(candidate) && connection.composing(); }

    InputPreedit preedit(T candidate) { return owns(candidate) ? connection.preedit() : InputPreedit.EMPTY; }
    boolean inlinePreedit(T candidate) { return owns(candidate) && connection.inlinePreedit(); }
    void cancelComposition(T candidate) { if (owns(candidate)) connection.cancelComposition(); }

    void locateFocus(T candidate) { if (owns(candidate)) connection.locateFocus(); }

    void release(T candidate) { if (owns(candidate)) clear(); }

    void clear() {
        Connection previous = connection;
        connection = null;
        owner = null;
        if (previous != null) {
            try { previous.cancelComposition(); }
            finally { previous.close(); }
        }
    }
}
