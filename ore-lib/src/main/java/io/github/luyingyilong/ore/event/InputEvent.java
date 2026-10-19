package io.github.luyingyilong.ore.event;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.window.WindowContext;

import java.util.Objects;

/**
 * Immutable host payload with a Window-owned, single-dispatch routing envelope.
 */
public abstract class InputEvent {
    private final WindowContext context;
    private final int modifiers;
    private Control target, currentTarget, capture;
    private boolean accepted, dispatched;
    private int localX, localY, treeX, treeY;

    protected InputEvent(WindowContext context, int modifiers) {
        this.context = Objects.requireNonNull(context);
        this.modifiers = modifiers;
    }

    public final WindowContext context() {
        return context;
    }

    public final int modifiers() {
        return modifiers;
    }

    public final Control target() {
        return target;
    }

    public final Control currentTarget() {
        return currentTarget;
    }

    public final int localX() {
        return localX;
    }

    public final int localY() {
        return localY;
    }

    /**
     * Internal unscrolled tree coordinates for geometry shared with layout.
     */
    public final int treeX() {
        return treeX;
    }

    public final int treeY() {
        return treeY;
    }

    public final boolean accepted() {
        return accepted;
    }

    public final boolean dispatched() {
        return dispatched;
    }

    public final void accept() {
        accepted = true;
    }

    public final void capturePointer() {
        capture = Objects.requireNonNull(currentTarget);
    }

    public final Control captureRequest() {
        return capture;
    }

    public final void begin(Control target) {
        if (dispatched) throw new IllegalStateException("InputEvent already dispatched");
        dispatched = true;
        this.target = target;
    }

    public final void deliver(Control control, int x, int y) {
        currentTarget = control;
        treeX = x;
        treeY = y;
        var bounds = control.getAbsoluteBounds();
        localX = x - bounds.x();
        localY = y - bounds.y();
        control.input(this);
    }
}
