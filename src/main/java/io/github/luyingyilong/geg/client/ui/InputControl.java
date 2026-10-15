package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.widget.sizer.Unit;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import net.minecraft.client.gui.Font;

/** Ore layout proxy for a ModularUI text field. ModularUI retains focus and editing. */
public final class InputControl extends Control {
    private final Input widget;
    private Rect lastArea = new Rect(0, 0, 0, 0);

    public InputControl(Input widget) {
        this.widget = widget;
        minimum(1, widget.controlSize().height());
        widget.left(() -> absoluteBounds().x(), Unit.Measure.PIXEL);
        widget.top(() -> absoluteBounds().y(), Unit.Measure.PIXEL);
        widget.width(() -> Math.max(1, bounds().width()), Unit.Measure.PIXEL);
        widget.height(() -> Math.max(1, bounds().height()), Unit.Measure.PIXEL);
    }

    public Input widget() { return widget; }

    @Override public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        Rect current = absoluteBounds();
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
