package io.github.luyingyilong.geg.client.ui.layout;

import net.minecraft.client.gui.Font;

/** A control which assigns rectangles to its direct children. */
public class Container extends Control {
    protected void fitChild(Control child, Rect bounds, Font font) {
        child.layout(bounds, font);
    }
}
