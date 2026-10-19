package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.debug.DebugBridge;

import brachy.modularui.api.drawable.IDrawable;
import io.github.luyingyilong.ore.render.DrawContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.theme.WidgetThemeKey;
import io.github.luyingyilong.ore.theme.OreDrawables;
import net.minecraft.client.gui.Font;

/** Themed background; space allocation is inherited from Control. */
public final class Panel extends Container {
    private final WidgetThemeKey<WidgetTheme> themeKey;
    private Control child;

    public Panel(WidgetThemeKey<WidgetTheme> themeKey) { this.themeKey = themeKey; }
    public Panel setChild(Control child) {
        replaceChild(this.child, child);
        return this;
    }

    @Override protected void validateChildren(java.util.List<Control> proposed) {
        if (proposed.size() > 1) throw new IllegalStateException("Panel accepts one child; use setChild");
    }

    @Override protected void childrenChanged() {
        child = getChildCount() == 0 ? null : getChild(0);
    }

    @Override public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        Size childSize = child == null ? new Size(0, 0) : child.desiredSize(font, available);
        return new Size(Math.max(getMinimumWidth(), childSize.width()),
                Math.max(getMinimumHeight(), childSize.height()));
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(localBounds, font);
            if (child != null) fitChild(child, new Rect(0, 0,
                    Math.max(child.getMinimumWidth(), localBounds.width()),
                    Math.max(child.getMinimumHeight(), localBounds.height())), font);
        }
    }

    @Override
    public void draw(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            drawAt(context, getScreenBounds(screenX, screenY));
            if (child != null && (child.isAlive() && child.getVisible())) child.draw(context, screenX, screenY);
        }
    }

    public void drawAt(DrawContext context, Rect area) {
        if (!isAlive()) return;
        WidgetThemeEntry<WidgetTheme> entry = themeKey == null ? null
                : context.getTheme().getWidgetTheme(themeKey);
        WidgetTheme style = entry == null ? context.getTheme().getPanelTheme().theme() : entry.theme();
        IDrawable background = style.getBackground();
        OreDrawables.draw(context, background, area, style);
    }
}
