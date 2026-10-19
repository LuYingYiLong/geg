package io.github.luyingyilong.ore.input;

import io.github.luyingyilong.ore.render.DrawContext;
import brachy.modularui.widget.sizer.Unit;
import io.github.luyingyilong.ore.Drawer;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.gui.Font;

/**
 * Ore layout proxy for a ModularUI text field. ModularUI retains focus and editing.
 */
public final class InputControl extends Control {
    private final Input widget;
    private Rect lastArea = new Rect(0, 0, 0, 0);

    public InputControl(Input widget) {
        this.widget = widget;
        getSignalScope().own(widget::releaseSession);
        widget.layoutControl(this);
        setMinimum(1, widget.getPreferredInputHeight());
        setFocusable(true);
        setFocusExpansion(2);
        widget.focusExited().connect(this, () -> {
            Control root = this;
            while (root.getParent() != null) root = root.getParent();
            if (root instanceof Window window)
                window.getFocusManager().nativeFocusLost(this);
        });
        widget.left(() -> getWidgetBounds().x(), Unit.Measure.PIXEL);
        widget.top(() -> getWidgetBounds().y(), Unit.Measure.PIXEL);
        widget.width(() -> Math.max(1, getBounds().width()), Unit.Measure.PIXEL);
        widget.height(() -> Math.max(1, getBounds().height()), Unit.Measure.PIXEL);
    }

    /**
     * The layout tree, rather than legacy widget constructor depths, owns the rendered layer.
     */
    public static float drawingDepth(Control node) {
        float depth = io.github.luyingyilong.ore.util.OreLayers.INPUT;
        for (Control parent = node.getParent(); parent != null; parent = parent.getParent())
            depth += parent.getContentDepth();
        return depth;
    }

    public static boolean pointerInteractive(Control target, int x, int y) {
        var context = target.getWindowContext();
        return context.getOwner() == null || context.getOwner().pointerOver(target, x - context.getOriginX(), y - context.getOriginY());
    }

    @Override
    protected boolean lifecycleReady() {
        return widget.isValid();
    }

    public Input getWidget() {
        return widget;
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return Math.max(getMinimumHeight(), widget.getPreferredInputHeight());
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        syncWidgetArea();
        widget.drawInTree(context.getWindowContext().getMui());
    }

    public Rect getDisplayedBounds() {
        return io.github.luyingyilong.ore.event.EventGeometry.displayed(this);
    }

    public Rect getWidgetBounds() {
        return io.github.luyingyilong.ore.event.EventGeometry.untransformed(this, getAbsoluteBounds())
                .translated(getWindowContext().getOriginX(), getWindowContext().getOriginY());
    }

    @Override
    public void input(io.github.luyingyilong.ore.event.InputEvent event) {
        if (event instanceof io.github.luyingyilong.ore.event.CancelEvent) {
            widget.cancelPreedit();
            super.input(event);
            return;
        }
        if (event instanceof io.github.luyingyilong.ore.event.PointerEvent pointer
                && pointer.action() == io.github.luyingyilong.ore.event.PointerEvent.Action.MOVE
                && !event.context().mouseDown(pointer.button())) return;
        syncWidgetArea();
        widget.receive(event);
        if (event.accepted() && event instanceof io.github.luyingyilong.ore.event.PointerEvent pointer
                && pointer.action() == io.github.luyingyilong.ore.event.PointerEvent.Action.PRESS)
            event.capturePointer();
    }

    @Override
    public void cancelInteraction() {
        widget.cancelPreedit();
        if (widget.isValid() && widget.isFocused()) widget.getContext().removeFocus();
        OreInputMethod.release(widget);
    }

    boolean layoutVisible() {
        for (Control node = this; node != null; node = node.getParent())
            if (!node.isAlive() || !(node.isAlive() && node.getVisible())) return false;
        return true;
    }

    boolean layoutInteractive() {
        for (Control node = this; node != null; node = node.getParent()) {
            if (node instanceof Drawer drawer && !drawer.isOpen()) return false;
            if (node instanceof Window window) return window.getFocusManager().allowsNativeInput(this);
        }
        return false;
    }

    float drawingDepth() {
        return drawingDepth(this);
    }

    boolean pointerInteractive(int x, int y) {
        return pointerInteractive(this, x, y);
    }

    /**
     * Share Ore viewport clips with the native field's drawing and pointer checks.
     */
    Rect drawingClip() {
        Rect clip = io.github.luyingyilong.ore.event.EventGeometry.clip(this);
        return clip == null ? null : clip.translated(getWindowContext().getOriginX(), getWindowContext().getOriginY());
    }

    @Override
    public void invalidateTextLayout() {
        widget.scheduleResize();
        super.invalidateTextLayout();
    }

    @Override
    public boolean acceptsFocus() {
        return super.acceptsFocus() && widget.inputVisible() && widget.isEnabled() && layoutInteractive();
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(localBounds, font);
            syncWidgetArea();
        }
    }

    void syncWidgetArea() {
        Rect current = getWidgetBounds();
        boolean changed = !current.equals(lastArea);
        lastArea = current;
        // Ore layout is authoritative now, not at MUI's next scheduled resize. In particular,
        // scrolling and moving a modal must update both absolute and relative coordinates.
        var area = widget.getArea();
        var parent = widget.isValid() ? widget.getParent() : null;
        int parentX = parent == null ? 0 : parent.getArea().x;
        int parentY = parent == null ? 0 : parent.getArea().y;
        area.x = current.x();
        area.y = current.y();
        area.rx = current.x() - parentX;
        area.ry = current.y() - parentY;
        area.width = Math.max(1, current.width());
        area.height = Math.max(1, current.height());
        if (changed) widget.scheduleResize();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        widget.setInputVisible(visible);
    }
}
