package io.github.luyingyilong.ore.input;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.widget.sizer.Unit;
import io.github.luyingyilong.ore.Drawer;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.gui.Font;

/** Ore layout proxy for a ModularUI text field. ModularUI retains focus and editing. */
public final class InputControl extends Control {
    private final Input widget;
    private Rect lastArea = new Rect(0, 0, 0, 0);

    public InputControl(Input widget) {
        this.widget = widget;
        widget.layoutControl(this);
        minimum(1, widget.preferredInputHeight());
        focusable(true);
        focusExpansion(2);
        widget.focusLostHandler(() -> {
            Control root = this;
            while (root.parent() != null) root = root.parent();
            if (root instanceof Window window)
                window.focusManager().nativeFocusLost(this);
        });
        widget.left(() -> widgetBounds().x(), Unit.Measure.PIXEL);
        widget.top(() -> widgetBounds().y(), Unit.Measure.PIXEL);
        widget.width(() -> Math.max(1, bounds().width()), Unit.Measure.PIXEL);
        widget.height(() -> Math.max(1, bounds().height()), Unit.Measure.PIXEL);
    }

    public Input widget() { return widget; }
    @Override public int measureHeight(Font font, int available) {
        return Math.max(minimumHeight(), widget.preferredInputHeight());
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        syncWidgetArea();
        widget.drawInTree(context);
    }

    public Rect displayedBounds() {
        return io.github.luyingyilong.ore.event.EventGeometry.displayed(this);
    }

    public Rect widgetBounds() { return displayedBounds().translated(windowContext().originX(),windowContext().originY()); }

    @Override public void input(io.github.luyingyilong.ore.event.InputEvent event) {
        if(event instanceof io.github.luyingyilong.ore.event.CancelEvent) {
            widget.cancelPreedit();super.input(event);return;
        }
        if(event instanceof io.github.luyingyilong.ore.event.PointerEvent pointer
                && pointer.action()==io.github.luyingyilong.ore.event.PointerEvent.Action.MOVE
                && !event.context().mouseDown(pointer.button()))return;
        syncWidgetArea();
        widget.receive(event);
        if(event.accepted() && event instanceof io.github.luyingyilong.ore.event.PointerEvent pointer
                && pointer.action()==io.github.luyingyilong.ore.event.PointerEvent.Action.PRESS)event.capturePointer();
    }

    @Override protected void onDetached() {
        if(widget.isValid() && widget.isFocused())widget.getContext().removeFocus();
        OreInputMethod.release(widget);
    }

    boolean layoutVisible() {
        for (Control node = this; node != null; node = node.parent())
            if (!node.visible()) return false;
        return true;
    }

    boolean layoutInteractive() {
        for (Control node = this; node != null; node = node.parent()) {
            if (node instanceof Drawer drawer && !drawer.isOpen()) return false;
            if (node instanceof Window window) return window.focusManager().allowsNativeInput(this);
        }
        return false;
    }

    float drawingDepth() {
        return drawingDepth(this);
    }

    /** The layout tree, rather than legacy widget constructor depths, owns the rendered layer. */
    public static float drawingDepth(Control node) {
        float depth = io.github.luyingyilong.ore.util.OreLayers.INPUT;
        for (Control parent = node.parent(); parent != null; parent = parent.parent()) depth += parent.contentDepth();
        return depth;
    }

    boolean pointerInteractive(int x, int y) { return pointerInteractive(this, x, y); }

    public static boolean pointerInteractive(Control target, int x, int y) {
        var context=target.windowContext();
        return context.owner()==null || context.owner().pointerOver(target,x-context.originX(),y-context.originY());
    }

    /** Share Ore viewport clips with the native field's drawing and pointer checks. */
    Rect drawingClip() {
        Rect clip=io.github.luyingyilong.ore.event.EventGeometry.clip(this);
        return clip==null?null:clip.translated(windowContext().originX(),windowContext().originY());
    }

    @Override public void invalidateTextLayout() {
        widget.scheduleResize();
        super.invalidateTextLayout();
    }

    @Override public boolean acceptsFocus() {
        return super.acceptsFocus() && widget.inputVisible() && widget.isEnabled() && layoutInteractive();
    }

    @Override public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        syncWidgetArea();
    }

    void syncWidgetArea() {
        Rect current = widgetBounds();
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

    @Override public void visible(boolean visible) {
        super.visible(visible);
        widget.setInputVisible(visible);
    }
}
