package io.github.luyingyilong.ore.input;

import com.sun.jna.Memory;
import com.sun.jna.Native;
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
    private final Imm imm;
    private final Pointer previous;
    private final boolean previousOpen;
    private final CandidateForm previousCandidate = new CandidateForm();
    private final CompositionForm previousComposition = new CompositionForm();
    private final boolean candidateCaptured;
    private final boolean compositionCaptured;
    private final boolean initialOpen;
    private boolean closed;
    private final boolean nativeWindow;

    WindowsInputMethod(Input input) {
        this(input, new WinDef.HWND(new Pointer(GLFWNativeWin32.glfwGetWin32Window(input.windowContext().handle()))));
    }

    WindowsInputMethod(Input input, WinDef.HWND window) {
        this(input, window, Imm.API);
    }

    /** Injectable IMM boundary keeps state restoration testable without a desktop input context. */
    WindowsInputMethod(Input input, WinDef.HWND window, Imm imm) {
        this.input = input;
        nativeWindow = input != null && input.windowContext().nativeWindow();
        this.window = window;
        this.imm = imm;
        previous = imm.ImmGetContext(window);
        previousOpen = previous != null && imm.ImmGetOpenStatus(previous);
        if (previous != null) imm.ImmReleaseContext(window, previous);
        // IACE_DEFAULT associates the window's default context, allowing OS language shortcuts.
        if (!nativeWindow && !imm.ImmAssociateContextEx(window, null, 0x0010))
            throw new IllegalStateException("Windows could not associate the input method context");
        Pointer context = imm.ImmGetContext(window);
        try {
            candidateCaptured = context != null && imm.ImmGetCandidateWindow(context, 0, previousCandidate);
            compositionCaptured = context != null && imm.ImmGetCompositionWindow(context, previousComposition);
            initialOpen = context != null && imm.ImmGetOpenStatus(context);
        } finally {
            if (context != null) imm.ImmReleaseContext(window, context);
        }
    }

    private static WinDef.HWND currentWindow() {
        long handle = GLFWNativeWin32.glfwGetWin32Window(Minecraft.getInstance().getWindow().getWindow());
        if (handle == 0L) throw new IllegalStateException("Missing native game window");
        return new WinDef.HWND(new Pointer(handle));
    }

    @Override public void update() {
        Pointer context = imm.ImmGetContext(window);
        if (context == null) return;
        try {
            var caret = input.inputMethodCaret();
            var host = input.windowContext();
            int x = (int) Math.round(host.nativeWindow() ? host.clientX(caret.x()) : caret.x() * host.guiScale());
            int y = (int) Math.round(host.nativeWindow() ? host.clientY(caret.y()) : caret.y() * host.guiScale());
            CandidateForm candidate = new CandidateForm();
            candidate.style = 0x0040; // CFS_CANDIDATEPOS
            candidate.position.x = x;
            candidate.position.y = y;
            imm.ImmSetCandidateWindow(context, candidate);
            CompositionForm composition = new CompositionForm();
            composition.style = 0x0002; // CFS_POINT
            composition.position.x = x;
            composition.position.y = y;
            imm.ImmSetCompositionWindow(context, composition);
        } finally {
            imm.ImmReleaseContext(window, context);
        }
    }

    static InputPreedit currentPreedit() { return readPreedit(currentWindow(), Imm.API); }

    private static InputPreedit readPreedit(WinDef.HWND window, Imm imm) {
        Pointer context = imm.ImmGetContext(window);
        if (context == null) return InputPreedit.EMPTY;
        try {
            int bytes = imm.ImmGetCompositionStringW(context, 0x0008, null, 0); // GCS_COMPSTR
            if (bytes <= 0 || bytes > InputPreedit.MAX_LENGTH * 2 || (bytes & 1) != 0)
                return InputPreedit.EMPTY;
            try (Memory memory = new Memory(bytes)) {
                int copied = imm.ImmGetCompositionStringW(context, 0x0008, memory, bytes);
                if (copied <= 0 || copied > bytes || (copied & 1) != 0) return InputPreedit.EMPTY;
                String text = new String(memory.getByteArray(0, copied), StandardCharsets.UTF_16LE);
                int cursor = imm.ImmGetCompositionStringW(context, 0x0080, null, 0); // GCS_CURSORPOS
                return new InputPreedit(text, cursor < 0 ? text.length() : cursor);
            }
        } finally {
            imm.ImmReleaseContext(window, context);
        }
    }

    @Override public InputPreedit preedit() { return closed ? InputPreedit.EMPTY : readPreedit(window, imm); }

    static void cancelCurrentComposition() { cancelComposition(currentWindow(), Imm.API); }

    private static void cancelComposition(WinDef.HWND window, Imm imm) {
        Pointer context = imm.ImmGetContext(window);
        if (context == null) return;
        try {
            imm.ImmNotifyIME(context, 0x0015, 0x0004, 0); // NI_COMPOSITIONSTR, CPS_CANCEL
            imm.ImmNotifyIME(context, 0x0011, 0, 0); // NI_CLOSECANDIDATE
        } finally {
            imm.ImmReleaseContext(window, context);
        }
    }

    @Override public void cancelComposition() { if (!closed) cancelComposition(window, imm); }

    @Override public void close() {
        if (closed) return;
        try {
            cancelComposition();
        } finally {
            closed = true;
            Pointer context = imm.ImmGetContext(window);
            try {
                if (context != null) {
                    if (candidateCaptured) imm.ImmSetCandidateWindow(context, previousCandidate);
                    if (compositionCaptured) imm.ImmSetCompositionWindow(context, previousComposition);
                    if (!nativeWindow) imm.ImmSetOpenStatus(context, initialOpen);
                }
            } finally {
                if (context != null) imm.ImmReleaseContext(window, context);
                imm.ImmAssociateContext(window, previous);
                if (previous != null && !nativeWindow) imm.ImmSetOpenStatus(previous, previousOpen);
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
