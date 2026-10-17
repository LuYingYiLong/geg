package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.ITheme;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeKey;

public final class OreTheme {
    public static final int TEXT_DARK = 0xFF1E1E1F;
    public static final int TEXT_WHITE = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFD0D1D4;
    public static final int TEXT_DISABLED = 0x80D0D1D4;
    public static final int TEXT_ERROR = 0xFFFF7777;
    public static final int TEXT_ACCENT = 0xFF52BEA8;

    public static final String NAME = "geg_ore";
    private static final WidgetThemeKey<WidgetTheme> PANEL = ITheme.getDefault().getPanelTheme().key();

    public static final WidgetThemeKey<WidgetTheme> BACKGROUND = PANEL.createSubKey("background");
    public static final WidgetThemeKey<WidgetTheme> HEADER_PANEL =
            PANEL.createSubKey("headerPanel");
    public static final WidgetThemeKey<WidgetTheme> INPUT_BOX = PANEL.createSubKey("inputBox");
    public static final WidgetThemeKey<WidgetTheme> MODAL = PANEL.createSubKey("modal");
    public static final WidgetThemeKey<WidgetTheme> DROPDOWN_PANEL =
            PANEL.createSubKey("dropdownPanel");
    public static final WidgetThemeKey<WidgetTheme> DROPDOWN_ITEM = PANEL.createSubKey("dropdownItem");
    public static final WidgetThemeKey<WidgetTheme> MENU_PANEL = PANEL.createSubKey("menuPanel");
    public static final WidgetThemeKey<WidgetTheme> MENU_DIVIDER = PANEL.createSubKey("menuDivider");
    public static final WidgetThemeKey<WidgetTheme> BAR = PANEL.createSubKey("bar");
    public static final WidgetThemeKey<WidgetTheme> FLOATING_BACKGROUND = PANEL.createSubKey("floatingBackground");
    public static final WidgetThemeKey<WidgetTheme> FLOATING_HEADER = PANEL.createSubKey("floatingHeader");
    public static final WidgetThemeKey<WidgetTheme> V_GRABBER = PANEL.createSubKey("VGrabber");
    public static final WidgetThemeKey<WidgetTheme> H_GRABBER = PANEL.createSubKey("HGrabber");
    private static final WidgetThemeKey<WidgetTheme> BUTTON =
            ITheme.getDefault().getButtonTheme().key();
    public static final WidgetThemeKey<WidgetTheme> BUTTON_DISABLED =
            BUTTON.createSubKey("disabled");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_MENU_HOVERED =
            BUTTON.createSubKey("menuItem:hovered");
    public static final WidgetThemeKey<WidgetTheme> BUTTON_MENU_PRESSED =
            BUTTON.createSubKey("menuItem:pressed");
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
    public static final WidgetThemeKey<WidgetTheme> SEGMENTED_BUTTON_SELECTED =
            BUTTON.createSubKey("segmentedButtonSelected");

    private OreTheme() {
    }

    public static void register() {
        // Loading this class registers Ore's panel and button subthemes before resource reload.
    }
}
