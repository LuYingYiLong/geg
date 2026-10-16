package io.github.luyingyilong.geg.client.ui;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;

import java.util.ArrayList;
import java.util.List;

/** Focus hand-off, stale callbacks, failed activation and Windows IMM ABI checks. */
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
        if (Platform.isWindows()) windowsContextIsRestored();
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

    private static void windowsContextIsRestored() {
        // A hidden, disposable native window avoids changing the user's game or active input field.
        var window = User32.INSTANCE.CreateWindowEx(0, "STATIC", "Ore IME test", 0,
                0, 0, 100, 100, null, null, null, null);
        check(window != null);
        try {
            Pointer original = WindowsInputMethod.Imm.API.ImmGetContext(window);
            if (original != null) WindowsInputMethod.Imm.API.ImmReleaseContext(window, original);
            WindowsInputMethod connection = new WindowsInputMethod(null, window);
            check(new WindowsInputMethod.CandidateForm().size() == 32);
            check(new WindowsInputMethod.CompositionForm().size() == 28);
            check(!connection.preedit().active());
            Pointer associated = WindowsInputMethod.Imm.API.ImmGetContext(window);
            check(associated != null);
            WindowsInputMethod.CandidateForm candidate = new WindowsInputMethod.CandidateForm();
            WindowsInputMethod.CompositionForm composition = new WindowsInputMethod.CompositionForm();
            boolean candidateCaptured = associated != null && WindowsInputMethod.Imm.API.ImmGetCandidateWindow(associated, 0, candidate);
            boolean compositionCaptured = associated != null && WindowsInputMethod.Imm.API.ImmGetCompositionWindow(associated, composition);
            boolean initialOpen = associated != null && WindowsInputMethod.Imm.API.ImmGetOpenStatus(associated);
            try {
                if (associated != null) {
                    WindowsInputMethod.CandidateForm movedCandidate = new WindowsInputMethod.CandidateForm();
                    movedCandidate.style = 0x0040;
                    movedCandidate.position.x = 57;
                    movedCandidate.position.y = 61;
                    WindowsInputMethod.Imm.API.ImmSetCandidateWindow(associated, movedCandidate);
                    WindowsInputMethod.CompositionForm movedComposition = new WindowsInputMethod.CompositionForm();
                    movedComposition.style = 0x0002;
                    movedComposition.position.x = 41;
                    movedComposition.position.y = 43;
                    WindowsInputMethod.Imm.API.ImmSetCompositionWindow(associated, movedComposition);
                    WindowsInputMethod.Imm.API.ImmSetOpenStatus(associated, !initialOpen);
                }
            } finally {
                if (associated != null) WindowsInputMethod.Imm.API.ImmReleaseContext(window, associated);
            }
            connection.cancelComposition();
            connection.close();
            connection.close();
            Pointer restored = WindowsInputMethod.Imm.API.ImmGetContext(window);
            try {
                check(java.util.Objects.equals(original, restored));
                if (restored != null && java.util.Objects.equals(associated, restored)) {
                    check(WindowsInputMethod.Imm.API.ImmGetOpenStatus(restored) == initialOpen);
                    if (candidateCaptured) {
                        WindowsInputMethod.CandidateForm actual = new WindowsInputMethod.CandidateForm();
                        check(WindowsInputMethod.Imm.API.ImmGetCandidateWindow(restored, 0, actual));
                        check(java.util.Arrays.equals(candidate.getPointer().getByteArray(0, candidate.size()),
                                actual.getPointer().getByteArray(0, actual.size())));
                    }
                    if (compositionCaptured) {
                        WindowsInputMethod.CompositionForm actual = new WindowsInputMethod.CompositionForm();
                        check(WindowsInputMethod.Imm.API.ImmGetCompositionWindow(restored, actual));
                        check(java.util.Arrays.equals(composition.getPointer().getByteArray(0, composition.size()),
                                actual.getPointer().getByteArray(0, actual.size())));
                    }
                }
            }
            finally { if (restored != null) WindowsInputMethod.Imm.API.ImmReleaseContext(window, restored); }
        } finally {
            User32.INSTANCE.DestroyWindow(window);
        }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("IME focus state or native context was not restored");
    }
}
