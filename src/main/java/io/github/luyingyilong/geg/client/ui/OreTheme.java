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
    public static final WidgetThemeKey<WidgetTheme> BUTTON_DISABLED =
            BUTTON.createSubKey("disabled");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_SECONDARY_NORMAL =
            BUTTON.createSubKey("secondary:normal");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_SECONDARY_HOVERED =
            BUTTON.createSubKey("secondary:hovered");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_SECONDARY_PRESSED =
            BUTTON.createSubKey("secondary:pressed");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_PRIMARY_NORMAL =
            BUTTON.createSubKey("primary:normal");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_PRIMARY_HOVERED =
            BUTTON.createSubKey("primary:hovered");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_PRIMARY_PRESSED =
            BUTTON.createSubKey("primary:pressed");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_DESTRUCTIVE_NORMAL =
            BUTTON.createSubKey("destructive:normal");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_DESTRUCTIVE_HOVERED =
            BUTTON.createSubKey("destructive:hovered");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_DESTRUCTIVE_PRESSED =
            BUTTON.createSubKey("destructive:pressed");
    public static final WidgetThemeKey<WidgetTheme> TAB_BUTTON_SELECTED =
            BUTTON.createSubKey("tabButtonSelected");

    private OreTheme() {
    }

    public static void register() {
        // Loading this class registers Ore's panel and button subthemes before resource reload.
    }
}
