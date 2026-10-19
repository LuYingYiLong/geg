package io.github.luyingyilong.ore.event;

import io.github.luyingyilong.ore.window.WindowContext;

public final class KeyEvent extends InputEvent {
    private final Action action;
    private final int key, scan;
    public KeyEvent(WindowContext context, Action action, int key, int scan, int modifiers) {
        super(context, modifiers);
        this.action = action;
        this.key = key;
        this.scan = scan;
    }

    public Action action() {
        return action;
    }

    public int keyCode() {
        return key;
    }

    public int scanCode() {
        return scan;
    }

    public enum Action {PRESS, RELEASE, REPEAT}
}
