package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Panel;
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
    private final Box stack = new Box();
    private final Control headerSlot = new Control();
    private final Control contentSlot = new Control();
    private final Control footerSlot = new Control();
    private final Panel background = new Panel(OreTheme.MODAL);

    public Modal(int width, int height, int verticalOffset, Component title) {
        this.preferredWidth = width;
        this.preferredHeight = height;
        this.verticalOffset = verticalOffset;
        this.title = title;
        headerSlot.minimum(0, HEADER_HEIGHT).preferred(0, HEADER_HEIGHT)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        contentSlot.policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        footerSlot.minimum(0, FOOTER_HEIGHT).preferred(0, FOOTER_HEIGHT)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        stack.add(headerSlot).add(contentSlot).add(footerSlot);
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
        layoutSlots(bounds);
        io.github.luyingyilong.geg.client.ui.layout.Rect area = contentSlot.bounds();
        return new Rect(bounds.x + PADDING, bounds.y + area.y(),
                Math.max(0, area.width() - 2 * PADDING), area.height());
    }

    private void layoutSlots(Rect bounds) {
        stack.layout(new io.github.luyingyilong.geg.client.ui.layout.Rect(
                0, 0, bounds.width, bounds.height), null);
    }

    public void draw(ModularGuiContext context, Rect viewport, Consumer<Rect> drawContent) {
        GuiGraphics graphics = context.getGraphics();
        Rect bounds = bounds(viewport.x, viewport.y, viewport.width, viewport.height);
        graphics.fill(viewport.x, viewport.y, viewport.right(), viewport.bottom(), OVERLAY_COLOR);

        WidgetThemeEntry<WidgetTheme> entry = context.getTheme().getWidgetTheme(OreTheme.MODAL);
        WidgetTheme style = entry != null ? entry.theme() : context.getTheme().getPanelTheme().theme();
        background.drawAt(context, new io.github.luyingyilong.geg.client.ui.layout.Rect(
                bounds.x, bounds.y, bounds.width, bounds.height));

        int titleWidth = Math.max(0, bounds.width - 3 * PADDING - CLOSE_SIZE);
        Label.draw(context, title, new io.github.luyingyilong.geg.client.ui.layout.Rect(
                        bounds.x + PADDING, bounds.y + 8, titleWidth, 20),
                style.getTextColor(), style.isTextShadow(), Label.Overflow.CLIP,
                Label.Horizontal.LEFT, Label.Vertical.CENTER);
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
        layoutSlots(bounds);
        return new Rect(bounds.right() - PADDING - CLOSE_SIZE,
                bounds.y + headerSlot.bounds().y() + PADDING,
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
        layoutSlots(bounds);
        int count = actions.size();
        int innerWidth = bounds.width - 2 * PADDING;
        int buttonWidth = Math.min(132, (innerWidth - (count - 1) * BUTTON_GAP) / count);
        int space = count == 1 ? 0 : (innerWidth - count * buttonWidth) / (count - 1);
        int startX = count == 1 ? bounds.right() - PADDING - buttonWidth : bounds.x + PADDING;
        return new Rect(startX + index * (buttonWidth + space),
                bounds.y + footerSlot.bounds().y() + footerSlot.bounds().height()
                        - PADDING - BUTTON_HEIGHT, buttonWidth, BUTTON_HEIGHT);
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
