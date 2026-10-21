package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.Descriptions;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.render.*;

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
    public static final ThemeKey<io.github.luyingyilong.ore.text.FontMetricsPolicy> FONT_METRICS = ThemeKey.of("Control", "font_metrics",
            io.github.luyingyilong.ore.text.FontMetricsPolicy.class, ThemeKey.Kind.FONT_METRICS,
            io.github.luyingyilong.ore.text.FontMetricsPolicy.DEFAULT, true);
    public static final ThemeKey<StyleBox> PANEL = ThemeKey.of("Panel","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/panel.png"), 3, 3, 0, 0, 3, 3), new ThemeInsets(1,1,1,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> HEADER_PANEL = ThemeKey.of("Navbar","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/header_panel.png"), 1, 7, 0, 0, 1, 7), new ThemeInsets(0,3,0,3), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> INPUT_BOX = ThemeKey.of("Input","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/input_box.png"), 16, 16, 0, 0, 16, 16), new ThemeInsets(1,4,1,4), new ThemeInsets(4,0,4,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> DIALOG_PANEL = ThemeKey.of("Dialog","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/modal.png"), 16, 16, 0, 0, 16, 16), new ThemeInsets(3,5,3,5), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> DROPDOWN_PANEL = ThemeKey.of("Dropdown","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/dropdown_panel.png"), 16, 16, 0, 0, 16, 16), new ThemeInsets(1,1,1,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> DROPDOWN_ITEM = ThemeKey.of("Dropdown","item",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/dropdown_item.png"), 16, 16, 0, 0, 16, 16), new ThemeInsets(1,1,1,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> BAR = ThemeKey.of("Table","row",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/bar.png"), 3, 3, 0, 0, 3, 3), new ThemeInsets(0,1,0,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> FLOATING_BACKGROUND = ThemeKey.of("Drawer","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/floating_background.png"), 3, 3, 0, 0, 3, 3), new ThemeInsets(1,1,1,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> FLOATING_HEADER = ThemeKey.of("Modal","header",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/floating_header.png"), 3, 3, 0, 0, 3, 3), new ThemeInsets(1,1,1,1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> H_DIVIDER = ThemeKey.of("Divider","horizontal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/h_divider.png"), 1, 2, 0, 0, 1, 2), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> V_DIVIDER = ThemeKey.of("Divider","vertical",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/v_divider.png"), 1, 1, 0, 0, 1, 1), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> V_GRABBER = ThemeKey.of("Scroll","vertical_grabber",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/v_grabber.png"), 6, 12, 0, 0, 6, 12), new ThemeInsets(2,5,2,5), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> H_GRABBER = ThemeKey.of("Scroll","horizontal_grabber",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/h_grabber.png"), 6, 6, 0, 0, 6, 6), new ThemeInsets(2,2,2,2), new ThemeInsets(0,0,0,0), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<StyleBox> SEGMENTED_BUTTON_SELECTED = ThemeKey.of("Segmented","selected",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(id("textures/gui/tab_selected.png"), 16, 16, 0, 0, 16, 16), new ThemeInsets(2,2,2,2), new ThemeInsets(8,4,8,4), new ThemeInsets(0,0,0,0), StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F, new ThemeInsets(0,2,0,0), new io.github.luyingyilong.ore.layout.Offset(0,2)),true);
    public static final ThemeKey<Integer> FONT_COLOR = ThemeKey.of("Label","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<Integer> TITLE_FONT_COLOR = ThemeKey.of("LabelHero","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
    public static final ThemeKey<ResourceLocation> TEXT_FONT = ThemeKey.of("Label","font",ResourceLocation.class,ThemeKey.Kind.FONT,id("ore"),true);
    public static final ThemeKey<ResourceLocation> HERO_FONT = ThemeKey.of("Label","hero_font",ResourceLocation.class,ThemeKey.Kind.FONT,id("ore_hero"),true);
    public static final ThemeKey<Float> FONT_SIZE = new ThemeKey<>("Label","font_size",Float.class,ThemeKey.Kind.FLOAT,8.0F,true,v -> Float.isFinite(v) && v>0);
    public static final ThemeKey<Float> TITLE_FONT_SIZE = new ThemeKey<>("LabelHero","font_size",Float.class,ThemeKey.Kind.FLOAT,8.0F,true,v -> Float.isFinite(v) && v>0);
    public static final ThemeKey<Boolean> TEXT_SHADOW = ThemeKey.of("Label","shadow",Boolean.class,ThemeKey.Kind.BOOLEAN,false,false);
    public static final ThemeKey<Integer> OVERLAY = ThemeKey.of("Control","overlay",Integer.class,ThemeKey.Kind.COLOR,OVERLAY_COLOR,false);
    public static final ThemeKey<Integer> TRACK = ThemeKey.of("Scroll","track_color",Integer.class,ThemeKey.Kind.COLOR,0xFF58585A,false);
    public static final ThemeKey<Integer> INPUT_FONT_COLOR = ThemeKey.of("Input","font_color",Integer.class,ThemeKey.Kind.COLOR,TEXT_WHITE,false);
    public static final ThemeKey<Integer> INPUT_PLACEHOLDER_FONT_COLOR = ThemeKey.of("Input","font_placeholder_color",Integer.class,ThemeKey.Kind.COLOR,TEXT_SECONDARY,false);
    public static final ThemeKey<Integer> INPUT_MARKED = ThemeKey.of("Input","selection_color",Integer.class,ThemeKey.Kind.COLOR,0xFFA9AAAC,false);
    public static final ThemeKey<ResourceLocation> INPUT_FONT = ThemeKey.of("Input","font",ResourceLocation.class,ThemeKey.Kind.FONT,id("ore"),true);
    public static final ThemeKey<Float> INPUT_FONT_SIZE = new ThemeKey<>("Input","font_size",Float.class,ThemeKey.Kind.FLOAT,8.0F,true,v -> Float.isFinite(v) && v>0);
    public static final ThemeKey<StyleBox> SLOT_PANEL = ThemeKey.of("Slot","panel",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new TextureRegion(ResourceLocation.fromNamespaceAndPath("modularui","textures/gui/slot/item.png"), 18, 18, 0, 0, 18, 18), new ThemeInsets(1,1,1,1), ThemeInsets.ZERO, ThemeInsets.ZERO, StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1.0F),true);
    public static final ThemeKey<Integer> LABEL_DARK_FONT_COLOR = ThemeKey.of("LabelDark","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false);
    public static final ThemeKey<Integer> LABEL_SECONDARY_FONT_COLOR = ThemeKey.of("LabelSecondary","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFFD0D1D4,false);
    public static final ThemeKey<Integer> LABEL_ERROR_FONT_COLOR = ThemeKey.of("LabelError","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFF7777,false);
    public static final ThemeKey<Integer> LABEL_ACCENT_FONT_COLOR = ThemeKey.of("LabelAccent","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFF52BEA8,false);
    public static final ThemeKey<Integer> LABEL_DISABLED_FONT_COLOR = ThemeKey.of("LabelDisabled","font_color",Integer.class,ThemeKey.Kind.COLOR,0x80D0D1D4,false);
    public static final ThemeKey<Integer> SEGMENTED_SELECTED_FONT_COLOR = ThemeKey.of("Segmented","font_selected_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false);
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
    public static final ThemeKey<Boolean> HERO = ThemeKey.of("Label","hero",Boolean.class,ThemeKey.Kind.BOOLEAN,false,true);
    public static final ThemeKey<Boolean> TITLE_HERO = ThemeKey.of("LabelHero","hero",Boolean.class,ThemeKey.Kind.BOOLEAN,true,true);
    public static final ThemeKey<Integer> NAVBAR_TITLE_FONT_COLOR = ThemeKey.of("NavbarTitle","font_color",Integer.class,ThemeKey.Kind.COLOR,TEXT_DARK,false);
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
    public static final ThemeKey<StyleBox> FOCUS=ThemeKey.of("Control","focus",StyleBox.class,ThemeKey.Kind.STYLE,
            new StyleBoxFlat(0,new ThemeInsets(1,1,1,1),0xFFFFFFFF,ThemeInsets.ZERO,new ThemeInsets(1,1,1,1)),false);
    public static final ThemeKey<StyleBox> INPUT_FOCUS=ThemeKey.of("Input","focus",StyleBox.class,ThemeKey.Kind.STYLE,
            new StyleBoxFlat(0,new ThemeInsets(1,1,1,1),0xFFFFFFFF,ThemeInsets.ZERO,new ThemeInsets(2,2,2,2)),false);
    public static final ThemeKey<StyleBox> SEGMENTED_FOCUS=ThemeKey.of("Segmented","focus",StyleBox.class,ThemeKey.Kind.STYLE,
            new StyleBoxFlat(0,new ThemeInsets(1,1,1,1),0xFFFFFFFF,ThemeInsets.ZERO,new ThemeInsets(1,1,2,1)),false);
    private static final List<ThemeKey<?>> KEYS = List.of(FOCUS,INPUT_FOCUS,SEGMENTED_FOCUS,
            SEGMENTED_INDICATOR, SEGMENTED_INDICATOR_HEIGHT, SEGMENTED_INDICATOR_WIDTH_RATIO,
            SEGMENTED_INDICATOR_MAX_WIDTH, SEGMENTED_INDICATOR_BOTTOM_GAP,
            MODAL_PANEL, MODAL_FOOTER, SEGMENTED_MIN_WIDTH, AVATAR_COLOR, HERO, TITLE_HERO,
            NAVBAR_TITLE_FONT_COLOR, PANEL, HEADER_PANEL, INPUT_BOX,
            DIALOG_PANEL, DROPDOWN_PANEL, DROPDOWN_ITEM, BAR,
            FLOATING_BACKGROUND, FLOATING_HEADER, H_DIVIDER, V_DIVIDER,
            V_GRABBER, H_GRABBER, SEGMENTED_BUTTON_SELECTED,
            FONT_COLOR, TITLE_FONT_COLOR, FONT_METRICS,
            TEXT_FONT, HERO_FONT, FONT_SIZE, TITLE_FONT_SIZE, TEXT_SHADOW,
            OVERLAY, TRACK, INPUT_FONT_COLOR, INPUT_PLACEHOLDER_FONT_COLOR,
            INPUT_MARKED, INPUT_FONT, INPUT_FONT_SIZE, SLOT_PANEL,
            LABEL_DARK_FONT_COLOR, LABEL_SECONDARY_FONT_COLOR, LABEL_ERROR_FONT_COLOR, LABEL_ACCENT_FONT_COLOR,
            LABEL_DISABLED_FONT_COLOR, SEGMENTED_SELECTED_FONT_COLOR, TEXT_DARK_KEY, TEXT_WHITE_KEY,
            TEXT_SECONDARY_KEY, TEXT_DISABLED_KEY, TEXT_ERROR_KEY, TEXT_ACCENT_KEY,
            TAG_PRIMARY_KEY, TAG_NEUTRAL_KEY, TAG_SECONDARY_KEY, TAG_INFORMATIVE_KEY,
            TAG_NOTICE_KEY, TAG_WARNING_KEY, TAG_REALMS_INFORMATIVE_KEY, TAG_OUTLINE_KEY,
            TOAST_SHADOW_KEY
    );
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
        IconTheme.registerTheme(db);
        io.github.luyingyilong.ore.Button.registerTheme(db);
        io.github.luyingyilong.ore.Collapse.registerTheme(db);
        io.github.luyingyilong.ore.TextureRect.registerTheme(db);
        Label.registerTheme(db);
        io.github.luyingyilong.ore.Dropdown.registerTheme(db);
        io.github.luyingyilong.ore.Checkbox.registerTheme(db);
        io.github.luyingyilong.ore.Radio.registerTheme(db);
        io.github.luyingyilong.ore.Spin.registerTheme(db);
        io.github.luyingyilong.ore.Switch.registerTheme(db);
        io.github.luyingyilong.ore.Slider.registerTheme(db);
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
        io.github.luyingyilong.ore.FormItem.registerTheme(db);
        io.github.luyingyilong.ore.Slot.registerTheme(db);
        Descriptions.registerTheme(db);
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
