package io.github.luyingyilong.geg.client.ui;

import com.sun.jna.Native;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.nio.charset.StandardCharsets;

/** Uses Windows' existing IME context; never changes the player's keyboard layout or language. */
final class WindowsInputMethod implements InputMethodSession.Connection {
    private final Input input;
    private final WinDef.HWND window;
    private final Pointer previous;
    private final boolean previousOpen;
    private final CandidateForm previousCandidate = new CandidateForm();
    private final CompositionForm previousComposition = new CompositionForm();
    private final boolean candidateCaptured;
    private final boolean compositionCaptured;
    private final boolean initialOpen;
    private boolean closed;

    WindowsInputMethod(Input input) {
        this(input, currentWindow());
    }

    WindowsInputMethod(Input input, WinDef.HWND window) {
        this.input = input;
        this.window = window;
        previous = Imm.API.ImmGetContext(window);
        previousOpen = previous != null && Imm.API.ImmGetOpenStatus(previous);
        if (previous != null) Imm.API.ImmReleaseContext(window, previous);
        // IACE_DEFAULT associates the window's default context, allowing OS language shortcuts.
        if (!Imm.API.ImmAssociateContextEx(window, null, 0x0010))
            throw new IllegalStateException("Windows could not associate the input method context");
        Pointer context = Imm.API.ImmGetContext(window);
        try {
            candidateCaptured = context != null && Imm.API.ImmGetCandidateWindow(context, 0, previousCandidate);
            compositionCaptured = context != null && Imm.API.ImmGetCompositionWindow(context, previousComposition);
            initialOpen = context != null && Imm.API.ImmGetOpenStatus(context);
        } finally {
            if (context != null) Imm.API.ImmReleaseContext(window, context);
        }
    }

    private static WinDef.HWND currentWindow() {
        long handle = GLFWNativeWin32.glfwGetWin32Window(Minecraft.getInstance().getWindow().getWindow());
        if (handle == 0L) throw new IllegalStateException("Missing native game window");
        return new WinDef.HWND(new Pointer(handle));
    }

    @Override public void update() {
        Pointer context = Imm.API.ImmGetContext(window);
        if (context == null) return;
        try {
            var caret = input.inputMethodCaret();
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            int x = (int) Math.round(caret.x() * scale);
            int y = (int) Math.round(caret.y() * scale);
            CandidateForm candidate = new CandidateForm();
            candidate.style = 0x0040; // CFS_CANDIDATEPOS
            candidate.position.x = x;
            candidate.position.y = y;
            Imm.API.ImmSetCandidateWindow(context, candidate);
            CompositionForm composition = new CompositionForm();
            composition.style = 0x0002; // CFS_POINT
            composition.position.x = x;
            composition.position.y = y;
            Imm.API.ImmSetCompositionWindow(context, composition);
        } finally {
            Imm.API.ImmReleaseContext(window, context);
        }
    }

    static InputPreedit currentPreedit() { return readPreedit(currentWindow()); }

    private static InputPreedit readPreedit(WinDef.HWND window) {
        Pointer context = Imm.API.ImmGetContext(window);
        if (context == null) return InputPreedit.EMPTY;
        try {
            int bytes = Imm.API.ImmGetCompositionStringW(context, 0x0008, null, 0); // GCS_COMPSTR
            if (bytes <= 0 || bytes > InputPreedit.MAX_LENGTH * 2 || (bytes & 1) != 0)
                return InputPreedit.EMPTY;
            try (Memory memory = new Memory(bytes)) {
                int copied = Imm.API.ImmGetCompositionStringW(context, 0x0008, memory, bytes);
                if (copied <= 0 || copied > bytes || (copied & 1) != 0) return InputPreedit.EMPTY;
                String text = new String(memory.getByteArray(0, copied), StandardCharsets.UTF_16LE);
                int cursor = Imm.API.ImmGetCompositionStringW(context, 0x0080, null, 0); // GCS_CURSORPOS
                return new InputPreedit(text, cursor < 0 ? text.length() : cursor);
            }
        } finally {
            Imm.API.ImmReleaseContext(window, context);
        }
    }

    @Override public InputPreedit preedit() { return closed ? InputPreedit.EMPTY : readPreedit(window); }

    static void cancelCurrentComposition() { cancelComposition(currentWindow()); }

    private static void cancelComposition(WinDef.HWND window) {
        Pointer context = Imm.API.ImmGetContext(window);
        if (context == null) return;
        try {
            Imm.API.ImmNotifyIME(context, 0x0015, 0x0004, 0); // NI_COMPOSITIONSTR, CPS_CANCEL
            Imm.API.ImmNotifyIME(context, 0x0011, 0, 0); // NI_CLOSECANDIDATE
        } finally {
            Imm.API.ImmReleaseContext(window, context);
        }
    }

    @Override public void cancelComposition() { if (!closed) cancelComposition(window); }

    @Override public void close() {
        if (closed) return;
        try {
            cancelComposition();
        } finally {
            closed = true;
            Pointer context = Imm.API.ImmGetContext(window);
            try {
                if (context != null) {
                    if (candidateCaptured) Imm.API.ImmSetCandidateWindow(context, previousCandidate);
                    if (compositionCaptured) Imm.API.ImmSetCompositionWindow(context, previousComposition);
                    Imm.API.ImmSetOpenStatus(context, initialOpen);
                }
            } finally {
                if (context != null) Imm.API.ImmReleaseContext(window, context);
                Imm.API.ImmAssociateContext(window, previous);
                if (previous != null) Imm.API.ImmSetOpenStatus(previous, previousOpen);
            }
        }
    }

    public interface Imm extends StdCallLibrary {
        Imm API = Native.load("imm32", Imm.class, W32APIOptions.DEFAULT_OPTIONS);
        Pointer ImmGetContext(WinDef.HWND window);
        boolean ImmReleaseContext(WinDef.HWND window, Pointer context);
        boolean ImmAssociateContextEx(WinDef.HWND window, Pointer context, int flags);
        Pointer ImmAssociateContext(WinDef.HWND window, Pointer context);
        boolean ImmSetCandidateWindow(Pointer context, CandidateForm form);
        boolean ImmSetCompositionWindow(Pointer context, CompositionForm form);
        boolean ImmGetCandidateWindow(Pointer context, int index, CandidateForm form);
        boolean ImmGetCompositionWindow(Pointer context, CompositionForm form);
        boolean ImmGetOpenStatus(Pointer context);
        boolean ImmSetOpenStatus(Pointer context, boolean open);
        boolean ImmNotifyIME(Pointer context, int action, int index, int value);
        int ImmGetCompositionStringW(Pointer context, int index, Pointer buffer, int length);
    }

    @Structure.FieldOrder({"index", "style", "position", "area"})
    public static class CandidateForm extends Structure {
        public int index;
        public int style;
        public WinDef.POINT position = new WinDef.POINT();
        public WinDef.RECT area = new WinDef.RECT();
    }

    @Structure.FieldOrder({"style", "position", "area"})
    public static class CompositionForm extends Structure {
        public int style;
        public WinDef.POINT position = new WinDef.POINT();
        public WinDef.RECT area = new WinDef.RECT();
    }
}
