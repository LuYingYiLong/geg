package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeKey;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Panel;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;

/** Horizontal themed divider, preserving the menu texture's two-pixel height. */
public final class Divider extends Control {
    private final Panel painter;

    public Divider() { this(OreTheme.MENU_DIVIDER); }

    Divider(WidgetThemeKey<WidgetTheme> theme) {
        painter = new Panel(theme);
        minimum(0, 2);
        sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        painter.drawAt(context, screenBounds(screenX, screenY));
    }
}
