package io.github.luyingyilong.ore.event;

import io.github.luyingyilong.ore.window.WindowContext;

public final class PointerEvent extends InputEvent {
    private final Action action;
    private final int x, y, button;
    private final boolean over;
    public PointerEvent(WindowContext context, Action action, int x, int y, int button, int modifiers) {
        this(context, action, x, y, button, modifiers, true);
    }

    public PointerEvent(WindowContext context, Action action, int x, int y, int button, int modifiers, boolean over) {
        super(context, modifiers);
        this.action = action;
        this.x = x;
        this.y = y;
        this.button = button;
        this.over = over;
    }

    public Action action() {
        return action;
    }

    public int windowX() {
        return x;
    }

    public int windowY() {
        return y;
    }

    public int button() {
        return button;
    }

    public boolean over() {
        return over && EventGeometry.contains(currentTarget(), x, y);
    }

    public enum Action {PRESS, RELEASE, MOVE}
}
