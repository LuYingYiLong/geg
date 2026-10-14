package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.ITheme;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeKey;

public final class OreTheme {
    public static final String NAME = "geg_ore";
    private static final WidgetThemeKey<WidgetTheme> PANEL = ITheme.getDefault().getPanelTheme().key();

    public static final WidgetThemeKey<WidgetTheme> BACKGROUND = PANEL.createSubKey("background");
    public static final WidgetThemeKey<WidgetTheme> HEADER_PANEL =
            PANEL.createSubKey("headerPanel");
    public static final WidgetThemeKey<WidgetTheme> INPUT_BOX = PANEL.createSubKey("inputBox");
    public static final WidgetThemeKey<WidgetTheme> MODAL = PANEL.createSubKey("modal");

    private OreTheme() {
    }

    public static void register() {
        // Loading this class registers every panel subtheme before resource reload.
    }
}
