package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

public final class Padding extends Container {
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
    public Padding child(Control child) {
        if (this.child != null) removeChild(this.child);
        addChild(child);
        return this;
    }

    @Override public Padding addChild(Control child) {
        if (this.child != null) throw new IllegalStateException("Padding accepts one child");
        super.addChild(child);
        this.child = child;
        return this;
    }

    @Override public boolean removeChild(Control child) {
        if (!super.removeChild(child)) return false;
        if (this.child == child) this.child = null;
        return true;
    }
    public Insets insets() { return insets; }

    @Override public Size measureSize(Font font, Size available) {
        int horizontal = insets.left() + insets.right();
        int vertical = insets.top() + insets.bottom();
        Size inner = child == null ? new Size(0, 0) : child.measureSize(font,
                new Size(Math.max(0, available.width() - horizontal),
                        Math.max(0, available.height() - vertical)));
        return new Size(Math.max(minimumWidth(), horizontal + inner.width()),
                Math.max(minimumHeight(), vertical + inner.height()));
    }

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
        if (child != null) fitChild(child, new Rect(insets.left(), insets.top(),
                Math.max(child.minimumWidth(), localBounds.width() - insets.left() - insets.right()),
                Math.max(child.minimumHeight(), localBounds.height() - insets.top() - insets.bottom())), font);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (child != null && child.visible()) child.draw(context, screenX, screenY);
    }
}
