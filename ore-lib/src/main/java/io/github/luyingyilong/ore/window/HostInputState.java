package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.input.OreInputMethod;
import io.github.luyingyilong.ore.signal.DispatchGuard;

import java.lang.ref.WeakReference;

/** Shared activation and viewport transitions for embedded, borrowed and native hosts. */
final class HostInputState {
    private final Window window;
    private WeakReference<Control> retained = new WeakReference<>(null);
    private boolean focused = true, drawable = true, active = true, measured;
    private int width, height, framebufferWidth, framebufferHeight;
    private double scale;
    private boolean fullscreen;

    HostInputState(Window window) {
        this.window = window;
    }

    boolean isActive() { return active; }

    void setFocused(boolean focused) {
        this.focused = focused;
        synchronize();
    }

    void updateViewport(boolean drawable, int width, int height, int framebufferWidth, int framebufferHeight,
                        double scale, boolean fullscreen) {
        update(focused, drawable, width, height, framebufferWidth, framebufferHeight, scale, fullscreen);
    }

    void update(boolean focused, boolean drawable, int width, int height, int framebufferWidth, int framebufferHeight,
                double scale, boolean fullscreen) {
        boolean changed = measured && (this.width != width || this.height != height
                || this.framebufferWidth != framebufferWidth || this.framebufferHeight != framebufferHeight
                || this.scale != scale || this.fullscreen != fullscreen);
        this.focused = focused;
        this.drawable = drawable;
        if (drawable) {
            measured = true;
            this.width = width;
            this.height = height;
            this.framebufferWidth = framebufferWidth;
            this.framebufferHeight = framebufferHeight;
            this.scale = scale;
            this.fullscreen = fullscreen;
        }
        boolean wasActive = active;
        synchronize();
        if (wasActive && active && changed) window.cancelHostGestures();
    }

    private void synchronize() {
        boolean next = focused && drawable;
        if (next == active || !window.isAlive()) return;
        active = next;
        try (var phase = DispatchGuard.enter()) {
            window.getContext().setFocused(next);
            if (!next) {
                Control target = window.getFocusManager().getFocused();
                retained = new WeakReference<>(target);
                // MUI's ordinary blur resets its caret and commits NumberInput expressions.
                if (target instanceof InputControl input) input.suspendHostFocus();
                window.cancelInteractions();
                var mui = window.getContext().getMui();
                if (mui != null && mui.isFocused()) mui.removeFocus();
                OreInputMethod.releaseWindow(window.getContext());
            } else {
                Control target = retained.get();
                retained.clear();
                if (target != null && target.isAlive())
                    window.getFocusManager().restoreFocus(target, window.getContext().getMui());
            }
        }
    }

    void clear() {
        retained.clear();
    }
}
