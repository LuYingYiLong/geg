package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.ITheme;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.theme.WidgetThemeKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public final class Button extends Control {
    public static final int HEIGHT = Size.MEDIUM.height();
    public static final int PADDING_X = 8;
    private static final int ICON_GAP = 4;
    private static final int ICON_PADDING = 4;

    private final Label label = new Label();
    private Size buttonSize = Size.MEDIUM;
    private Type type = Type.SECONDARY;
    private TextAlignment alignment = TextAlignment.CENTER;
    private Icon icon;
    private Icon trailingIcon;
    private Runnable onPress = () -> { };
    private final Consumer<OreSounds.Cue> soundPlayer;
    private boolean emphasizedSound;
    private ACTION_MODE actionMode = ACTION_MODE.BUTTON_RELEASE;
    private boolean pointerPressed;
    private int activationKey = -1;

    public Button() {
        this(OreSounds::play);
    }

    Button(Consumer<OreSounds.Cue> soundPlayer) {
        this.soundPlayer = java.util.Objects.requireNonNull(soundPlayer);
        minimum(0, buttonSize.height());
        focusable(true);
        focusExpansion(1);
    }

    public Button(Component label) {
        this();
        label(label);
    }

    public Button label(Component label) { this.label.text(label); requestLayout(); return this; }
    public Button hero(boolean hero) { label.hero(hero); requestLayout(); return this; }
    public boolean hero() { return label.hero(); }
    public Button fontSize(float fontSize) { label.fontSize(fontSize); requestLayout(); return this; }
    public float fontSize() { return label.fontSize(); }
    public Button size(Size size) { this.buttonSize = java.util.Objects.requireNonNull(size); minimum(0, size.height()); return this; }
    public Button type(Type type) { this.type = java.util.Objects.requireNonNull(type); return this; }
    public Type type() { return type; }
    public Button emphasizedSound(boolean emphasized) { emphasizedSound = emphasized; return this; }
    public boolean emphasizedSound() { return emphasizedSound; }
    public Button actionMode(ACTION_MODE actionMode) {
        this.actionMode = java.util.Objects.requireNonNull(actionMode);
        cancelInteraction();
        return this;
    }
    public ACTION_MODE actionMode() { return actionMode; }
    public Button alignment(TextAlignment alignment) { this.alignment = java.util.Objects.requireNonNull(alignment); return this; }
    /** Leading icon with text, or a centered icon when the label is empty. Null clears it. */
    public Button icon(Icon icon) { this.icon = icon; requestLayout(); return this; }
    public Button icon(ResourceLocation texture, int width, int height) { return icon(new Icon(texture, width, height)); }
    public Icon icon() { return icon; }
    public Button trailingIcon(Icon icon) { this.trailingIcon = icon; requestLayout(); return this; }
    public Button onPress(Runnable action) { this.onPress = java.util.Objects.requireNonNull(action); return this; }

    @Override public int measureWidth(Font font, int available) {
        if (icon != null && !hasLabel() && trailingIcon == null)
            return Math.max(minimumWidth(), Math.max(buttonSize.height(), icon.width + 2 * ICON_PADDING));
        return Math.max(minimumWidth(), label.measureWidth(font, available) + 2 * PADDING_X
                + (icon == null ? 0 : icon.width + (hasLabel() ? ICON_GAP : 0))
                + (trailingIcon == null ? 0 : trailingIcon.width + ICON_GAP));
    }

    @Override public int measureHeight(Font font, int available) {
        int iconHeight = Math.max(icon == null ? 0 : icon.height, trailingIcon == null ? 0 : trailingIcon.height);
        return Math.max(super.measureHeight(font, available), iconHeight == 0 ? 0 : iconHeight + 2 * ICON_PADDING);
    }

    private boolean hasLabel() { return !label.getText().getString().isEmpty(); }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = screenBounds(screenX, screenY);
        boolean hovered = area.contains(context.getAbsMouseX(), context.getAbsMouseY());
        drawLabelled(context, area.x(), area.y(), area.width(), area.height(), label, icon, trailingIcon,
                alignment, type, hovered, activationKey != -1 || pointerPressed && hovered);
    }

    public boolean pressAt(int rootX, int rootY) {
        if (!hit(rootX, rootY)) return false;
        if (pointerPressed) return true;
        pointerPressed = true;
        if (actionMode == ACTION_MODE.BUTTON_PRESS) activate();
        return true;
    }

    @Override protected boolean onPointerPressed(int rootX, int rootY) {
        return pressAt(rootX, rootY);
    }

    @Override public boolean activate() {
        if (!visible()) return false;
        soundPlayer.accept(emphasizedSound ? OreSounds.Cue.BUTTON : OreSounds.Cue.CLICK);
        onPress.run();
        return true;
    }

    @Override public boolean releasePointer(boolean over) {
        boolean armed = pointerPressed;
        pointerPressed = false;
        if (armed && over && actionMode == ACTION_MODE.BUTTON_RELEASE) activate();
        return armed;
    }

    @Override public boolean onActivationPressed(int keyCode) {
        if (activationKey != -1) return true;
        activationKey = keyCode;
        if (actionMode == ACTION_MODE.BUTTON_PRESS) activate();
        return true;
    }

    @Override public boolean onActivationReleased(int keyCode) {
        if (activationKey != keyCode) return false;
        activationKey = -1;
        if (actionMode == ACTION_MODE.BUTTON_RELEASE) activate();
        return true;
    }

    @Override public void cancelInteraction() { pointerPressed = false; activationKey = -1; }
    @Override public void onFocusChanged(boolean focused) { if (!focused) cancelInteraction(); }

    public static void draw(ModularGuiContext context, int x, int y, int width,
                            Component label) {
        draw(context, x, y, width, Size.MEDIUM, label);
    }

    public static void draw(ModularGuiContext context, int x, int y, int width,
                            Size size, Component label) {
        draw(context, x, y, width, size.height(), label, (Icon) null);
    }

    public static void draw(ModularGuiContext context, int x, int y, int width,
                            int height, Component label, Type type) {
        draw(context, x, y, width, height, label, null, TextAlignment.CENTER, type);
    }

    public static void draw(ModularGuiContext context, int x, int y, int width,
                            Size size, Component label, TextAlignment alignment) {
        draw(context, x, y, width, size.height(), label, null, alignment);
    }

    public static void draw(ModularGuiContext context, int x, int y, int width,
                            Size size, Component label, Icon trailingIcon,
                            TextAlignment alignment) {
        draw(context, x, y, width, size.height(), label, trailingIcon, alignment);
    }

    static void draw(ModularGuiContext context, int x, int y, int width,
                     int height, Component label, Icon trailingIcon) {
        draw(context, x, y, width, height, label, trailingIcon, TextAlignment.CENTER);
    }

    static void draw(ModularGuiContext context, int x, int y, int width,
                     int height, Component label, Icon trailingIcon,
                     TextAlignment alignment) {
        draw(context, x, y, width, height, label, trailingIcon, alignment, Type.SECONDARY);
    }

    static void draw(ModularGuiContext context, int x, int y, int width,
                     int height, Component label, Icon trailingIcon,
                     TextAlignment alignment, Type type) {
        drawLabelled(context, x, y, width, height, new Label(label), trailingIcon, alignment, type);
    }

    private static void drawLabelled(ModularGuiContext context, int x, int y, int width,
                                     int height, Label label, Icon trailingIcon,
                                     TextAlignment alignment, Type type) {
        boolean hovered = contains(x, y, width, height,
                context.getAbsMouseX(), context.getAbsMouseY());
        drawLabelled(context, x, y, width, height, label, null, trailingIcon, alignment,
                type, hovered, hovered && leftMousePressed());
    }

    private static void drawLabelled(ModularGuiContext context, int x, int y, int width,
                                     int height, Label label, Icon icon, Icon trailingIcon, TextAlignment alignment,
                                     Type type, boolean hovered, boolean pressed) {
        GuiGraphics graphics = context.getGraphics();
        WidgetTheme style = theme(context.getTheme(), type, hovered, pressed);
        IDrawable background = style.getBackground();
        if (type == Type.TEXT) {
            int color = textBackgroundColor(hovered, pressed);
            if (color != 0) graphics.fill(x, y, x + width, y + height, color);
        } else if (background != null) {
            background.draw(context, x, y, width, height, style);
        }

        boolean hasLabel = !label.getText().getString().isEmpty();
        ContentLayout content = contentLayout(new Rect(x, y, width, height),
                hasLabel ? label.measureWidth(context.getFont(), width) : 0, hasLabel, icon, trailingIcon, alignment);
        if (hasLabel) {
            label.color(style.getTextColor()).shadow(style.isTextShadow())
                .overflow(Label.Overflow.ELLIPSIS).align(switch (alignment) {
                    case LEFT -> Label.Horizontal.LEFT;
                    case CENTER -> Label.Horizontal.CENTER;
                    case RIGHT -> Label.Horizontal.RIGHT;
                }, Label.Vertical.CENTER);
            label.layout(content.label(), context.getFont());
            label.draw(context, 0, 0);
        }
        drawIcon(graphics, icon, content.icon());
        drawIcon(graphics, trailingIcon, content.trailingIcon());
    }

    static ContentLayout contentLayout(Rect area, int textWidth, boolean hasLabel,
                                       Icon icon, Icon trailingIcon, TextAlignment alignment) {
        Rect trailing = trailingIcon == null ? null : new Rect(
                area.right() - PADDING_X - trailingIcon.width,
                area.y() + (area.height() - trailingIcon.height) / 2, trailingIcon.width, trailingIcon.height);
        if (icon != null && !hasLabel && trailingIcon == null)
            return new ContentLayout(new Rect(area.x(), area.y(), 0, area.height()),
                    new Rect(area.x() + (area.width() - icon.width) / 2,
                            area.y() + (area.height() - icon.height) / 2, icon.width, icon.height), null);
        int available = Math.max(0, area.width() - 2 * PADDING_X
                - (trailingIcon == null ? 0 : trailingIcon.width + ICON_GAP));
        if (icon == null)
            return new ContentLayout(new Rect(area.x() + PADDING_X, area.y(), available, area.height()), null, trailing);
        int gap = hasLabel ? ICON_GAP : 0;
        int labelWidth = hasLabel ? Math.min(textWidth, Math.max(0, available - icon.width - gap)) : 0;
        int freeSpace = Math.max(0, available - icon.width - gap - labelWidth);
        int left = area.x() + PADDING_X + switch (alignment) {
            case LEFT -> 0;
            case CENTER -> freeSpace / 2;
            case RIGHT -> freeSpace;
        };
        return new ContentLayout(new Rect(left + icon.width + gap, area.y(), labelWidth, area.height()),
                new Rect(left, area.y() + (area.height() - icon.height) / 2, icon.width, icon.height), trailing);
    }

    record ContentLayout(Rect label, Rect icon, Rect trailingIcon) { }

    private static void drawIcon(GuiGraphics graphics, Icon icon, Rect area) {
        if (icon != null) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(icon.texture, area.x(), area.y(), 0.0F, 0.0F,
                    icon.width, icon.height, icon.width, icon.height);
        }
    }

    static WidgetTheme theme(ITheme theme, Type type, boolean hovered, boolean pressed) {
        WidgetThemeKey<WidgetTheme> key = switch (type) {
            case TEXT -> OreTheme.BUTTON_SECONDARY_NORMAL;
            case SECONDARY -> pressed ? OreTheme.BUTTON_SECONDARY_PRESSED
                    : hovered ? OreTheme.BUTTON_SECONDARY_HOVERED : OreTheme.BUTTON_SECONDARY_NORMAL;
            case PRIMARY -> pressed ? OreTheme.BUTTON_PRIMARY_PRESSED
                    : hovered ? OreTheme.BUTTON_PRIMARY_HOVERED : OreTheme.BUTTON_PRIMARY_NORMAL;
            case DESTRUCTIVE -> pressed ? OreTheme.BUTTON_DESTRUCTIVE_PRESSED
                    : hovered ? OreTheme.BUTTON_DESTRUCTIVE_HOVERED : OreTheme.BUTTON_DESTRUCTIVE_NORMAL;
        };
        WidgetThemeEntry<WidgetTheme> entry = theme.getWidgetTheme(key);
        return entry != null ? entry.theme() : theme.getButtonTheme().getTheme(hovered);
    }

    static int textBackgroundColor(boolean hovered, boolean pressed) {
        return pressed ? 0x1A000000 : hovered ? 0x16FFFFFF : 0;
    }

    static boolean leftMousePressed() {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    public static boolean contains(int x, int y, int width, int height,
                                   int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public record Icon(ResourceLocation texture, int width, int height) {
        public Icon {
            java.util.Objects.requireNonNull(texture);
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Icon dimensions must be positive");
        }
    }

    public enum TextAlignment {
        LEFT,
        CENTER,
        RIGHT,
    }

    public enum Type {
        SECONDARY,
        PRIMARY,
        DESTRUCTIVE,
        TEXT,
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

        public int height() {
            return height;
        }
    }
}
