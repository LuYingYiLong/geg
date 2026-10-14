package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class Button {
    public static final int HEIGHT = Size.MEDIUM.height();
    public static final int PADDING_X = 8;
    public static final int PADDING_Y = 4;
    private static final int ICON_GAP = 4;

    private Button() {
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
        GuiGraphics graphics = context.getGraphics();
        boolean hovered = contains(x, y, width, height,
                context.getAbsMouseX(), context.getAbsMouseY());
        WidgetTheme style = context.getTheme().getButtonTheme().getTheme(hovered);
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
