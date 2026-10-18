package io.github.luyingyilong.geg.client.ui.window;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.lwjgl.glfw.GLFWNativeWin32;

/** Separate HIMC: IMBlocker's primary-window state must not affect the secondary window. */
final class NativeImeContext implements AutoCloseable {
    private interface Imm extends StdCallLibrary {
        Imm API = Native.load("imm32", Imm.class, W32APIOptions.DEFAULT_OPTIONS);
        Pointer ImmCreateContext();
        boolean ImmDestroyContext(Pointer context);
        Pointer ImmGetContext(WinDef.HWND window);
        boolean ImmReleaseContext(WinDef.HWND window, Pointer context);
        Pointer ImmAssociateContext(WinDef.HWND window, Pointer context);
        boolean ImmGetOpenStatus(Pointer context);
        boolean ImmSetOpenStatus(Pointer context, boolean open);
    }
    private final WinDef.HWND window;
    private final Pointer previous, owned;
    private boolean closed;
    NativeImeContext(long handle) {
        window = new WinDef.HWND(new Pointer(GLFWNativeWin32.glfwGetWin32Window(handle)));
        previous = Imm.API.ImmGetContext(window);
        boolean open = previous != null && Imm.API.ImmGetOpenStatus(previous);
        if (previous != null) Imm.API.ImmReleaseContext(window, previous);
        owned = Imm.API.ImmCreateContext();
        if (owned == null) throw new IllegalStateException("Windows could not create an independent IME context");
        Imm.API.ImmAssociateContext(window, owned); Imm.API.ImmSetOpenStatus(owned, open);
    }
    @Override public void close() {
        if (closed) return; closed = true;
        Imm.API.ImmAssociateContext(window, previous); Imm.API.ImmDestroyContext(owned);
    }
}
