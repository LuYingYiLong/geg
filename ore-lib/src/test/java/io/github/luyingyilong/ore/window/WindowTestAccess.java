package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.control.overlay.Overlay;

/** Observes the installed overlay without changing the public Window API. */
public final class WindowTestAccess {
    private WindowTestAccess() { }
    public static Overlay overlay(Window window) { return window.overlayLayer(); }
}
