package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.widget.sizer.Unit;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import io.github.luyingyilong.geg.client.ui.layout.Scroll;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Font;

/** Ore layout proxy for a ModularUI text field. ModularUI retains focus and editing. */
public final class InputControl extends Control {
    private final Input widget;
    private Rect lastArea = new Rect(0, 0, 0, 0);

    public InputControl(Input widget) {
        this.widget = widget;
        widget.layoutControl(this);
        minimum(1, widget.controlSize().height());
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

    float drawingDepth(float defaultDepth) {
        for (Control node = parent(); node != null; node = node.parent())
            if (node instanceof Drawer drawer) return Math.max(defaultDepth, drawer.depth() + 20.0F);
        return defaultDepth;
    }

    /** Native widgets are drawn separately, so inherit the Ore ancestors' viewport clips. */
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
