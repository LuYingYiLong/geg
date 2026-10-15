package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.theme.WidgetThemeKey;
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
        Size childSize = child == null ? new Size(0, 0) : child.measureSize(font, available);
        return new Size(Math.max(minimumWidth(), childSize.width()),
                Math.max(minimumHeight(), childSize.height()));
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        if (child != null) fitChild(child, new Rect(0, 0,
                Math.max(child.minimumWidth(), localBounds.width()),
                Math.max(child.minimumHeight(), localBounds.height())), font);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        drawAt(context, screenBounds(screenX, screenY));
        if (child != null && child.visible()) child.draw(context, screenX, screenY);
    }

    public void drawAt(ModularGuiContext context, Rect area) {
        WidgetThemeEntry<WidgetTheme> entry = themeKey == null ? null
                : context.getTheme().getWidgetTheme(themeKey);
        WidgetTheme style = entry == null ? context.getTheme().getPanelTheme().theme() : entry.theme();
        IDrawable background = style.getBackground();
        if (background != null && area.width() > 0 && area.height() > 0) {
            background.draw(context, area.x(), area.y(), area.width(), area.height(), style);
        }
    }
}
