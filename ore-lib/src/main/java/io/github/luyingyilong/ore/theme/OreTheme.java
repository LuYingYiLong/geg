package io.github.luyingyilong.ore.theme;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Built-in declarations and safe defaults. Resource values remain instance/inheritance scoped. */
public final class OreTheme {
    public static final int OVERLAY_COLOR = 0x80000000;
    public static final int TEXT_DARK = 0xFF1E1E1F;
    public static final int TEXT_WHITE = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFD0D1D4;
    public static final int TEXT_DISABLED = 0x80D0D1D4;
    public static final int TEXT_ERROR = 0xFFFF7777;
    public static final int TEXT_ACCENT = 0xFF52BEA8;
    public static final int TAG_PRIMARY = 0xFF6CC349;
    public static final int TAG_NEUTRAL = 0xFF1E1E1F;
    public static final int TAG_SECONDARY = 0xFF313233;
    public static final int TAG_INFORMATIVE = 0xFF8CB3FF;
    public static final int TAG_NOTICE = 0xFFFFE866;
    public static final int TAG_WARNING = 0xFFF46D6D;
    public static final int TAG_REALMS_INFORMATIVE = 0xFFAC90F3;
    public static final int TAG_OUTLINE = TEXT_DARK;
    public static final int TOAST_SHADOW = 0x4D000000;
    public static final String NAME = "ore:ore";
    public static final ResourceLocation ID = id("ore"), DEFAULT_ID = id("default");
    public static final ThemeKey<StyleBox> PANEL = ThemeKey.of("Panel","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/panel.png"), 3, 3, 0, 0, 3, 3, new ThemeInsets(1,1,1,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> HEADER_PANEL = ThemeKey.of("Navbar","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/header_panel.png"), 1, 7, 0, 0, 1, 7, new ThemeInsets(0,3,0,3), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> INPUT_BOX = ThemeKey.of("Input","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/input_box.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(1,4,1,4), new ThemeInsets(4,0,4,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> DIALOG_PANEL = ThemeKey.of("Dialog","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/modal.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(3,5,3,5), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> DROPDOWN_PANEL = ThemeKey.of("Dropdown","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/dropdown_panel.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(1,1,1,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> DROPDOWN_ITEM = ThemeKey.of("Dropdown","item",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/dropdown_item.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(1,1,1,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> BAR = ThemeKey.of("Table","row",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/bar.png"), 3, 3, 0, 0, 3, 3, new ThemeInsets(0,1,0,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> FLOATING_BACKGROUND = ThemeKey.of("Drawer","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/floating_background.png"), 3, 3, 0, 0, 3, 3, new ThemeInsets(1,1,1,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> FLOATING_HEADER = ThemeKey.of("Modal","header",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/floating_header.png"), 3, 3, 0, 0, 3, 3, new ThemeInsets(1,1,1,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> H_DIVIDER = ThemeKey.of("Divider","horizontal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/h_divider.png"), 1, 2, 0, 0, 1, 2, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> V_DIVIDER = ThemeKey.of("Divider","vertical",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/v_divider.png"), 1, 1, 0, 0, 1, 1, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> V_GRABBER = ThemeKey.of("Scroll","vertical_grabber",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/v_grabber.png"), 6, 12, 0, 0, 6, 12, new ThemeInsets(2,5,2,5), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> H_GRABBER = ThemeKey.of("Scroll","horizontal_grabber",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/h_grabber.png"), 6, 6, 0, 0, 6, 6, new ThemeInsets(2,2,2,2), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> BUTTON_DISABLED = ThemeKey.of("Button","disabled",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/button_disabled.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,4,2,4), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> SEGMENTED_BUTTON_SELECTED = ThemeKey.of("Segmented","selected",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/tab_selected.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,2,2,2), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F, new ThemeInsets(0,2,0,0), new io.github.luyingyilong.ore.layout.Offset(0,2)),true);
    public static final ThemeKey<StyleBox> BUTTON_SECONDARY_NORMAL = ThemeKey.of("Button","secondary_normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/button_secondary_normal.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,4,2,4), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> BUTTON_SECONDARY_HOVERED = ThemeKey.of("Button","secondary_hovered",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/button_secondary_hovered.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,4,2,4), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> BUTTON_SECONDARY_PRESSED = ThemeKey.of("Button","secondary_pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/button_secondary_pressed.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,2,2,2), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F, new ThemeInsets(0,2,0,0), new io.github.luyingyilong.ore.layout.Offset(0,2)),true);
    public static final ThemeKey<StyleBox> BUTTON_PRIMARY_NORMAL = ThemeKey.of("Button","primary_normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/button_primary_normal.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,4,2,4), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> BUTTON_PRIMARY_HOVERED = ThemeKey.of("Button","primary_hovered",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/button_primary_hovered.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,4,2,4), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> BUTTON_PRIMARY_PRESSED = ThemeKey.of("Button","primary_pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/button_primary_pressed.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,2,2,2), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F, new ThemeInsets(0,2,0,0), new io.github.luyingyilong.ore.layout.Offset(0,2)),true);
    public static final ThemeKey<StyleBox> BUTTON_DESTRUCTIVE_NORMAL = ThemeKey.of("Button","destructive_normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/button_destructive_normal.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,4,2,4), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> BUTTON_DESTRUCTIVE_HOVERED = ThemeKey.of("Button","destructive_hovered",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/button_destructive_hovered.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,4,2,4), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> BUTTON_DESTRUCTIVE_PRESSED = ThemeKey.of("Button","destructive_pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/button_destructive_pressed.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(2,2,2,2), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F, new ThemeInsets(0,2,0,0), new io.github.luyingyilong.ore.layout.Offset(0,2)),true);
    public static final ThemeKey<StyleBox> BUTTON_MENU_HOVERED = ThemeKey.of("Button","menu_hovered",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/menu_item_hovered.png"), 1, 3, 0, 0, 1, 3, new ThemeInsets(0,1,0,1), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> BUTTON_MENU_PRESSED = ThemeKey.of("Button","menu_pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(id("textures/gui/menu_item_pressed.png"), 1, 3, 0, 0, 1, 3, new ThemeInsets(0,1,0,1), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<Integer> TEXT_COLOR = ThemeKey.of("Label","color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<ResourceLocation> TEXT_FONT = ThemeKey.of("Label","font",ResourceLocation.class,ThemeKey.Kind.FONT,id("ore"),true);
    public static final ThemeKey<ResourceLocation> HERO_FONT = ThemeKey.of("Label","hero_font",ResourceLocation.class,ThemeKey.Kind.FONT,id("ore_hero"),true);
    public static final ThemeKey<Float> FONT_SIZE = new ThemeKey<>("Label","font_size",Float.class,ThemeKey.Kind.FLOAT,8.0F,true,v -> Float.isFinite(v) && v>0);
    public static final ThemeKey<Boolean> TEXT_SHADOW = ThemeKey.of("Label","shadow",Boolean.class,ThemeKey.Kind.BOOLEAN,false,false);
    public static final ThemeKey<Integer> OVERLAY = ThemeKey.of("Control","overlay",Integer.class,ThemeKey.Kind.COLOR,OVERLAY_COLOR,false);
    public static final ThemeKey<Integer> TRACK = ThemeKey.of("Scroll","track_color",Integer.class,ThemeKey.Kind.COLOR,0xFF58585A,false);
    public static final ThemeKey<Integer> INPUT_TEXT = ThemeKey.of("Input","color",Integer.class,ThemeKey.Kind.COLOR,TEXT_WHITE,false);
    public static final ThemeKey<Integer> INPUT_HINT = ThemeKey.of("Input","placeholder_color",Integer.class,ThemeKey.Kind.COLOR,TEXT_SECONDARY,false);
    public static final ThemeKey<Integer> INPUT_MARKED = ThemeKey.of("Input","selection_color",Integer.class,ThemeKey.Kind.COLOR,0xFFA9AAAC,false);
    public static final ThemeKey<ResourceLocation> INPUT_FONT = ThemeKey.of("Input","font",ResourceLocation.class,ThemeKey.Kind.FONT,id("ore"),true);
    public static final ThemeKey<Float> INPUT_FONT_SIZE = new ThemeKey<>("Input","font_size",Float.class,ThemeKey.Kind.FLOAT,8.0F,true,v -> Float.isFinite(v) && v>0);
    public static final ThemeKey<StyleBox> SLOT_PANEL = ThemeKey.of("Slot","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(ResourceLocation.fromNamespaceAndPath("modularui","textures/gui/slot/item.png"),18,18,0,0,18,18,new ThemeInsets(1,1,1,1),ThemeInsets.ZERO,ThemeInsets.ZERO,StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1.0F),true);
    public static final ThemeKey<Integer> LABEL_DARK_COLOR = ThemeKey.of("LabelDark","color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false);
    public static final ThemeKey<Integer> LABEL_SECONDARY_COLOR = ThemeKey.of("LabelSecondary","color",Integer.class,ThemeKey.Kind.COLOR,0xFFD0D1D4,false);
    public static final ThemeKey<Integer> LABEL_ERROR_COLOR = ThemeKey.of("LabelError","color",Integer.class,ThemeKey.Kind.COLOR,0xFFFF7777,false);
    public static final ThemeKey<Integer> LABEL_ACCENT_COLOR = ThemeKey.of("LabelAccent","color",Integer.class,ThemeKey.Kind.COLOR,0xFF52BEA8,false);
    public static final ThemeKey<Integer> LABEL_DISABLED_COLOR = ThemeKey.of("LabelDisabled","color",Integer.class,ThemeKey.Kind.COLOR,0x80D0D1D4,false);
    public static final ThemeKey<Integer> BUTTON_DISABLED_TEXT = ThemeKey.of("Button","disabled_color",Integer.class,ThemeKey.Kind.COLOR,0x801E1E1F,false);
    public static final ThemeKey<Integer> SEGMENTED_BUTTON_SELECTED_TEXT = ThemeKey.of("Segmented","selected_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<Integer> BUTTON_SECONDARY_NORMAL_TEXT = ThemeKey.of("Button","secondary_normal_color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false);
    public static final ThemeKey<Integer> BUTTON_SECONDARY_HOVERED_TEXT = ThemeKey.of("Button","secondary_hovered_color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false);
    public static final ThemeKey<Integer> BUTTON_SECONDARY_PRESSED_TEXT = ThemeKey.of("Button","secondary_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false);
    public static final ThemeKey<Integer> BUTTON_PRIMARY_NORMAL_TEXT = ThemeKey.of("Button","primary_normal_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<Integer> BUTTON_PRIMARY_HOVERED_TEXT = ThemeKey.of("Button","primary_hovered_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<Integer> BUTTON_PRIMARY_PRESSED_TEXT = ThemeKey.of("Button","primary_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<Integer> BUTTON_DESTRUCTIVE_NORMAL_TEXT = ThemeKey.of("Button","destructive_normal_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<Integer> BUTTON_DESTRUCTIVE_HOVERED_TEXT = ThemeKey.of("Button","destructive_hovered_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<Integer> BUTTON_DESTRUCTIVE_PRESSED_TEXT = ThemeKey.of("Button","destructive_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<Integer> BUTTON_MENU_HOVERED_TEXT = ThemeKey.of("Button","menu_hovered_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<Integer> BUTTON_MENU_PRESSED_TEXT = ThemeKey.of("Button","menu_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<Integer> TEXT_DARK_KEY = ThemeKey.of("Control","text_dark",Integer.class,ThemeKey.Kind.COLOR,TEXT_DARK,false);
    public static final ThemeKey<Integer> TEXT_WHITE_KEY = ThemeKey.of("Control","text_white",Integer.class,ThemeKey.Kind.COLOR,TEXT_WHITE,false);
    public static final ThemeKey<Integer> TEXT_SECONDARY_KEY = ThemeKey.of("Control","text_secondary",Integer.class,ThemeKey.Kind.COLOR,TEXT_SECONDARY,false);
    public static final ThemeKey<Integer> TEXT_DISABLED_KEY = ThemeKey.of("Control","text_disabled",Integer.class,ThemeKey.Kind.COLOR,TEXT_DISABLED,false);
    public static final ThemeKey<Integer> TEXT_ERROR_KEY = ThemeKey.of("Control","text_error",Integer.class,ThemeKey.Kind.COLOR,TEXT_ERROR,false);
    public static final ThemeKey<Integer> TEXT_ACCENT_KEY = ThemeKey.of("Control","text_accent",Integer.class,ThemeKey.Kind.COLOR,TEXT_ACCENT,false);
    public static final ThemeKey<Integer> TAG_PRIMARY_KEY = ThemeKey.of("Tag","tag_primary",Integer.class,ThemeKey.Kind.COLOR,TAG_PRIMARY,false);
    public static final ThemeKey<Integer> TAG_NEUTRAL_KEY = ThemeKey.of("Tag","tag_neutral",Integer.class,ThemeKey.Kind.COLOR,TAG_NEUTRAL,false);
    public static final ThemeKey<Integer> TAG_SECONDARY_KEY = ThemeKey.of("Tag","tag_secondary",Integer.class,ThemeKey.Kind.COLOR,TAG_SECONDARY,false);
    public static final ThemeKey<Integer> TAG_INFORMATIVE_KEY = ThemeKey.of("Tag","tag_informative",Integer.class,ThemeKey.Kind.COLOR,TAG_INFORMATIVE,false);
    public static final ThemeKey<Integer> TAG_NOTICE_KEY = ThemeKey.of("Tag","tag_notice",Integer.class,ThemeKey.Kind.COLOR,TAG_NOTICE,false);
    public static final ThemeKey<Integer> TAG_WARNING_KEY = ThemeKey.of("Tag","tag_warning",Integer.class,ThemeKey.Kind.COLOR,TAG_WARNING,false);
    public static final ThemeKey<Integer> TAG_REALMS_INFORMATIVE_KEY = ThemeKey.of("Tag","tag_realms_informative",Integer.class,ThemeKey.Kind.COLOR,TAG_REALMS_INFORMATIVE,false);
    public static final ThemeKey<Integer> TAG_OUTLINE_KEY = ThemeKey.of("Tag","tag_outline",Integer.class,ThemeKey.Kind.COLOR,TAG_OUTLINE,false);
    public static final ThemeKey<Integer> TOAST_SHADOW_KEY = ThemeKey.of("Toast","toast_shadow",Integer.class,ThemeKey.Kind.COLOR,TOAST_SHADOW,false);
    public static final ThemeKey<ThemeIcon> ICON_SMALL_CLOSE = ThemeKey.of("Control","small_close",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/small_close.png"), 8, 8, 0, 0, 8, 8, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_CLOSE = ThemeKey.of("Control","close",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/close.png"), 7, 7, 0, 0, 7, 7, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_CHECKED = ThemeKey.of("Control","checked",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/checked.png"), 8, 8, 0, 0, 8, 8, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_ARROW_DOWN = ThemeKey.of("Control","arrow_down",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/arrow_down.png"), 7, 7, 0, 0, 7, 7, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_ARROW_DOWN_LIGHT = ThemeKey.of("Control","arrow_down_light",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/arrow_down_light.png"), 7, 7, 0, 0, 7, 7, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_ARROW_RIGHT_LIGHT = ThemeKey.of("Control","arrow_right_light",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/arrow_right_light.png"), 7, 7, 0, 0, 7, 7, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_ARROW_LEFT = ThemeKey.of("Control","arrow_left",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/arrow_left.png"), 7, 7, 0, 0, 7, 7, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_FLAG = ThemeKey.of("Control","flag",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/flag.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_ORG = ThemeKey.of("Control","org",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/org.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_LOADING = ThemeKey.of("Control","loading",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/loading.png"), 7, 112, 0, 0, 7, 112, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_CHECKBOX_NORMAL = ThemeKey.of("Control","checkbox_normal",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/checkbox_normal.png"), 12, 12, 0, 0, 12, 12, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_CHECKBOX_HOVERED = ThemeKey.of("Control","checkbox_hovered",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/checkbox_hovered.png"), 12, 12, 0, 0, 12, 12, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_CHECKBOX_DISABLED = ThemeKey.of("Control","checkbox_disabled",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/checkbox_disabled.png"), 12, 12, 0, 0, 12, 12, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_CHECKBOX_CHECKED_NORMAL = ThemeKey.of("Control","checkbox_checked_normal",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/checkbox_checked_normal.png"), 12, 12, 0, 0, 12, 12, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_CHECKBOX_CHECKED_HOVERED = ThemeKey.of("Control","checkbox_checked_hovered",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/checkbox_checked_hovered.png"), 12, 12, 0, 0, 12, 12, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_CHECKBOX_CHECKED_PRESSED = ThemeKey.of("Control","checkbox_checked_pressed",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/checkbox_checked_pressed.png"), 12, 12, 0, 0, 12, 12, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_CHECKBOX_CHECKED_DISABLED = ThemeKey.of("Control","checkbox_checked_disabled",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/checkbox_checked_disabled.png"), 12, 12, 0, 0, 12, 12, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_FILTER = ThemeKey.of("Control","filter",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/filter.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_EMAIL = ThemeKey.of("Control","email",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/email.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_EMAIL_READED = ThemeKey.of("Control","email_readed",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/email_readed.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_GIFT = ThemeKey.of("Control","gift",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/gift.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_GIFT_OPENED = ThemeKey.of("Control","gift_opened",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/gift_opened.png"), 16, 16, 0, 0, 16, 16, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_SWITCH_BASE = ThemeKey.of("Control","switch_base",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/switch_base.png"), 27, 13, 0, 0, 27, 13, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_SWITCH_BASE_DISABLED = ThemeKey.of("Control","switch_base_disabled",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/switch_base_disabled.png"), 27, 13, 0, 0, 27, 13, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_SWITCH_BUTTON_NORMAL = ThemeKey.of("Control","switch_button_normal",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/switch_button_normal.png"), 14, 15, 0, 0, 14, 15, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_SWITCH_BUTTON_HOVERED = ThemeKey.of("Control","switch_button_hovered",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/switch_button_hovered.png"), 14, 15, 0, 0, 14, 15, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_SWITCH_BUTTON_PRESSED = ThemeKey.of("Control","switch_button_pressed",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/switch_button_pressed.png"), 14, 15, 0, 0, 14, 15, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<ThemeIcon> ICON_SWITCH_BUTTON_DISABLED = ThemeKey.of("Control","switch_button_disabled",ThemeIcon.class,ThemeKey.Kind.ICON,new ThemeIcon(new StyleBoxTexture(id("textures/icons/switch_button_disabled.png"), 14, 15, 0, 0, 14, 15, new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F)),true);
    public static final ThemeKey<StyleBox> BUTTON_TEXT_NORMAL = ThemeKey.of("Button","text_normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxFlat(0x00000000,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),ThemeInsets.ZERO),true);
    public static final ThemeKey<StyleBox> BUTTON_TEXT_HOVERED = ThemeKey.of("Button","text_hovered",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxFlat(0x16FFFFFF,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),ThemeInsets.ZERO),true);
    public static final ThemeKey<StyleBox> BUTTON_TEXT_PRESSED = ThemeKey.of("Button","text_pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxFlat(0x1A000000,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),ThemeInsets.ZERO),true);
    public static final ThemeKey<StyleBox> BUTTON_MENU_NORMAL = ThemeKey.of("Button","menu_normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxFlat(0x00000000,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),ThemeInsets.ZERO),true);
    public static final ThemeKey<Boolean> HERO = ThemeKey.of("Label","hero",Boolean.class,ThemeKey.Kind.BOOLEAN,false,true);
    public static final ThemeKey<Boolean> TITLE_HERO = ThemeKey.of("LabelHero","hero",Boolean.class,ThemeKey.Kind.BOOLEAN,true,true);
    public static final ThemeKey<Integer> NAVBAR_TITLE_COLOR = ThemeKey.of("NavbarTitle","color",Integer.class,ThemeKey.Kind.COLOR,TEXT_DARK,false);
    public static final ThemeKey<Integer> AVATAR_COLOR = ThemeKey.of("Avatar","background_color",Integer.class,ThemeKey.Kind.COLOR,0xFF58585A,false);
    public static final ThemeKey<Integer> SEGMENTED_MIN_WIDTH = new ThemeKey<>("Segmented","minimum_item_width",Integer.class,ThemeKey.Kind.INTEGER,24,true,v -> v>=0);
    public static final ThemeKey<StyleBox> SEGMENTED_INDICATOR = ThemeKey.of("Segmented","selected_indicator",StyleBox.class,
            ThemeKey.Kind.STYLE,new StyleBoxFlat(TEXT_WHITE,ThemeInsets.ZERO,0,ThemeInsets.ZERO,ThemeInsets.ZERO),false);
    public static final ThemeKey<Integer> SEGMENTED_INDICATOR_HEIGHT = new ThemeKey<>("Segmented","selected_indicator_height",
            Integer.class,ThemeKey.Kind.INTEGER,1,false,v -> v>=0);
    public static final ThemeKey<Float> SEGMENTED_INDICATOR_WIDTH_RATIO = new ThemeKey<>("Segmented","selected_indicator_width_ratio",
            Float.class,ThemeKey.Kind.FLOAT,1.0F/3.0F,false,v -> Float.isFinite(v) && v>=0 && v<=1);
    public static final ThemeKey<Integer> SEGMENTED_INDICATOR_MAX_WIDTH = new ThemeKey<>("Segmented","selected_indicator_max_width",
            Integer.class,ThemeKey.Kind.INTEGER,16,false,v -> v>=0);
    public static final ThemeKey<Integer> SEGMENTED_INDICATOR_BOTTOM_GAP = new ThemeKey<>("Segmented","selected_indicator_bottom_gap",
            Integer.class,ThemeKey.Kind.INTEGER,1,false,v -> v>=0);
    public static final ThemeKey<StyleBox> MODAL_PANEL = ThemeKey.of("Modal","panel",StyleBox.class,ThemeKey.Kind.STYLE,FLOATING_BACKGROUND.defaultValue(),true);
    public static final ThemeKey<StyleBox> MODAL_FOOTER = ThemeKey.of("Modal","footer",StyleBox.class,ThemeKey.Kind.STYLE,BAR.defaultValue(),true);
    private static final List<ThemeKey<?>> KEYS = List.of(
            SEGMENTED_INDICATOR, SEGMENTED_INDICATOR_HEIGHT, SEGMENTED_INDICATOR_WIDTH_RATIO,
            SEGMENTED_INDICATOR_MAX_WIDTH, SEGMENTED_INDICATOR_BOTTOM_GAP,
            MODAL_PANEL, MODAL_FOOTER, SEGMENTED_MIN_WIDTH, AVATAR_COLOR, HERO, TITLE_HERO,
            NAVBAR_TITLE_COLOR, BUTTON_TEXT_NORMAL, BUTTON_TEXT_HOVERED, BUTTON_TEXT_PRESSED,
            BUTTON_MENU_NORMAL, PANEL, HEADER_PANEL, INPUT_BOX,
            DIALOG_PANEL, DROPDOWN_PANEL, DROPDOWN_ITEM, BAR,
            FLOATING_BACKGROUND, FLOATING_HEADER, H_DIVIDER, V_DIVIDER,
            V_GRABBER, H_GRABBER, BUTTON_DISABLED, SEGMENTED_BUTTON_SELECTED,
            BUTTON_SECONDARY_NORMAL, BUTTON_SECONDARY_HOVERED, BUTTON_SECONDARY_PRESSED, BUTTON_PRIMARY_NORMAL,
            BUTTON_PRIMARY_HOVERED, BUTTON_PRIMARY_PRESSED, BUTTON_DESTRUCTIVE_NORMAL, BUTTON_DESTRUCTIVE_HOVERED,
            BUTTON_DESTRUCTIVE_PRESSED, BUTTON_MENU_HOVERED, BUTTON_MENU_PRESSED, TEXT_COLOR,
            TEXT_FONT, HERO_FONT, FONT_SIZE, TEXT_SHADOW,
            OVERLAY, TRACK, INPUT_TEXT, INPUT_HINT,
            INPUT_MARKED, INPUT_FONT, INPUT_FONT_SIZE, SLOT_PANEL,
            LABEL_DARK_COLOR, LABEL_SECONDARY_COLOR, LABEL_ERROR_COLOR, LABEL_ACCENT_COLOR,
            LABEL_DISABLED_COLOR, BUTTON_DISABLED_TEXT, SEGMENTED_BUTTON_SELECTED_TEXT, BUTTON_SECONDARY_NORMAL_TEXT,
            BUTTON_SECONDARY_HOVERED_TEXT, BUTTON_SECONDARY_PRESSED_TEXT, BUTTON_PRIMARY_NORMAL_TEXT, BUTTON_PRIMARY_HOVERED_TEXT,
            BUTTON_PRIMARY_PRESSED_TEXT, BUTTON_DESTRUCTIVE_NORMAL_TEXT, BUTTON_DESTRUCTIVE_HOVERED_TEXT, BUTTON_DESTRUCTIVE_PRESSED_TEXT,
            BUTTON_MENU_HOVERED_TEXT, BUTTON_MENU_PRESSED_TEXT, TEXT_DARK_KEY, TEXT_WHITE_KEY,
            TEXT_SECONDARY_KEY, TEXT_DISABLED_KEY, TEXT_ERROR_KEY, TEXT_ACCENT_KEY,
            TAG_PRIMARY_KEY, TAG_NEUTRAL_KEY, TAG_SECONDARY_KEY, TAG_INFORMATIVE_KEY,
            TAG_NOTICE_KEY, TAG_WARNING_KEY, TAG_REALMS_INFORMATIVE_KEY, TAG_OUTLINE_KEY,
            TOAST_SHADOW_KEY, ICON_SMALL_CLOSE, ICON_CLOSE, ICON_CHECKED,
            ICON_ARROW_DOWN, ICON_ARROW_DOWN_LIGHT, ICON_ARROW_RIGHT_LIGHT, ICON_ARROW_LEFT,
            ICON_FLAG, ICON_ORG, ICON_LOADING, ICON_CHECKBOX_NORMAL,
            ICON_CHECKBOX_HOVERED, ICON_CHECKBOX_DISABLED, ICON_CHECKBOX_CHECKED_NORMAL, ICON_CHECKBOX_CHECKED_HOVERED,
            ICON_CHECKBOX_CHECKED_PRESSED, ICON_CHECKBOX_CHECKED_DISABLED, ICON_FILTER, ICON_EMAIL,
            ICON_EMAIL_READED, ICON_GIFT, ICON_GIFT_OPENED, ICON_SWITCH_BASE,
            ICON_SWITCH_BASE_DISABLED, ICON_SWITCH_BUTTON_NORMAL, ICON_SWITCH_BUTTON_HOVERED, ICON_SWITCH_BUTTON_PRESSED,
            ICON_SWITCH_BUTTON_DISABLED
    );
    private static final java.util.Map<ResourceLocation,ThemeKey<ThemeIcon>> ICON_KEYS=java.util.Map.ofEntries(
        java.util.Map.entry(ICON_SMALL_CLOSE.defaultValue().getTexture().location(),ICON_SMALL_CLOSE),
        java.util.Map.entry(ICON_CLOSE.defaultValue().getTexture().location(),ICON_CLOSE),
        java.util.Map.entry(ICON_CHECKED.defaultValue().getTexture().location(),ICON_CHECKED),
        java.util.Map.entry(ICON_ARROW_DOWN.defaultValue().getTexture().location(),ICON_ARROW_DOWN),
        java.util.Map.entry(ICON_ARROW_DOWN_LIGHT.defaultValue().getTexture().location(),ICON_ARROW_DOWN_LIGHT),
        java.util.Map.entry(ICON_ARROW_RIGHT_LIGHT.defaultValue().getTexture().location(),ICON_ARROW_RIGHT_LIGHT),
        java.util.Map.entry(ICON_ARROW_LEFT.defaultValue().getTexture().location(),ICON_ARROW_LEFT),
        java.util.Map.entry(ICON_FLAG.defaultValue().getTexture().location(),ICON_FLAG),
        java.util.Map.entry(ICON_ORG.defaultValue().getTexture().location(),ICON_ORG),
        java.util.Map.entry(ICON_LOADING.defaultValue().getTexture().location(),ICON_LOADING),
        java.util.Map.entry(ICON_CHECKBOX_NORMAL.defaultValue().getTexture().location(),ICON_CHECKBOX_NORMAL),
        java.util.Map.entry(ICON_CHECKBOX_HOVERED.defaultValue().getTexture().location(),ICON_CHECKBOX_HOVERED),
        java.util.Map.entry(ICON_CHECKBOX_DISABLED.defaultValue().getTexture().location(),ICON_CHECKBOX_DISABLED),
        java.util.Map.entry(ICON_CHECKBOX_CHECKED_NORMAL.defaultValue().getTexture().location(),ICON_CHECKBOX_CHECKED_NORMAL),
        java.util.Map.entry(ICON_CHECKBOX_CHECKED_HOVERED.defaultValue().getTexture().location(),ICON_CHECKBOX_CHECKED_HOVERED),
        java.util.Map.entry(ICON_CHECKBOX_CHECKED_PRESSED.defaultValue().getTexture().location(),ICON_CHECKBOX_CHECKED_PRESSED),
        java.util.Map.entry(ICON_CHECKBOX_CHECKED_DISABLED.defaultValue().getTexture().location(),ICON_CHECKBOX_CHECKED_DISABLED),
        java.util.Map.entry(ICON_FILTER.defaultValue().getTexture().location(),ICON_FILTER),
        java.util.Map.entry(ICON_EMAIL.defaultValue().getTexture().location(),ICON_EMAIL),
        java.util.Map.entry(ICON_EMAIL_READED.defaultValue().getTexture().location(),ICON_EMAIL_READED),
        java.util.Map.entry(ICON_GIFT.defaultValue().getTexture().location(),ICON_GIFT),
        java.util.Map.entry(ICON_GIFT_OPENED.defaultValue().getTexture().location(),ICON_GIFT_OPENED),
        java.util.Map.entry(ICON_SWITCH_BASE.defaultValue().getTexture().location(),ICON_SWITCH_BASE),
        java.util.Map.entry(ICON_SWITCH_BASE_DISABLED.defaultValue().getTexture().location(),ICON_SWITCH_BASE_DISABLED),
        java.util.Map.entry(ICON_SWITCH_BUTTON_NORMAL.defaultValue().getTexture().location(),ICON_SWITCH_BUTTON_NORMAL),
        java.util.Map.entry(ICON_SWITCH_BUTTON_HOVERED.defaultValue().getTexture().location(),ICON_SWITCH_BUTTON_HOVERED),
        java.util.Map.entry(ICON_SWITCH_BUTTON_PRESSED.defaultValue().getTexture().location(),ICON_SWITCH_BUTTON_PRESSED),
        java.util.Map.entry(ICON_SWITCH_BUTTON_DISABLED.defaultValue().getTexture().location(),ICON_SWITCH_BUTTON_DISABLED));
    public static ThemeKey<ThemeIcon> getIconKey(ResourceLocation location) {
        var key=ICON_KEYS.get(location); if(key==null) throw new IllegalArgumentException("Unknown built-in icon: "+location); return key;
    }
    private OreTheme() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("ore",path); }
    public static void register(ThemeDB db) {
        db.registerType("Control",null);
        db.registerType("Button","Control");
        db.registerType("Label","Control");
        db.registerType("Panel","Control");
        db.registerType("Box","Control");
        db.registerType("Grid","Control");
        db.registerType("Padding","Control");
        db.registerType("Scroll","Control");
        db.registerType("Table","Control");
        db.registerType("Tree","Control");
        db.registerType("Splitter","Control");
        db.registerType("Divider","Control");
        db.registerType("Navbar","Control");
        db.registerType("InputControl","Control");
        db.registerType("Input","Control");
        db.registerType("NumberInput","Input");
        db.registerType("TextArea","Input");
        db.registerType("Form","Control");
        db.registerType("FormItem","Control");
        db.registerType("Descriptions","Control");
        db.registerType("DescriptionItem","Control");
        db.registerType("Dialog","Control");
        db.registerType("Modal","Control");
        db.registerType("Drawer","Control");
        db.registerType("Dropdown","Control");
        db.registerType("Menu","Control");
        db.registerType("Segmented","Control");
        db.registerType("Checkbox","Control");
        db.registerType("Switch","Control");
        db.registerType("Slot","Control");
        db.registerType("Badge","Control");
        db.registerType("Tag","Control");
        db.registerType("Toast","Control");
        db.registerType("Avatar","Control");
        db.registerType("Window","Control");
        db.registerVariation("LabelDark","Label");
        db.registerVariation("LabelSecondary","Label");
        db.registerVariation("LabelError","Label");
        db.registerVariation("LabelAccent","Label");
        db.registerVariation("LabelDisabled","Label");
        db.registerVariation("LabelHero","Label");
        db.registerVariation("NavbarTitle","LabelHero");
        KEYS.forEach(db::register);
        io.github.luyingyilong.ore.Button.registerTheme(db);
        io.github.luyingyilong.ore.Label.registerTheme(db);
        io.github.luyingyilong.ore.Dropdown.registerTheme(db);
        io.github.luyingyilong.ore.Checkbox.registerTheme(db);
        io.github.luyingyilong.ore.Switch.registerTheme(db);
        io.github.luyingyilong.ore.Segmented.registerTheme(db);
        io.github.luyingyilong.ore.Dialog.registerTheme(db);
        io.github.luyingyilong.ore.Modal.registerTheme(db);
        io.github.luyingyilong.ore.Drawer.registerTheme(db);
        io.github.luyingyilong.ore.Table.registerTheme(db);
        io.github.luyingyilong.ore.Menu.registerTheme(db);
        io.github.luyingyilong.ore.Navbar.registerTheme(db);
        io.github.luyingyilong.ore.Tag.registerTheme(db);
        io.github.luyingyilong.ore.Toast.registerTheme(db);
        io.github.luyingyilong.ore.Badge.registerTheme(db);
        io.github.luyingyilong.ore.Descriptions.registerTheme(db);
        io.github.luyingyilong.ore.Divider.registerTheme(db);
        io.github.luyingyilong.ore.Tree.registerTheme(db);
        io.github.luyingyilong.ore.layout.Splitter.registerTheme(db);
        io.github.luyingyilong.ore.layout.Panel.registerTheme(db);
        io.github.luyingyilong.ore.layout.Box.registerTheme(db);
        io.github.luyingyilong.ore.layout.Grid.registerTheme(db);
        io.github.luyingyilong.ore.layout.Padding.registerTheme(db);
        io.github.luyingyilong.ore.layout.Scroll.registerTheme(db);
        io.github.luyingyilong.ore.layout.Window.registerTheme(db);
        io.github.luyingyilong.ore.input.InputControl.registerTheme(db);

        db.registerTheme(ID);
    }
    public static void registerType(ThemeDB db,String name) { db.registerType("Control",null); if(!db.hasType(name)) db.registerType(name,name.equals("NumberInput") || name.equals("TextArea") ? "Input" : "Control"); KEYS.stream().filter(k -> k.type().equals(name)).forEach(db::register); }
}
