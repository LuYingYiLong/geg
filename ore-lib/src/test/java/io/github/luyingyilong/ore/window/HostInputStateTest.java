package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.event.CancelEvent;
import io.github.luyingyilong.ore.event.KeyEvent;
import io.github.luyingyilong.ore.event.WindowInputs;
import org.lwjgl.glfw.GLFW;

/** Both host kinds share idempotent suspension, geometry cancellation and key pairing. */
public final class HostInputStateTest {
    public static void main(String[] args) {
        for (boolean nativeHost : new boolean[]{false, true}) {
            focus(nativeHost);
            geometry(nativeHost);
            keys(nativeHost);
        }
        isolation();
        repeatedText();
        System.out.println("ORE_HOST_INPUT_STATE_PASS");
    }

    private static Window window(boolean nativeHost) {
        return nativeHost ? Window.nativeWindow(WindowOptions.defaults("Host state test")) : Window.embedded();
    }

    private static void viewport(Window window, boolean drawable, int width, boolean fullscreen) {
        window.hostInputState().updateViewport(drawable, width, 120, width * 2, 240, 2, fullscreen);
    }

    private static void focus(boolean nativeHost) {
        Window root = window(nativeHost);
        Control target = new Control().setFocusable(true).setMinimum(20, 20);
        root.setContent(target); root.layoutViewport(200, 120, null);
        root.getFocusManager().requestFocus(target, null);
        viewport(root, true, 200, false);
        try {
            root.hostInputState().setFocused(false);
            check(!root.hostInputState().isActive(), "Host becomes inactive");
            // InputControl's MUI blur clears logical focus through its focusExited signal.
            root.getFocusManager().requestFocus(null, null);
            root.hostInputState().setFocused(false);
            viewport(root, false, 0, false);
            root.hostInputState().setFocused(true);
            check(!root.hostInputState().isActive() && root.getFocusManager().getFocused() == null,
                    "Focus gain while minimized cannot restore input yet");
            viewport(root, true, 240, false);
            check(root.hostInputState().isActive() && root.getFocusManager().getFocused() == target,
                    "Repeated loss and minimization retain the first restore target");
            root.hostInputState().setFocused(false);
            root.getFocusManager().requestFocus(null, null);
            target.queueFree(); LifecycleScheduler.flush();
            root.hostInputState().setFocused(true);
            check(root.getFocusManager().getFocused() == null, "A disposed field is never restored");
        } finally { root.queueFree(); LifecycleScheduler.flush(); }
    }

    private static void geometry(boolean nativeHost) {
        Window root = window(nativeHost);
        Button button = new Button(); int[] clicks = {0};
        button.pressed().connect(root, () -> clicks[0]++);
        root.setContent(button); root.layoutViewport(200, 120, null);
        viewport(root, true, 200, false);
        try {
            WindowInputs.press(root, 5, 5, null);
            check(root.getPointerCaptured(0), "Press establishes capture");
            viewport(root, true, 200, false);
            check(root.getPointerCaptured(0), "An unchanged viewport does not interrupt a gesture");
            // A fullscreen mode transition must invalidate capture even when dimensions match.
            viewport(root, true, 200, true);
            check(!root.getPointerCaptured(0) && !root.getContext().mouseDown(0), "Fullscreen cancels capture and held state");
            WindowInputs.move(root, 6, 5); WindowInputs.release(root, 6, 5);
            check(clicks[0] == 0 && root.getFocusManager().getFocused() == button,
                    "Old releases cannot activate and logical focus remains");
            WindowInputs.press(root, 5, 5, null);
            viewport(root, true, 240, true);
            WindowInputs.release(root, 5, 5);
            check(clicks[0] == 0, "Resize also cancels the old coordinate-space gesture");
            WindowInputs.press(root, 5, 5, null); WindowInputs.release(root, 5, 5);
            check(clicks[0] == 1, "A fresh press/release works after the transition");
        } finally { root.queueFree(); LifecycleScheduler.flush(); }
    }

