package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.Signal0;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.signal.SignalSource0;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.StyleBox;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public final class Button extends Control {
    private static final ThemePadding ICON_PADDING = ThemePadding.of("Button", "icon_", 4, 4, 4, 4);
    public static final ThemeKey<Integer> ICON_PADDING_TOP = ICON_PADDING.top();
    public static final ThemeKey<Integer> ICON_PADDING_RIGHT = ICON_PADDING.right();
    public static final ThemeKey<Integer> ICON_PADDING_BOTTOM = ICON_PADDING.bottom();
    public static final ThemeKey<Integer> ICON_PADDING_LEFT = ICON_PADDING.left();
    private static final ThemePadding BADGE_PADDING = ThemePadding.of("Button", "badge_", 2, 8, 2, 8);
    public static final ThemeKey<Integer> BADGE_PADDING_TOP = BADGE_PADDING.top();
    public static final ThemeKey<Integer> BADGE_PADDING_RIGHT = BADGE_PADDING.right();
    public static final ThemeKey<Integer> BADGE_PADDING_BOTTOM = BADGE_PADDING.bottom();
    public static final ThemeKey<Integer> BADGE_PADDING_LEFT = BADGE_PADDING.left();

    public static final java.util.List<String> BUILTIN_VARIATIONS = java.util.List.of("SecondaryButton","PrimaryButton","DestructiveButton","TextButton","MenuButton","DarkButton");
    public static final ThemeKey<StyleBox> NORMAL = ThemeKey.of("Button","normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_secondary_normal.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true);
    public static final ThemeKey<Integer> FONT_COLOR = ThemeKey.of("Button","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false);
    public static final ThemeKey<StyleBox> HOVER = ThemeKey.of("Button","hover",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_secondary_hover.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true);
    public static final ThemeKey<Integer> FONT_HOVER_COLOR = ThemeKey.of("Button","font_hover_color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false);
    public static final ThemeKey<StyleBox> PRESSED = ThemeKey.of("Button","pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_secondary_pressed.png"),16,16,0,0,16,16),new ThemeInsets(2,2,2,2),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,2,0,0),new io.github.luyingyilong.ore.layout.Offset(0,2)),true);
    public static final ThemeKey<Integer> FONT_PRESSED_COLOR = ThemeKey.of("Button","font_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false);
    public static final ThemeKey<StyleBox> DISABLED = ThemeKey.of("Button","disabled",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_disabled.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true);
    public static final ThemeKey<Integer> FONT_DISABLED_COLOR = ThemeKey.of("Button","font_disabled_color",Integer.class,ThemeKey.Kind.COLOR,0x801E1E1F,false);
    public static final int HEIGHT = Size.MEDIUM.getHeight();
    private static final int ICON_GAP = 4;
    private static final int LABEL_OFFSET_Y = -2;
    private final Label label = ownPart("label", new Label().setOverflow(Label.Overflow.ELLIPSIS));
    private final TextureRect iconControl = ownPart("icon", new TextureRect().setStretchMode(TextureRect.StretchMode.KEEP_CENTERED));
    private final TextureRect trailingIconControl = ownPart("trailing_icon", new TextureRect().setStretchMode(TextureRect.StretchMode.KEEP_CENTERED));
    private final SignalSource0 pressed = new SignalSource0(this, "pressed");
    private final SignalSource<Boolean> toggled = new SignalSource<>(this, "toggled", "Boolean");
    private final Consumer<OreSounds.Cue> soundPlayer;
    private Size buttonSize = Size.MEDIUM;
    private TextAlignment alignment = TextAlignment.CENTER;
    private Badge badge;
    private boolean emphasizedSound;
    private ACTION_MODE actionMode = ACTION_MODE.BUTTON_RELEASE;
    private boolean pointerPressed;
    private boolean pointerHovered;
    private int activationKey = -1;
    private boolean toggleButton;
    private boolean buttonPressed;
    private boolean disabled;
    public Button() {
        this(OreSounds::play);
    }

    Button(Consumer<OreSounds.Cue> soundPlayer) {
        this.soundPlayer = java.util.Objects.requireNonNull(soundPlayer);
        setMinimum(0, buttonSize.getHeight());
        setFocusable(true);

    }

    public Button(Component label) {
        this();
        setLabel(label);
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db,"Button");
        ICON_PADDING.register(db); BADGE_PADDING.register(db);
        for(var key:java.util.List.of(NORMAL,HOVER,PRESSED,DISABLED,FONT_COLOR,FONT_HOVER_COLOR,FONT_PRESSED_COLOR,FONT_DISABLED_COLOR))db.register(key);
        db.registerVariation("SecondaryButton","Button");
        db.register(ThemeKey.of("SecondaryButton","normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_secondary_normal.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("SecondaryButton","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false));
        db.register(ThemeKey.of("SecondaryButton","hover",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_secondary_hover.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("SecondaryButton","font_hover_color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false));
        db.register(ThemeKey.of("SecondaryButton","pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_secondary_pressed.png"),16,16,0,0,16,16),new ThemeInsets(2,2,2,2),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,2,0,0),new io.github.luyingyilong.ore.layout.Offset(0,2)),true));
        db.register(ThemeKey.of("SecondaryButton","font_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFF1E1E1F,false));
        db.register(ThemeKey.of("SecondaryButton","disabled",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_disabled.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("SecondaryButton","font_disabled_color",Integer.class,ThemeKey.Kind.COLOR,0x801E1E1F,false));
        db.registerVariation("PrimaryButton","Button");
        db.register(ThemeKey.of("PrimaryButton","normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_primary_normal.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("PrimaryButton","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("PrimaryButton","hover",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_primary_hover.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("PrimaryButton","font_hover_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("PrimaryButton","pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_primary_pressed.png"),16,16,0,0,16,16),new ThemeInsets(2,2,2,2),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,2,0,0),new io.github.luyingyilong.ore.layout.Offset(0,2)),true));
        db.register(ThemeKey.of("PrimaryButton","font_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("PrimaryButton","disabled",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_disabled.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("PrimaryButton","font_disabled_color",Integer.class,ThemeKey.Kind.COLOR,0x801E1E1F,false));
        db.registerVariation("DestructiveButton","Button");
        db.register(ThemeKey.of("DestructiveButton","normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_destructive_normal.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("DestructiveButton","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("DestructiveButton","hover",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_destructive_hover.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("DestructiveButton","font_hover_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("DestructiveButton","pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_destructive_pressed.png"),16,16,0,0,16,16),new ThemeInsets(2,2,2,2),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,2,0,0),new io.github.luyingyilong.ore.layout.Offset(0,2)),true));
        db.register(ThemeKey.of("DestructiveButton","font_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("DestructiveButton","disabled",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_disabled.png"),16,16,0,0,16,16),new ThemeInsets(2,4,2,4),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("DestructiveButton","font_disabled_color",Integer.class,ThemeKey.Kind.COLOR,0x801E1E1F,false));
        db.registerVariation("TextButton","Button");
        db.register(ThemeKey.of("TextButton","normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxFlat(0x00000000,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("TextButton","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("TextButton","hover",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxFlat(0x16FFFFFF,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("TextButton","font_hover_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("TextButton","pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxFlat(0x1A000000,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("TextButton","font_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("TextButton","disabled",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxFlat(0x00000000,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("TextButton","font_disabled_color",Integer.class,ThemeKey.Kind.COLOR,0x801E1E1F,false));
        db.registerVariation("MenuButton","Button");
        db.register(ThemeKey.of("MenuButton","normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxFlat(0x00000000,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("MenuButton","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("MenuButton","hover",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/menu_item_hover.png"),1,3,0,0,1,3),new ThemeInsets(0,1,0,1),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("MenuButton","font_hover_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("MenuButton","pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/menu_item_pressed.png"),1,3,0,0,1,3),new ThemeInsets(0,1,0,1),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("MenuButton","font_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("MenuButton","disabled",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxFlat(0x00000000,ThemeInsets.ZERO,0,new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("MenuButton","font_disabled_color",Integer.class,ThemeKey.Kind.COLOR,0x801E1E1F,false));
        db.registerVariation("DarkButton","Button");
        db.register(ThemeKey.of("DarkButton","normal",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_dark_normal.png"),5,5,0,0,5,5),new ThemeInsets(2,2,2,2),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("DarkButton","font_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("DarkButton","hover",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_dark_hover.png"),5,5,0,0,5,5),new ThemeInsets(2,2,2,2),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("DarkButton","font_hover_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("DarkButton","pressed",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_dark_pressed.png"),5,5,0,0,5,5),new ThemeInsets(2,2,2,2),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("DarkButton","font_pressed_color",Integer.class,ThemeKey.Kind.COLOR,0xFFFFFFFF,false));
        db.register(ThemeKey.of("DarkButton","disabled",StyleBox.class,ThemeKey.Kind.STYLE,new StyleBoxTexture(new io.github.luyingyilong.ore.render.TextureRegion(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ore","textures/gui/button_dark_disabled.png"),3,3,0,0,3,3),new ThemeInsets(1,1,1,1),new ThemeInsets(8,4,8,4),new ThemeInsets(0,0,0,0),StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,1F,new ThemeInsets(0,0,0,0),new io.github.luyingyilong.ore.layout.Offset(0,0)),true));
        db.register(ThemeKey.of("DarkButton","font_disabled_color",Integer.class,ThemeKey.Kind.COLOR,0x801E1E1F,false));
    }

    @Override public Button setThemeTypeVariation(String variation) {
        if(!variation.isEmpty() && !ThemeDB.getDefault().getTypeChain(variation).contains("Button"))
            throw new IllegalArgumentException("Expected a Button theme variation: "+variation);
        super.setThemeTypeVariation(variation);return this;
    }

    private static io.github.luyingyilong.ore.layout.Size imageSize(TextureRect image, Font font) {
        return image.getTexture() == null || !image.getVisible() ? null : image.desiredSize(font, new io.github.luyingyilong.ore.layout.Size(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    private static void drawLabelled(Control owner, DrawContext context, int x, int y, int width,
                                     int height, Label label, TextureRect iconControl, TextureRect trailingIconControl, TextAlignment alignment,
                                     boolean hovered, boolean pressed, boolean disabled, int reservedRight) {
        pressed = pressed && !disabled;
        var icon = imageSize(iconControl, context.getFont());
        var trailingIcon = imageSize(trailingIconControl, context.getFont());
        StyleBox background = owner.getThemeItem(getStyleKey(hovered, pressed, disabled));
        Rect area = new Rect(x, y, width, height);
        background.draw(context, area);
        boolean hasLabel = !label.getText().getString().isEmpty();
        Rect contentArea = new Rect(x, y, Math.max(0, width - reservedRight), height);
        ContentLayout content = icon != null && !hasLabel && trailingIcon == null
                ? layoutContent(ICON_PADDING.getInsets(owner).inset(contentArea).translated(background.getContentOffset().x(), background.getContentOffset().y()),
                        0, false, icon, null, alignment)
                : contentLayout(contentArea, background,
                hasLabel ? label.measureWidth(context.getFont(), background.getContentRect(contentArea).width()) : 0,
                hasLabel, icon, trailingIcon, alignment);
        if (hasLabel) {
            label.setThemeOverride(OreTheme.FONT_COLOR, owner.getThemeItem(getTextKey(hovered, pressed, disabled)));
            label.setOpticalCenter(true).setOverflow(Label.Overflow.ELLIPSIS).setAlignment(switch (alignment) {
                case LEFT -> Label.Horizontal.LEFT;
                case CENTER -> Label.Horizontal.CENTER;
                case RIGHT -> Label.Horizontal.RIGHT;
            }, Label.Vertical.CENTER);
            label.layout(content.label(), context.getFont());
            label.draw(context, 0, 0);
        }
        drawImage(owner, "icon", iconControl, content.icon(), context);
        drawImage(owner, "trailing_icon", trailingIconControl, content.trailingIcon(), context);
    }

    static ThemeKey<StyleBox> getStyleKey(boolean hovered, boolean pressed, boolean disabled) {
        return disabled?DISABLED:pressed?PRESSED:hovered?HOVER:NORMAL;
    }
    static ThemeKey<Integer> getTextKey(boolean hovered, boolean pressed, boolean disabled) {
        return disabled?FONT_DISABLED_COLOR:pressed?FONT_PRESSED_COLOR:hovered?FONT_HOVER_COLOR:FONT_COLOR;
    }

    static ContentLayout contentLayout(Rect area, StyleBox style, int textWidth, boolean hasLabel,
                                       io.github.luyingyilong.ore.layout.Size icon, io.github.luyingyilong.ore.layout.Size trailingIcon, TextAlignment alignment) {
        // Translate the original content box, independently of the inset background.
        Rect inner = style.getContentDrawRect(area);
        ContentLayout content = layoutContent(inner,
                textWidth, hasLabel, icon, trailingIcon, alignment);
        // Align captions above the shared optical center without shifting icons or state offsets.
        return hasLabel ? new ContentLayout(getLabelBounds(content.label()),
                content.icon(), content.trailingIcon()) : content;
    }

    static Rect getLabelBounds(Rect contentArea) {
        return contentArea.translated(0, LABEL_OFFSET_Y);
    }

    private static ContentLayout layoutContent(Rect area, int textWidth, boolean hasLabel,
                                       io.github.luyingyilong.ore.layout.Size icon, io.github.luyingyilong.ore.layout.Size trailingIcon, TextAlignment alignment) {
        Rect trailing = trailingIcon == null ? null : new Rect(
                area.right() - trailingIcon.width(),
                area.y() + (area.height() - trailingIcon.height()) / 2, trailingIcon.width(), trailingIcon.height());
        if (icon != null && !hasLabel && trailingIcon == null)
            return new ContentLayout(new Rect(area.x(), area.y(), 0, area.height()),
                    new Rect(area.x() + (area.width() - icon.width()) / 2,
                            area.y() + (area.height() - icon.height()) / 2, icon.width(), icon.height()), null);
        int available = Math.max(0, area.width()
                - (trailingIcon == null ? 0 : trailingIcon.width() + ICON_GAP));
        if (icon == null)
            return new ContentLayout(new Rect(area.x(), area.y(), available, area.height()), null, trailing);
        int gap = hasLabel ? ICON_GAP : 0;
        int labelWidth = hasLabel ? Math.min(textWidth, Math.max(0, available - icon.width() - gap)) : 0;
        int freeSpace = Math.max(0, available - icon.width() - gap - labelWidth);
        int left = area.x() + switch (alignment) {
            case LEFT -> 0;
            case CENTER -> freeSpace / 2;
            case RIGHT -> freeSpace;
        };
        return new ContentLayout(new Rect(left + icon.width() + gap, area.y(), labelWidth, area.height()),
                new Rect(left, area.y() + (area.height() - icon.height()) / 2, icon.width(), icon.height()), trailing);
    }

    private static void drawImage(Control owner, String name, TextureRect image, Rect area, DrawContext context) {
        if (area == null) return;
        image.layout(area, context.getFont());
        DebugBridge.backend().internal(owner, name, image);
        image.draw(context, 0, 0);
    }

    static boolean leftMousePressed() {
        return io.github.luyingyilong.ore.window.WindowContext.getCurrent().mouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    public static boolean contains(int x, int y, int width, int height,
                                   int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public Button setLabel(Component label) {
        if (!this.label.getText().equals(label)) {
            this.label.setText(label);
            requestLayout();
        }
        return this;
    }

    public boolean getHero() {
        return label.getHero();
    }

    public Button setHero(boolean hero) {
        label.setHero(hero);
        requestLayout();
        return this;
    }

    public Button setSize(Size size) {
        this.buttonSize = java.util.Objects.requireNonNull(size);
        setMinimum(0, size.getHeight());
        return this;
    }

    public boolean getEmphasizedSound() {
        return emphasizedSound;
    }

    public Button setEmphasizedSound(boolean emphasized) {
        emphasizedSound = emphasized;
        return this;
    }

    public ACTION_MODE getActionMode() {
        return actionMode;
    }

    public Button setActionMode(ACTION_MODE actionMode) {
        this.actionMode = java.util.Objects.requireNonNull(actionMode);
        cancelInteraction();
        return this;
    }

    public Button setAlignment(TextAlignment alignment) {
        this.alignment = java.util.Objects.requireNonNull(alignment);
        return this;
    }

    public TextureSource getIcon() {
        return iconControl.getTexture();
    }

    /**
     * Leading icon with text, or a centered icon when the label is empty. Null clears it.
     */
    public Button setIcon(TextureSource source) {
        iconControl.setTexture(source);
        return this;
    }

    public TextureRect getIconControl() {
        return iconControl;
    }

    public TextureSource getTrailingIcon() {
        return trailingIconControl.getTexture();
    }

    public Button setTrailingIcon(TextureSource source) {
        trailingIconControl.setTexture(source);
        return this;
    }

    public TextureRect getTrailingIconControl() {
        return trailingIconControl;
    }

    public Badge getBadge() {
        return badge;
    }

    /**
     * Right-aligned decoration inside the button; null removes it.
     */
    public Button setBadge(Badge badge) {
        if (this.badge == badge) return this;
        java.util.List<Control> next = new java.util.ArrayList<>(getChildren());
        if (this.badge != null) next.remove(this.badge);
        if (badge != null) next.add(badge);
        updateChildren(next);
        return this;
    }

    @Override
    protected void validateChildren(java.util.List<Control> proposed) {
        if (proposed.stream().filter(Badge.class::isInstance).count() > 1)
            throw new IllegalStateException("Button accepts one Badge; use setBadge");
    }

    @Override
    protected void childrenChanged() {
        badge = getChildren().stream().filter(Badge.class::isInstance).map(Badge.class::cast).findFirst().orElse(null);
    }

    public Signal0 pressed() {
        return pressed.signal();
    }

    public Signal<Boolean> toggled() {
        return toggled.signal();
    }

    public boolean getToggleButton() {
        return toggleButton;
    }

    public Button setToggleButton(boolean toggleButton) {
        this.toggleButton = toggleButton;
        if (!toggleButton) setButtonPressed(false);
        return this;
    }

    public boolean getButtonPressed() {
        return buttonPressed;
    }

    /**
     * Programmatic state updates never activate the button or play its sound.
     */
    public Button setButtonPressed(boolean value) {
        boolean previous = buttonPressed;
        setButtonPressedNoSignal(value);
        if (previous != buttonPressed) toggled.emit(buttonPressed);
        return this;
    }

    public Button setButtonPressedNoSignal(boolean value) {
        buttonPressed = toggleButton && value;
        return this;
    }

    public boolean getDisabled() {
        return disabled;
    }

    public Button setDisabled(boolean disabled) {
        this.disabled = disabled;
        if (disabled) cancelInteraction();
        return this;
    }

    @Override
    public boolean acceptsFocus() {
        return !disabled && super.acceptsFocus();
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        var icon = imageSize(iconControl, font);
        var trailingIcon = imageSize(trailingIconControl, font);
        int reserved = badgeReservation(font);
        int badgeMinimum = hasBadge() ? badge.measureWidth(font, available) + BADGE_PADDING.getInsets(this).getHorizontal() : 0;
        if (icon != null && !hasLabel() && trailingIcon == null)
            return Math.max(Math.max(getMinimumWidth(), badgeMinimum), Math.max(buttonSize.getHeight(), icon.width()
                    + (hasBadge() ? getThemeItem(getStyleKey(false, false, disabled)).getContentMargin().getHorizontal()
                    : ICON_PADDING.getInsets(this).getHorizontal())) + reserved);
        return Math.max(Math.max(getMinimumWidth(), badgeMinimum), label.measureWidth(font, available) + getThemeItem(getStyleKey(false, false, disabled)).getContentMargin().getHorizontal()
                + (icon == null ? 0 : icon.width() + (hasLabel() ? ICON_GAP : 0))
                + (trailingIcon == null ? 0 : trailingIcon.width() + ICON_GAP) + reserved);
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        var icon = imageSize(iconControl, font);
        var trailingIcon = imageSize(trailingIconControl, font);
        int iconHeight = Math.max(icon == null ? 0 : icon.height(), trailingIcon == null ? 0 : trailingIcon.height());
        var margin = getThemeItem(getStyleKey(false, false, disabled)).getContentMargin();
        // Single-line button text is optically centered; line leading must not enlarge SMALL buttons after a font reload.
        float textScale = label.getScale() * label.getThemeItem(Label.FONT_SIZE) / Label.DEFAULT_FONT_SIZE;
        int textHeight = hasLabel() && font != null ? (int) Math.ceil((label.getTextLayout(font, Integer.MAX_VALUE).getMetrics().opticalBottom() - label.getTextLayout(font, Integer.MAX_VALUE).getMetrics().opticalTop()))
                + margin.getVertical() : 0;
        int iconPadding = icon != null && !hasLabel() && trailingIcon == null ? ICON_PADDING.getInsets(this).getVertical() : margin.getVertical();
        int height = Math.max(Math.max(super.measureHeight(font, available), textHeight), iconHeight == 0 ? 0 : iconHeight + iconPadding);
        return Math.max(height, hasBadge() ? badge.measureHeight(font, available) + BADGE_PADDING.getInsets(this).getVertical() : 0);
    }

    private boolean hasBadge() {
        return badge != null && badge.getVisible();
    }

    private int badgeReservation(Font font) {
        return hasBadge() ? badge.measureWidth(font, Integer.MAX_VALUE) + ICON_GAP
                + Math.max(0, getThemeItem(BADGE_PADDING_RIGHT) - getThemeItem(NORMAL).getContentMargin().right()) : 0;
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            layoutBadge(font);
        }
    }

    private void layoutBadge(Font font) {
        if (!hasBadge()) return;
        var offset = getPresentationStyle(pointerHovered, visuallyPressed()).getContentOffset();
        io.github.luyingyilong.ore.layout.Size natural = badge.measureSize(font,
                new io.github.luyingyilong.ore.layout.Size(getSize().width(), getSize().height()));
        Rect inner = BADGE_PADDING.getInsets(this).inset(new Rect(0, 0, getSize().width(), getSize().height()));
        badge.layout(new Rect(inner.right() - natural.width() + offset.x(),
                inner.y() + (inner.height() - natural.height()) / 2 + offset.y(),
                natural.width(), natural.height()), font);
    }

    private boolean hasLabel() {
        return !label.getText().getString().isEmpty();
    }

    private boolean visuallyPressed() {
        return !disabled && (buttonPressed || activationKey != -1 || pointerPressed && pointerHovered);
    }

    @Override
    public Rect getFocusBounds() {
        return getPresentationStyle(pointerHovered, visuallyPressed()).getBackgroundRect(getAbsoluteBounds());
    }

    StyleBox getPresentationStyle(boolean hovered, boolean pressed) {
        return getThemeItem(getStyleKey(hovered, pressed, disabled));
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        boolean hovered = area.contains(context.getAbsMouseX(), context.getAbsMouseY());
        pointerHovered = hovered;
        layoutBadge(context.getFont());
        drawPresentation(context, area, hovered, visuallyPressed());
    }

    /**
     * Composite controls reuse Button's appearance while retaining their own activation behavior.
     */
    void drawPresentation(DrawContext context, Rect area, boolean hovered, boolean pressed) {
        if (!isAlive()) return;
        DebugBridge.backend().internal(this, "label", label);
        drawLabelled(this, context, area.x(), area.y(), area.width(), area.height(), label, iconControl, trailingIconControl,
                alignment, !disabled && hovered, !disabled && pressed, disabled, badgeReservation(context.getFont()));
    }

    @Override
    protected boolean pointerDown(int rootX, int rootY) {
        if (!hit(rootX, rootY)) return false;
        if (disabled) return true;
        if (pointerPressed) return true;

        pointerPressed = true;
        pointerHovered = true;
        if (actionMode == ACTION_MODE.BUTTON_PRESS) activate();
        return true;
    }

    @Override
    public boolean activate() {
        if (!getVisible() || disabled) return false;
        soundPlayer.accept(emphasizedSound ? OreSounds.Cue.BUTTON : OreSounds.Cue.CLICK);
        setButtonPressed(toggleButton ? !buttonPressed : true);
        pressed.emit();
        return true;
    }

    @Override
    protected boolean pointerUp(boolean over) {
        boolean armed = pointerPressed;
        pointerPressed = false;
        if (armed && over && actionMode == ACTION_MODE.BUTTON_RELEASE) activate();
        return armed;
    }

    @Override
    protected boolean pointerMove(int rootX, int rootY) {
        if (!pointerPressed) return false;
        pointerHovered = hit(rootX, rootY);
        return true;
    }

    @Override
    public boolean onActivationPressed(int keyCode) {
        if (!getVisible() || disabled) return false;
        if (activationKey != -1) return true;
        activationKey = keyCode;
        if (actionMode == ACTION_MODE.BUTTON_PRESS) activate();
        return true;
    }

    @Override
    public boolean onActivationReleased(int keyCode) {
        if (activationKey != keyCode) return false;
        activationKey = -1;
        if (actionMode == ACTION_MODE.BUTTON_RELEASE) activate();
        return true;
    }

    @Override
    public void cancelInteraction() {
        pointerPressed = false;
        pointerHovered = false;
        activationKey = -1;
    }

    @Override
    public void onFocusChanged(boolean focused) {
        if (!focused) cancelInteraction();
    }

    public String getThemeState() {
        return disabled ? "disabled" : (visuallyPressed() ? "pressed" : pointerHovered ? "hover" : "normal");
    }

    @Override
    public void debugProperties(java.util.List<DebugProperty> out) {
        super.debugProperties(out);
        out.add(DebugProperty.bool(label, "hero", "Text", label::getHero));
        out.add(DebugProperty.themeNumber(label, Label.FONT_SIZE, "Text", 0.0001, 256));
        out.add(DebugProperty.themeColor(this, FONT_COLOR, "Text"));
    }

    public enum TextAlignment {
        LEFT,
        CENTER,
        RIGHT,
    }

    public enum ACTION_MODE {
        BUTTON_PRESS,
        BUTTON_RELEASE,
    }

    public enum Size {
        SMALL(18),
        MEDIUM(20);

        private final int height;

        Size(int height) {
            this.height = height;
        }

        public int getHeight() {
            return height;
        }
    }

    record ContentLayout(Rect label, Rect icon, Rect trailingIcon) {
    }
}
