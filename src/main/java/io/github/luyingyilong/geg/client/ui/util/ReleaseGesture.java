package io.github.luyingyilong.geg.client.ui.util;

import java.util.Objects;

/** Remembers the original logical target, never retargeting a held mouse button or key. */
public final class ReleaseGesture<T> {
    private T pointerTarget;
    private T keyTarget;
    private int key = -1;

    public void pointerDown(T target) {
        if (pointerTarget == null) pointerTarget = Objects.requireNonNull(target);
    }

    public Release<T> pointerUp(T current, boolean allowed) {
        T original = pointerTarget;
        pointerTarget = null;
        return new Release<>(original != null,
                allowed && original != null && Objects.equals(original, current) ? original : null);
    }

    public void keyDown(int keyCode, T target) {
        if (key != -1) return;
        key = keyCode;
        keyTarget = Objects.requireNonNull(target);
    }

    public Release<T> keyUp(int keyCode, T current) {
        if (key != keyCode) return new Release<>(false, null);
        T original = keyTarget;
        key = -1;
        keyTarget = null;
        return new Release<>(true, Objects.equals(original, current) ? original : null);
    }

    public boolean pointerPending() { return pointerTarget != null; }
    public boolean pointerPressed(T target) { return Objects.equals(pointerTarget, target); }
    public boolean keyPressed(T target) { return Objects.equals(keyTarget, target); }
    public void cancel() { pointerTarget = null; keyTarget = null; key = -1; }

    public record Release<T>(boolean handled, T target) { }
}
