package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A themed overlay with a fixed header, eight-pixel content inset, and footer actions. */
public final class Modal {
    public static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 36;
    private static final int BUTTON_HEIGHT = Button.Size.MEDIUM.height();
    private static final int BUTTON_GAP = 8;
    private static final int CLOSE_SIZE = 18;
    private static final int CLOSE_ICON_SIZE = 8;
    private static final int OVERLAY_COLOR = 0xB8000000;

    private final int preferredWidth;
    private final int preferredHeight;
    private final int verticalOffset;
    private final Component title;
    private final List<Action> actions = new ArrayList<>();

    public Modal(int width, int height, int verticalOffset, Component title) {
        this.preferredWidth = width;
        this.preferredHeight = height;
        this.verticalOffset = verticalOffset;
        this.title = title;
    }

    public Modal action(Component label, Runnable onClick) {
        actions.add(new Action(label, onClick));
        return this;
    }

    public Rect bounds(int x, int y, int width, int height) {
        int modalWidth = Math.min(preferredWidth, width - 12);
        return new Rect(x + (width - modalWidth) / 2,
                y + (height - preferredHeight) / 2 + verticalOffset,
                modalWidth, preferredHeight);
    }

    public Rect content(Rect bounds) {
        return new Rect(bounds.x + PADDING, bounds.y + HEADER_HEIGHT,
                bounds.width - 2 * PADDING,
                bounds.height - HEADER_HEIGHT - FOOTER_HEIGHT);
    }

    public void draw(ModularGuiContext context, Rect viewport, Consumer<Rect> drawContent) {
        GuiGraphics graphics = context.getGraphics();
        Font font = context.getFont();
        Rect bounds = bounds(viewport.x, viewport.y, viewport.width, viewport.height);
        graphics.fill(viewport.x, viewport.y, viewport.right(), viewport.bottom(), OVERLAY_COLOR);

        WidgetThemeEntry<WidgetTheme> entry = context.getTheme().getWidgetTheme(OreTheme.MODAL);
        WidgetTheme style = entry != null ? entry.theme() : context.getTheme().getPanelTheme().theme();
        IDrawable background = style.getBackground();
        if (background != null) {
            background.draw(context, bounds.x, bounds.y, bounds.width, bounds.height, style);
        }

        int titleWidth = Math.max(0, bounds.width - 3 * PADDING - CLOSE_SIZE);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), titleWidth),
                bounds.x + PADDING, bounds.y + 11, style.getTextColor(), style.isTextShadow());
        drawCloseButton(context, closeButton(bounds));
        drawContent.accept(content(bounds));
        for (int i = 0; i < actions.size(); i++) {
            Rect button = actionButton(bounds, i);
            Button.draw(context, button.x, button.y, button.width, Button.Size.MEDIUM,
                    actions.get(i).label);
        }
    }

    public boolean click(Rect bounds, int mouseX, int mouseY, Runnable onClose) {
        if (!bounds.contains(mouseX, mouseY) || closeButton(bounds).contains(mouseX, mouseY)) {
            onClose.run();
            return true;
        }
        for (int i = 0; i < actions.size(); i++) {
            if (actionButton(bounds, i).contains(mouseX, mouseY)) {
                actions.get(i).onClick.run();
                return true;
            }
        }
        return false;
    }

    private Rect closeButton(Rect bounds) {
        return new Rect(bounds.right() - PADDING - CLOSE_SIZE, bounds.y + PADDING,
                CLOSE_SIZE, CLOSE_SIZE);
    }

    private static void drawCloseButton(ModularGuiContext context, Rect bounds) {
        GuiGraphics graphics = context.getGraphics();
        if (bounds.contains(context.getAbsMouseX(), context.getAbsMouseY())) {
            graphics.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(), 0x66303030);
        }
        int iconX = bounds.x + (bounds.width - CLOSE_ICON_SIZE) / 2;
        int iconY = bounds.y + (bounds.height - CLOSE_ICON_SIZE) / 2;
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(OreIcons.SMALL_CLOSE, iconX, iconY, 0.0F, 0.0F,
                CLOSE_ICON_SIZE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE);
    }

    private Rect actionButton(Rect bounds, int index) {
        int count = actions.size();
        int innerWidth = bounds.width - 2 * PADDING;
        int buttonWidth = Math.min(132, (innerWidth - (count - 1) * BUTTON_GAP) / count);
        int space = count == 1 ? 0 : (innerWidth - count * buttonWidth) / (count - 1);
        int startX = count == 1 ? bounds.right() - PADDING - buttonWidth : bounds.x + PADDING;
        return new Rect(startX + index * (buttonWidth + space),
                bounds.bottom() - PADDING - BUTTON_HEIGHT, buttonWidth, BUTTON_HEIGHT);
    }

    private record Action(Component label, Runnable onClick) {
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }
}
