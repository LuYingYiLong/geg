package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.debug.DebugBridge;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
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
    public Panel child(Control child) {
        if (this.child != null) removeChild(this.child);
        addChild(child);
        return this;
    }

    @Override public Panel addChild(Control child) {
        if (this.child != null) throw new IllegalStateException("Panel accepts one child");
        super.addChild(child);
        this.child = child;
        return this;
    }

    @Override public boolean removeChild(Control child) {
        if (!super.removeChild(child)) return false;
        if (this.child == child) this.child = null;
        return true;
    }

    @Override public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        Size childSize = child == null ? new Size(0, 0) : child.desiredSize(font, available);
        return new Size(Math.max(minimumWidth(), childSize.width()),
                Math.max(minimumHeight(), childSize.height()));
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(localBounds, font);
            if (child != null) fitChild(child, new Rect(0, 0,
                    Math.max(child.minimumWidth(), localBounds.width()),
                    Math.max(child.minimumHeight(), localBounds.height())), font);
        }
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            drawAt(context, screenBounds(screenX, screenY));
            if (child != null && (child.isAlive() && child.visible())) child.draw(context, screenX, screenY);
        }
    }

    public void drawAt(ModularGuiContext context, Rect area) {
        if (!isAlive()) return;
        WidgetThemeEntry<WidgetTheme> entry = themeKey == null ? null
                : context.getTheme().getWidgetTheme(themeKey);
        WidgetTheme style = entry == null ? context.getTheme().getPanelTheme().theme() : entry.theme();
        IDrawable background = style.getBackground();
        OreDrawables.draw(context, background, area, style);
    }
}
