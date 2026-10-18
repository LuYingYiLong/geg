package io.github.luyingyilong.ore.input;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Exercises the real adapter's restore paths without requiring an installed/active Windows IME. */
public final class WindowsInputMethodTest {
    public static void main(String[] args) {
        check(new WindowsInputMethod.CandidateForm().size() == 32, "CANDIDATEFORM ABI");
        check(new WindowsInputMethod.CompositionForm().size() == 28, "COMPOSITIONFORM ABI");
        Pointer context = Pointer.createConstant(11), previous = Pointer.createConstant(22);
        restored(context, context);
        restored(previous, context);
        restored(null, context);

        FakeImm unavailable = new FakeImm(null, null);
        WindowsInputMethod absent = new WindowsInputMethod(null, unavailable.window, unavailable);
        check(absent.preedit().equals(InputPreedit.EMPTY), "No default context is a valid unavailable-IME state");
        absent.cancelComposition();
        absent.close();
        absent.close();
        check(unavailable.associated == null && unavailable.borrowed == 0, "Missing context cleanup remains balanced");

        FakeImm rejected = new FakeImm(previous, context);
        rejected.associate = false;
        try {
            new WindowsInputMethod(null, rejected.window, rejected);
            throw new AssertionError("Failed association must be reported");
        } catch (IllegalStateException expected) {
            check(rejected.associated.equals(previous) && rejected.borrowed == 0, "Failed activation releases the previous context");
        }

        FakeImm failing = new FakeImm(previous, context);
        WindowsInputMethod connection = new WindowsInputMethod(null, failing.window, failing);
        failing.failCancel = true;
        try {
            connection.close();
            throw new AssertionError("Failed cancellation must propagate");
        } catch (IllegalStateException expected) {
            check(failing.associated.equals(previous) && failing.borrowed == 0, "Close restores association even when cancellation fails");
        }
        connection.close();
    }

    private static void restored(Pointer previous, Pointer defaults) {
        FakeImm imm = new FakeImm(previous, defaults);
        State initial = imm.states.get(defaults);
        int[] candidate = initial.candidate.clone(), composition = initial.composition.clone();
        boolean initialOpen = initial.open;
        boolean previousOpen = previous != null && imm.states.get(previous).open;
        WindowsInputMethod connection = new WindowsInputMethod(null, imm.window, imm);
        check(imm.associated.equals(defaults), "Focus restores the window's default context");
        check(connection.preedit().equals(new InputPreedit("zhong", 3)), "Read UTF-16 preedit and caret through the actual adapter");
        initial.candidate = new int[]{0, 0x40, 57, 61, 0, 0, 0, 0};
        initial.composition = new int[]{2, 41, 43, 0, 0, 0, 0};
        initial.open = !initialOpen;
        connection.cancelComposition();
        check(connection.preedit().equals(InputPreedit.EMPTY), "Cancel removes the composition");
        connection.close();
        int calls = imm.calls;
        connection.close();
        check(imm.calls == calls && !connection.preedit().active(), "Double close and reading after close do not access IMM");
        check(java.util.Objects.equals(imm.associated, previous), "Restore the originally associated context, including null");
        check(Arrays.equals(initial.candidate, candidate) && Arrays.equals(initial.composition, composition),
                "Restore candidate and composition positions");
        check(initial.open == initialOpen, "Restore the default context's original open status");
        if (previous != null) check(imm.states.get(previous).open == previousOpen, "Restore a distinct previous context's open status");
        check(imm.borrowed == 0, "Each acquired context has been released");
    }

    private static final class State {
        boolean open;
        String text = "zhong";
        int[] candidate = {0, 0x40, 11, 13, 0, 0, 0, 0};
        int[] composition = {2, 17, 19, 0, 0, 0, 0};
    }

    private static final class FakeImm implements WindowsInputMethod.Imm {
        final WinDef.HWND window = new WinDef.HWND(Pointer.createConstant(1));
        final Map<Pointer, State> states = new HashMap<>();
        final Pointer defaults;
        Pointer associated;
        int borrowed, calls;
        boolean associate = true, failCancel;

        FakeImm(Pointer previous, Pointer defaults) {
            this.defaults = defaults;
            associated = previous;
            if (defaults != null) states.put(defaults, new State());
            if (previous != null && !previous.equals(defaults)) {
                State state = new State();
                state.open = true;
                states.put(previous, state);
            }
        }

        @Override public Pointer ImmGetContext(WinDef.HWND window) {
            calls++;
            check(window.equals(this.window), "Use the injected test window");
            if (associated != null) borrowed++;
            return associated;
        }
        @Override public boolean ImmReleaseContext(WinDef.HWND window, Pointer context) {
            calls++;
            check(context != null && borrowed > 0, "Release an acquired context only");
            borrowed--;
            return true;
        }
        @Override public boolean ImmAssociateContextEx(WinDef.HWND window, Pointer context, int flags) {
            calls++;
            check(flags == 0x0010, "Use IACE_DEFAULT to restore, not create, a context");
            if (associate) associated = defaults;
            return associate;
        }
        @Override public Pointer ImmAssociateContext(WinDef.HWND window, Pointer context) {
            calls++;
            Pointer old = associated;
            associated = context;
            return old;
        }
        @Override public boolean ImmGetOpenStatus(Pointer context) { calls++; return states.get(context).open; }
        @Override public boolean ImmSetOpenStatus(Pointer context, boolean open) { calls++; states.get(context).open = open; return true; }
        @Override public boolean ImmGetCandidateWindow(Pointer context, int index, WindowsInputMethod.CandidateForm form) {
            calls++;
            int[] values = states.get(context).candidate;
            form.index = values[0]; form.style = values[1];
            form.position.x = values[2]; form.position.y = values[3];
            form.area.left = values[4]; form.area.top = values[5]; form.area.right = values[6]; form.area.bottom = values[7];
            return true;
        }
        @Override public boolean ImmSetCandidateWindow(Pointer context, WindowsInputMethod.CandidateForm form) {
            calls++;
            states.get(context).candidate = new int[]{form.index, form.style, form.position.x, form.position.y,
                    form.area.left, form.area.top, form.area.right, form.area.bottom};
            return true;
        }
        @Override public boolean ImmGetCompositionWindow(Pointer context, WindowsInputMethod.CompositionForm form) {
            calls++;
            int[] values = states.get(context).composition;
            form.style = values[0]; form.position.x = values[1]; form.position.y = values[2];
            form.area.left = values[3]; form.area.top = values[4]; form.area.right = values[5]; form.area.bottom = values[6];
            return true;
        }
        @Override public boolean ImmSetCompositionWindow(Pointer context, WindowsInputMethod.CompositionForm form) {
            calls++;
            states.get(context).composition = new int[]{form.style, form.position.x, form.position.y,
                    form.area.left, form.area.top, form.area.right, form.area.bottom};
            return true;
        }
        @Override public boolean ImmNotifyIME(Pointer context, int action, int index, int value) {
            calls++;
            if (failCancel) throw new IllegalStateException("Unavailable cancellation");
            if (action == 0x0015) states.get(context).text = "";
            return true;
        }
        @Override public int ImmGetCompositionStringW(Pointer context, int index, Pointer buffer, int length) {
            calls++;
            if (index == 0x0080) return 3;
            byte[] bytes = states.get(context).text.getBytes(StandardCharsets.UTF_16LE);
            if (buffer != null) buffer.write(0, bytes, 0, bytes.length);
            return bytes.length;
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
