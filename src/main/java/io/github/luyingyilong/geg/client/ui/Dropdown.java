package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

public final class Dropdown<T> {
    public static final int ITEM_PADDING = 4;
    public static final int PANEL_PADDING = 1;
    public static final int ITEM_OVERLAP = 1;
    private static final int CHECKED_SIZE = 8;
    private static final int ARROW_SIZE = 8;
    private static final Button.Icon ARROW = new Button.Icon(OreIcons.ARROW_DOWN,
            ARROW_SIZE, ARROW_SIZE);

    private final int maxVisibleItems;
    private final Button.Size size;
    private List<Option<T>> options = List.of();
    private T selected;
    private int offset;
    private boolean open;

    public Dropdown(T initialValue, int maxVisibleItems) {
        this(initialValue, maxVisibleItems, Button.Size.MEDIUM);
    }

    public Dropdown(T initialValue, int maxVisibleItems, Button.Size size) {
        this.selected = initialValue;
        this.maxVisibleItems = Math.max(1, maxVisibleItems);
        this.size = Objects.requireNonNull(size);
    }

    public void setOptions(List<Option<T>> options) {
        this.options = List.copyOf(options);
        if (this.options.stream().noneMatch(option -> Objects.equals(option.value, selected))) {
            selected = this.options.isEmpty() ? null : this.options.get(0).value;
        }
        offset = Math.min(offset, Math.max(0, this.options.size() - maxVisibleItems));
        if (this.options.isEmpty()) {
            open = false;
        }
    }

    public T selected() {
        return selected;
    }

    public void select(T value) {
        if (options.stream().anyMatch(option -> Objects.equals(option.value, value))) {
            selected = value;
        }
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public void drawTrigger(ModularGuiContext context, int x, int y, int width, int height) {
        Component label = options.stream().filter(option -> Objects.equals(option.value, selected))
                .findFirst().map(Option::label).orElse(Component.empty());
        Button.draw(context, x, y, width, height, label, ARROW, Button.TextAlignment.LEFT);
    }

    public void drawTrigger(ModularGuiContext context, int x, int y, int width) {
        Component label = options.stream().filter(option -> Objects.equals(option.value, selected))
                .findFirst().map(Option::label).orElse(Component.empty());
        Button.draw(context, x, y, width, size, label, ARROW, Button.TextAlignment.LEFT);
    }

    public void drawMenu(ModularGuiContext context, int x, int y, int width, int triggerHeight) {
        if (!open || options.isEmpty()) {
            return;
        }
        GuiGraphics graphics = context.getGraphics();
        Rect panel = menuBounds(x, y, width, triggerHeight);
        WidgetThemeEntry<WidgetTheme> panelEntry = context.getTheme()
                .getWidgetTheme(OreTheme.DROPDOWN_PANEL);
        WidgetTheme panelStyle = panelEntry != null
                ? panelEntry.theme() : context.getTheme().getPanelTheme().theme();
        drawBackground(context, panel, panelStyle);

        WidgetThemeEntry<WidgetTheme> itemEntry = context.getTheme()
                .getWidgetTheme(OreTheme.MENU_ITEM);
        WidgetTheme itemStyle = itemEntry != null ? itemEntry.theme() : panelStyle;
        for (int index = 0; index < visibleCount(); index++) {
            Option<T> option = options.get(offset + index);
            Rect item = itemBounds(panel, index);
            drawBackground(context, item, itemStyle);
            if (item.contains(context.getAbsMouseX(), context.getAbsMouseY())) {
                graphics.fill(item.x + 1, item.y + 1,
                        item.right() - 1, item.bottom() - 1, 0x28FFFFFF);
            }
            boolean checked = Objects.equals(option.value, selected);
            int iconSpace = CHECKED_SIZE + ITEM_PADDING;
            int textWidth = Math.max(0, item.width - 2 * ITEM_PADDING - iconSpace);
            Label.draw(context, option.label, new io.github.luyingyilong.geg.client.ui.layout.Rect(
                            item.x + ITEM_PADDING, item.y, textWidth, item.height),
                    itemStyle.getTextColor(), itemStyle.isTextShadow(), Label.Overflow.CLIP,
                    Label.Horizontal.LEFT, Label.Vertical.CENTER);
            if (checked) {
                int iconX = item.right() - ITEM_PADDING - CHECKED_SIZE;
                int iconY = item.y + (item.height - CHECKED_SIZE) / 2;
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                graphics.blit(OreIcons.CHECKED, iconX, iconY, 0.0F, 0.0F,
                        CHECKED_SIZE, CHECKED_SIZE, CHECKED_SIZE, CHECKED_SIZE);
            }
        }
    }

    public boolean click(int x, int y, int width, int triggerHeight,
                         int mouseX, int mouseY) {
        if (Button.contains(x, y, width, triggerHeight, mouseX, mouseY)) {
            open = !open && !options.isEmpty();
            return true;
        }
        if (!open) {
            return false;
        }
        Rect panel = menuBounds(x, y, width, triggerHeight);
        if (panel.contains(mouseX, mouseY)) {
            for (int index = visibleCount() - 1; index >= 0; index--) {
                if (itemBounds(panel, index).contains(mouseX, mouseY)) {
                    selected = options.get(offset + index).value;
                    break;
                }
            }
        }
        open = false;
        return true;
    }

    public boolean click(int x, int y, int width, int mouseX, int mouseY) {
        return click(x, y, width, size.height(), mouseX, mouseY);
    }

    public boolean scroll(double delta) {
        if (!open) {
            return false;
        }
        offset = Math.max(0, Math.min(Math.max(0, options.size() - maxVisibleItems),
                offset - (int) Math.signum(delta)));
        return true;
    }

    private int visibleCount() {
        return Math.min(maxVisibleItems, options.size() - offset);
    }

    private Rect menuBounds(int x, int y, int width, int triggerHeight) {
        int count = visibleCount();
        int height = 2 * PANEL_PADDING + count * size.height()
                - Math.max(0, count - 1) * ITEM_OVERLAP;
        return new Rect(x, y + triggerHeight, width, height);
    }

    private Rect itemBounds(Rect panel, int index) {
        return new Rect(panel.x + PANEL_PADDING,
                panel.y + PANEL_PADDING + index * (size.height() - ITEM_OVERLAP),
                panel.width - 2 * PANEL_PADDING, size.height());
    }

    private static void drawBackground(ModularGuiContext context, Rect bounds, WidgetTheme style) {
        IDrawable background = style.getBackground();
        if (background != null) {
            background.draw(context, bounds.x, bounds.y, bounds.width, bounds.height, style);
        }
    }

    public record Option<T>(T value, Component label) {
    }

    private record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(int mouseX, int mouseY) {
            return Button.contains(x, y, width, height, mouseX, mouseY);
        }
    }
}
