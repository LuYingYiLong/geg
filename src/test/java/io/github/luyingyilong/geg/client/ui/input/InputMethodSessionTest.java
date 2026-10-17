package io.github.luyingyilong.geg.client.ui.input;

import java.util.ArrayList;
import java.util.List;

/** Platform-independent focus hand-off, stale callbacks and failed activation/cleanup. */
public final class InputMethodSessionTest {
    private InputMethodSessionTest() { }

    public static void main(String[] args) {
        List<String> events = new ArrayList<>();
        InputMethodSession<Object> session = new InputMethodSession<>();
        Object first = new Object();
        Object second = new Object();
        session.focus(first, () -> connection(events, "first"));
        session.focus(first, () -> { throw new AssertionError("Focus must not activate twice"); });
        session.update(first, true);
        check(session.composing(first) && !session.composing(second));
        check(session.preedit(first).equals(new InputPreedit("pinyin", 3)));
        check(session.preedit(second).equals(InputPreedit.EMPTY));
        check(session.inlinePreedit(first) && !session.inlinePreedit(second));
        session.focus(second, () -> connection(events, "second"));
        check(events.equals(List.of("first:open", "first:update", "first:cancel", "first:close", "second:open")));
        session.release(first);
        session.cancelComposition(first);
        check(session.owns(second));
        check(events.get(events.size() - 1).equals("second:open"));
        session.cancelComposition(second);
        check(events.get(events.size() - 1).equals("second:cancel"));
        session.locateFocus(first);
        session.locateFocus(second);
        check(events.get(events.size() - 1).equals("second:locate"));
        session.update(second, false);
        session.clear();
        check(events.get(events.size() - 1).equals("second:close"));
        check(!session.owns(second) && !session.composing(second));
        try {
            session.focus(first, () -> { throw new IllegalStateException("Unavailable IME"); });
            throw new AssertionError("Activation failure should propagate");
        } catch (IllegalStateException expected) {
            check(!session.owns(first));
        }
        session.focus(first, () -> new InputMethodSession.Connection() {
            @Override public void cancelComposition() { throw new IllegalStateException("Cancellation failed"); }
            @Override public void close() { events.add("failed:close"); }
        });
        try {
            session.release(first);
            throw new AssertionError("Cancellation failure should propagate");
        } catch (IllegalStateException expected) {
            check(!session.owns(first));
            check(events.get(events.size() - 1).equals("failed:close"));
        }
        session.clear();
    }

    private static InputMethodSession.Connection connection(List<String> events, String name) {
        events.add(name + ":open");
        return new InputMethodSession.Connection() {
            @Override public void update() { events.add(name + ":update"); }
            @Override public InputPreedit preedit() { return new InputPreedit("pinyin", 3); }
            @Override public void cancelComposition() { events.add(name + ":cancel"); }
            @Override public void locateFocus() { events.add(name + ":locate"); }
            @Override public void close() { events.add(name + ":close"); }
        };
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("IME focus state or native context was not restored");
    }
}
