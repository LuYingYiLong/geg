package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.debug.DebugBridge;

import net.minecraft.client.gui.Font;

/**
 * A control which assigns rectangles to its direct children.
 */
public class Container extends Control {
    protected final void fitChild(Control child, Rect slot, Font font) {
        if (!child.isAlive()) return;
        DebugBridge.backend().slot(child, slot);
        int horizontal = child.getSizeFlagsHorizontal();
        int vertical = child.getSizeFlagsVertical();
        Size natural = SizeFlags.fills(horizontal) && SizeFlags.fills(vertical)
                ? child.getMinimumSize() : child.desiredSize(font, new Size(slot.width(), slot.height()));
        int width = Math.max(child.getMinimumWidth(), SizeFlags.fills(horizontal)
                ? slot.width() : Math.min(slot.width(), natural.width()));
        if (!SizeFlags.fills(vertical) && width != slot.width())
            natural = child.desiredSize(font, new Size(width, slot.height()));
        int height = Math.max(child.getMinimumHeight(), SizeFlags.fills(vertical)
                ? slot.height() : Math.min(slot.height(), natural.height()));
        layoutChild(child, new Rect(slot.x() + SizeFlags.offset(horizontal, slot.width(), width),
                slot.y() + SizeFlags.offset(vertical, slot.height(), height), width, height), font);
    }

    /**
     * Internal geometry such as an Overlay viewport or a scrollbar is not a content slot.
     */
    protected final void layoutChild(Control child, Rect bounds, Font font) {
        if (child.isAlive()) try (var phase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            child.layout(bounds, font);
        }
    }
}
