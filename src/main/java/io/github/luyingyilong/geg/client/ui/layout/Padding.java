package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

public final class Padding extends Control {
    public record Insets(int top, int right, int bottom, int left) {
        public Insets {
            if (top < 0 || right < 0 || bottom < 0 || left < 0)
                throw new IllegalArgumentException("Negative padding");
        }
        public static Insets all(int value) { return new Insets(value, value, value, value); }
    }

    private final Insets insets;
    private Control child;

    public Padding(Insets insets) { this.insets = insets; }
    public Padding child(Control child) { attach(child); this.child = child; return this; }
    public Insets insets() { return insets; }

    @Override
    public int measureWidth(Font font, int available) {
        return insets.left() + insets.right() + (child == null ? 0
                : child.measureWidth(font, Math.max(0, available - insets.left() - insets.right())));
    }

    @Override
    public int measureHeight(Font font, int available) {
        return insets.top() + insets.bottom() + (child == null ? 0
                : child.measureHeight(font, Math.max(0, available - insets.top() - insets.bottom())));
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        if (child != null) child.layout(new Rect(insets.left(), insets.top(),
                localBounds.width() - insets.left() - insets.right(),
                localBounds.height() - insets.top() - insets.bottom()), font);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (child != null && child.visible()) child.draw(context, screenX, screenY);
    }
}
