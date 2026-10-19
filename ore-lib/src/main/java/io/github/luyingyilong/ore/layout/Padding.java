package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.render.DrawContext;
import net.minecraft.client.gui.Font;

import java.util.Objects;

public final class Padding extends Container {
    public record Insets(int top, int right, int bottom, int left) {
        public Insets {
            if (top < 0 || right < 0 || bottom < 0 || left < 0)
                throw new IllegalArgumentException("Negative padding");
        }
        public static Insets all(int value) { return new Insets(value, value, value, value); }
    }

    private Insets insets;
    private Control child;

    public Padding(Insets insets) { this.insets = Objects.requireNonNull(insets); }
    public Padding setInsets(Insets insets) {
        Insets next = Objects.requireNonNull(insets);
        if (!this.insets.equals(next)) {
            this.insets = next;
            requestLayout();
        }
        return this;
    }
    public Padding setChild(Control child) {
        replaceChild(this.child, child);
        return this;
    }

    @Override protected void validateChildren(java.util.List<Control> proposed) {
        if (proposed.size() > 1) throw new IllegalStateException("Padding accepts one child; use setChild");
    }

    @Override protected void childrenChanged() {
        child = getChildCount() == 0 ? null : getChild(0);
    }

    public Insets getInsets() { if (!DebugBridge.active()) return insets; return new Insets(debugValue("top", insets.top()), debugValue("right", insets.right()),
                debugValue("bottom", insets.bottom()), debugValue("left", insets.left())); }

    @Override public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        int horizontal = getInsets().left() + getInsets().right();
        int vertical = getInsets().top() + getInsets().bottom();
        Size inner = child == null ? new Size(0, 0) : child.desiredSize(font,
                new Size(Math.max(0, available.width() - horizontal),
                        Math.max(0, available.height() - vertical)));
        return new Size(Math.max(getMinimumWidth(), horizontal + inner.width()),
                Math.max(getMinimumHeight(), vertical + inner.height()));
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return getInsets().left() + getInsets().right() + (child == null ? 0
                : child.measureWidth(font, Math.max(0, available - getInsets().left() - getInsets().right())));
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return getInsets().top() + getInsets().bottom() + (child == null ? 0
                : child.measureHeight(font, Math.max(0, available - getInsets().top() - getInsets().bottom())));
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(localBounds, font);
            if (child != null) fitChild(child, new Rect(getInsets().left(), getInsets().top(),
                    Math.max(child.getMinimumWidth(), localBounds.width() - getInsets().left() - getInsets().right()),
                    Math.max(child.getMinimumHeight(), localBounds.height() - getInsets().top() - getInsets().bottom())), font);
        }
    }

    @Override
    public void draw(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            if (child != null && (child.isAlive() && child.getVisible())) child.draw(context, screenX, screenY);
        }
    }
    @Override public void debugProperties(java.util.List<DebugProperty> out) {
        super.debugProperties(out);
        out.add(DebugProperty.number(this,"top","Layout",()->getInsets().top(),0,10000));
        out.add(DebugProperty.number(this,"right","Layout",()->getInsets().right(),0,10000));
        out.add(DebugProperty.number(this,"bottom","Layout",()->getInsets().bottom(),0,10000));
        out.add(DebugProperty.number(this,"left","Layout",()->getInsets().left(),0,10000));
    }
}
