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

public final class Button extends Control {
    public static final int HEIGHT = Size.MEDIUM.height();
    public static final int PADDING_X = 8;
    public static final int PADDING_Y = 4;
    private static final int ICON_GAP = 4;

    private Component label = Component.empty();
    private Size buttonSize = Size.MEDIUM;
    private Type type = Type.SECONDARY;
    private TextAlignment alignment = TextAlignment.CENTER;
    private Icon trailingIcon;
    private Runnable onPress = () -> { };

    public Button() {
        minimum(0, buttonSize.height());
    }

    public Button(Component label) {
        this();
        label(label);
    }

    public Button label(Component label) { this.label = java.util.Objects.requireNonNull(label); requestLayout(); return this; }
    public Button size(Size size) { this.buttonSize = java.util.Objects.requireNonNull(size); minimum(0, size.height()); return this; }
    public Button type(Type type) { this.type = java.util.Objects.requireNonNull(type); return this; }
    public Type type() { return type; }
    public Button alignment(TextAlignment alignment) { this.alignment = java.util.Objects.requireNonNull(alignment); return this; }
    public Button trailingIcon(Icon icon) { this.trailingIcon = icon; requestLayout(); return this; }
    public Button onPress(Runnable action) { this.onPress = java.util.Objects.requireNonNull(action); return this; }

    @Override public int measureWidth(Font font, int available) {
        return Math.max(minimumWidth(), font.width(label) + 2 * PADDING_X
                + (trailingIcon == null ? 0 : trailingIcon.width + ICON_GAP));
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = screenBounds(screenX, screenY);
        draw(context, area.x(), area.y(), area.width(), area.height(), label, trailingIcon, alignment, type);
    }

    public boolean pressAt(int rootX, int rootY) {
        if (!hit(rootX, rootY)) return false;
        onPress.run();
        return true;
    }

    @Override protected boolean onPointerPressed(int rootX, int rootY) {
        return pressAt(rootX, rootY);
    }

    public static void draw(ModularGuiContext context, int x, int y, int width,
                            Component label) {
        draw(context, x, y, width, Size.MEDIUM, label);
    }

    public static void draw(ModularGuiContext context, int x, int y, int width,
                            Size size, Component label) {
        draw(context, x, y, width, size.height(), label, null);
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
        GuiGraphics graphics = context.getGraphics();
        boolean hovered = contains(x, y, width, height,
                context.getAbsMouseX(), context.getAbsMouseY());
        WidgetTheme style = theme(context.getTheme(), type, hovered, hovered && leftMousePressed());
        IDrawable background = style.getBackground();
        if (background != null) {
            background.draw(context, x, y, width, height, style);
        }

        int iconSpace = trailingIcon == null ? 0 : trailingIcon.width + ICON_GAP;
        int labelWidth = Math.max(0, width - 2 * PADDING_X - iconSpace);
        Label.draw(context, label, new io.github.luyingyilong.geg.client.ui.layout.Rect(
                        x + PADDING_X, y, labelWidth, height), style.getTextColor(), style.isTextShadow(),
                Label.Overflow.ELLIPSIS, switch (alignment) {
                    case LEFT -> Label.Horizontal.LEFT;
                    case CENTER -> Label.Horizontal.CENTER;
                    case RIGHT -> Label.Horizontal.RIGHT;
                }, Label.Vertical.CENTER);

        if (trailingIcon != null) {
            int iconX = x + width - PADDING_X - trailingIcon.width;
            int iconY = y + (height - trailingIcon.height) / 2;
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(trailingIcon.texture, iconX, iconY, 0.0F, 0.0F,
                    trailingIcon.width, trailingIcon.height,
                    trailingIcon.width, trailingIcon.height);
        }
    }

    static WidgetTheme theme(ITheme theme, Type type, boolean hovered, boolean pressed) {
        WidgetThemeKey<WidgetTheme> key = switch (type) {
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

    static boolean leftMousePressed() {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    public static boolean contains(int x, int y, int width, int height,
                                   int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public record Icon(ResourceLocation texture, int width, int height) {
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