    private static void keys(boolean nativeHost) {
        Window root = window(nativeHost);
        Button button = new Button(); int[] clicks = {0};
        button.pressed().connect(root, () -> clicks[0]++);
        root.setContent(button); root.layoutViewport(200, 120, null);
        viewport(root, true, 200, false);
        root.getFocusManager().requestFocus(button, null);
        try {
            for (int key : new int[]{GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE}) {
                WindowInputs.keyDown(root, key, 0, null);
                root.hostInputState().setFocused(false);
                WindowInputs.keyDown(root, key, 0, null);
                root.hostInputState().setFocused(true);
                WindowInputs.key(root, KeyEvent.Action.REPEAT, key, 0, GLFW.GLFW_MOD_CONTROL, null);
                check(!root.getContext().keyDown(key) && root.getContext().getModifiers() == 0,
                        "Orphan repeats do not restore held keys or stale modifiers");
                WindowInputs.keyUp(root, key, 0, null);
                check(clicks[0] == 0, "Loss/repeat/release cannot reactivate a button");
                WindowInputs.keyDown(root, key, 0, null);
                root.dispatchInput(new CancelEvent(root.getContext(), CancelEvent.Reason.LOST_FOCUS));
                WindowInputs.key(root, KeyEvent.Action.REPEAT, key, 0, 0, null);
                WindowInputs.keyUp(root, key, 0, null);
                check(clicks[0] == 0, "Explicit cancellation also invalidates repeat/release pairing");
            }
            WindowInputs.keyDown(root, GLFW.GLFW_KEY_ENTER, 0, null);
            WindowInputs.key(root, KeyEvent.Action.REPEAT, GLFW.GLFW_KEY_ENTER, 0, 0, null);
            WindowInputs.keyUp(root, GLFW.GLFW_KEY_ENTER, 0, null);
            check(clicks[0] == 1, "A new press with repeats completes exactly once");
            WindowInputs.keyDown(root, GLFW.GLFW_KEY_ENTER, 0, null);
            viewport(root, true, 240, false);
            WindowInputs.key(root, KeyEvent.Action.REPEAT, GLFW.GLFW_KEY_ENTER, 0, 0, null);
            WindowInputs.keyUp(root, GLFW.GLFW_KEY_ENTER, 0, null);
            check(clicks[0] == 1, "Geometry cancellation invalidates keyboard activation too");
        } finally { root.queueFree(); LifecycleScheduler.flush(); }
    }

    private static void isolation() {
        Window first = window(false), second = window(true);
        first.setContent(new Button()); second.setContent(new Button());
        first.layoutViewport(100, 60, null); second.layoutViewport(100, 60, null);
        viewport(first, true, 200, false); viewport(second, true, 200, false);
        try {
            WindowInputs.press(first, 5, 5, null); WindowInputs.press(second, 5, 5, null);
            first.hostInputState().setFocused(false);
            check(!first.getPointerCaptured(0) && second.getPointerCaptured(0) && second.getContext().mouseDown(0),
                    "One host's suspension never cancels another host's gesture");
        } finally { first.queueFree(); second.queueFree(); LifecycleScheduler.flush(); }
    }

    private static void repeatedText() {
        for (boolean nativeHost : new boolean[]{false, true}) {
            Window root = window(nativeHost);
            int[] typed = {0};
            Control field = new Control() {
                @Override public void input(io.github.luyingyilong.ore.event.InputEvent event) {
                    if (event instanceof io.github.luyingyilong.ore.event.TextInputEvent) { typed[0]++; event.accept(); }
                }
            }.setFocusable(true);
            root.setContent(field); root.layoutViewport(200, 120, null);
            root.getFocusManager().requestFocus(field, null);
            try {
                WindowInputs.keyDown(root, GLFW.GLFW_KEY_A, 0, null);
                root.hostInputState().setFocused(false); root.hostInputState().setFocused(true);
                WindowInputs.key(root, KeyEvent.Action.REPEAT, GLFW.GLFW_KEY_A, 0, 0, null);
                WindowInputs.text(root, "a", 0, null);
                check(typed[0] == 0, "The character callback following an orphan repeat cannot edit a draft");
                WindowInputs.keyUp(root, GLFW.GLFW_KEY_A, 0, null);
                WindowInputs.keyDown(root, GLFW.GLFW_KEY_A, 0, null);
                WindowInputs.text(root, "a", 0, null);
                check(typed[0] == 1, "A fresh key permits text input again");
            } finally { root.queueFree(); LifecycleScheduler.flush(); }
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
