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
    public static final WidgetThemeKey<WidgetTheme> DROPDOWN_PANEL =
            PANEL.createSubKey("dropdownPanel");
    public static final WidgetThemeKey<WidgetTheme> MENU_ITEM = PANEL.createSubKey("menuItem");
    public static final WidgetThemeKey<WidgetTheme> LIST_ITEM = PANEL.createSubKey("listItem");
    private static final WidgetThemeKey<WidgetTheme> BUTTON =
            ITheme.getDefault().getButtonTheme().key();
    public static final WidgetThemeKey<WidgetTheme> TAB_BUTTON =
            BUTTON.createSubKey("tabButton");
    public static final WidgetThemeKey<WidgetTheme> TAB_BUTTON_SELECTED =
            BUTTON.createSubKey("tabButtonSelected");

    private OreTheme() {
    }

    public static void register() {
        // Loading this class registers the panel subthemes and tab button theme before resource reload.
    }
}
