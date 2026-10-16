package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.widget.sizer.Unit;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import io.github.luyingyilong.geg.client.ui.layout.Scroll;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Font;
import brachy.modularui.screen.viewport.ModularGuiContext;

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
        widget.tabNavigation(modifiers -> {
            Control root = this;
            while (root.parent() != null) root = root.parent();
            return root instanceof Window window
                    && window.keyPressed(GLFW.GLFW_KEY_TAB, modifiers, widget.getContext());
        });
        widget.mousePressHandler(() -> {
            Control root = this;
            while (root.parent() != null) root = root.parent();
            if (root instanceof Window window)
                window.focusManager().pointerPressed(this, widget.getContext());
        });
        widget.focusLostHandler(() -> {
            Control root = this;
            while (root.parent() != null) root = root.parent();
            if (root instanceof Window window)
                window.focusManager().nativeFocusLost(this);
        });
        widget.left(() -> displayedBounds().x(), Unit.Measure.PIXEL);
        widget.top(() -> displayedBounds().y(), Unit.Measure.PIXEL);
        widget.width(() -> Math.max(1, bounds().width()), Unit.Measure.PIXEL);
        widget.height(() -> Math.max(1, bounds().height()), Unit.Measure.PIXEL);
    }

    public Input widget() { return widget; }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        widget.drawInTree(context);
    }

    Rect displayedBounds() {
        Rect area = absoluteBounds();
        for (Control node = parent(); node != null; node = node.parent())
            if (node instanceof Scroll scroll) area = area.translated(-scroll.scrollX(), -scroll.scrollY());
        return area;
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
        return true;
    }

    float drawingDepth() {
        return drawingDepth(this);
    }

    /** The layout tree, rather than legacy widget constructor depths, owns the rendered layer. */
    public static float drawingDepth(Control node) {
        float depth = io.github.luyingyilong.geg.client.ui.util.OreLayers.INPUT;
        for (Control parent = node.parent(); parent != null; parent = parent.parent()) depth += parent.contentDepth();
        return depth;
    }

    boolean pointerInteractive(int x, int y) { return pointerInteractive(this, x, y); }

    public static boolean pointerInteractive(Control target, int x, int y) {
        for (Control node = target.parent(); node != null; node = node.parent()) {
            if (!(node instanceof Scroll scroll)) continue;
            int localX = x, localY = y;
            for (Control outer = node.parent(); outer != null; outer = outer.parent())
                if (outer instanceof Scroll ancestor) { localX += ancestor.scrollX(); localY += ancestor.scrollY(); }
            if (scroll.scrollbarAt(localX, localY)) return false;
        }
        return true;
    }

    /** Share Ore viewport clips with the native field's drawing and pointer checks. */
    Rect drawingClip() {
        Rect clip = null;
        for (Control node = parent(); node != null; node = node.parent()) {
            Rect next = node instanceof Scroll scroll
                    ? scroll.viewport().translated(scroll.absoluteBounds().x(), scroll.absoluteBounds().y())
                    : node instanceof Drawer ? node.absoluteBounds() : null;
            if (next == null) continue;
            for (Control outer = node.parent(); outer != null; outer = outer.parent())
                if (outer instanceof Scroll scroll) next = next.translated(-scroll.scrollX(), -scroll.scrollY());
            if (clip == null) clip = next;
            else {
                int x = Math.max(clip.x(), next.x());
                int y = Math.max(clip.y(), next.y());
                clip = new Rect(x, y, Math.max(0, Math.min(clip.right(), next.right()) - x),
                        Math.max(0, Math.min(clip.bottom(), next.bottom()) - y));
            }
        }
        return clip;
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
        Rect current = displayedBounds();
        if (!current.equals(lastArea)) {
            lastArea = current;
            widget.scheduleResize();
        }
    }

    @Override public void visible(boolean visible) {
        super.visible(visible);
        widget.setInputVisible(visible);
    }
}
