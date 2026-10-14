package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.theme.WidgetThemeKey;
import net.minecraft.client.gui.Font;

/** Themed background; space allocation is inherited from Control. */
public final class Panel extends Control {
    private final WidgetThemeKey<WidgetTheme> themeKey;
    private Control child;

    public Panel(WidgetThemeKey<WidgetTheme> themeKey) { this.themeKey = themeKey; }
    public Panel child(Control child) { attach(child); this.child = child; return this; }

    @Override
    public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        if (child != null) child.layout(new Rect(0, 0, localBounds.width(), localBounds.height()), font);
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
