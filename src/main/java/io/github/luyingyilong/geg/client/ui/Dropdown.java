package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.FocusManager;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class Dropdown<T> extends Control {
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
    private Component triggerLabel;
    private boolean checkable = true;
    private int offset;
    private boolean open;
    private int highlighted;
    private Consumer<T> onSelect = value -> { };

    public Dropdown(T initialValue, int maxVisibleItems) {
        this(initialValue, maxVisibleItems, Button.Size.MEDIUM);
    }

    public Dropdown(T initialValue, int maxVisibleItems, Button.Size size) {
        this.selected = initialValue;
        this.maxVisibleItems = Math.max(1, maxVisibleItems);
        this.size = Objects.requireNonNull(size);
        minimum(0, size.height());
        focusable(true);
        focusExpansion(1);
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        io.github.luyingyilong.geg.client.ui.layout.Rect area = screenBounds(screenX, screenY);
        drawTrigger(context, area.x(), area.y(), area.width(), area.height());
    }

    public boolean clickAt(int screenX, int screenY, int mouseX, int mouseY) {
        io.github.luyingyilong.geg.client.ui.layout.Rect area = screenBounds(screenX, screenY);
        return click(area.x(), area.y(), area.width(), area.height(), mouseX, mouseY);
    }

    public Dropdown<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = Objects.requireNonNull(onSelect);
        return this;
    }

    public Dropdown<T> triggerLabel(Component label) {
        triggerLabel = Objects.requireNonNull(label);
        return this;
    }

    public Dropdown<T> checkable(boolean checkable) {
        this.checkable = checkable;
        return this;
    }

    public int menuHeight() {
        int count = visibleCount();
        return 2 * PANEL_PADDING + count * size.height()
                - Math.max(0, count - 1) * ITEM_OVERLAP;
    }

    @Override protected boolean onPointerPressed(int rootX, int rootY) {
        return clickAt(0, 0, rootX, rootY);
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
        highlighted = Math.max(0, Math.min(highlighted, this.options.size() - 1));
    }

    public T selected() {
        return selected;
    }

    public void select(T value) {
        if (options.stream().anyMatch(option -> Objects.equals(option.value, value))) {
            boolean changed = !Objects.equals(selected, value);
            selected = value;
            if (changed) onSelect.accept(selected);
        }
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    @Override public boolean activate() {
        if (!options.isEmpty()) OreSounds.play(OreSounds.Cue.CLICK);
        if (open && !options.isEmpty()) select(options.get(highlighted).value);
        else {
            open = !options.isEmpty();
            highlighted = selectedIndex();
            revealHighlight();
        }
        return true;
    }

    @Override public boolean onFocusKey(int keyCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && open) {
            close();
            return true;
        }
        return false;
    }

    @Override public boolean onFocusMove(FocusManager.Direction direction) {
        if (!open || options.isEmpty()) return false;
        if (direction == FocusManager.Direction.UP) highlighted = Math.max(0, highlighted - 1);
        else if (direction == FocusManager.Direction.DOWN)
            highlighted = Math.min(options.size() - 1, highlighted + 1);
        else return false;
        revealHighlight();
        return true;
    }

    private int selectedIndex() {
        for (int index = 0; index < options.size(); index++)
            if (Objects.equals(options.get(index).value, selected)) return index;
        return 0;
    }

    private void revealHighlight() {
        if (highlighted < offset) offset = highlighted;
        else if (highlighted >= offset + maxVisibleItems)
            offset = highlighted - maxVisibleItems + 1;
    }

    public void drawTrigger(ModularGuiContext context, int x, int y, int width, int height) {
        Component label = triggerLabel == null ? options.stream()
                .filter(option -> Objects.equals(option.value, selected))
                .findFirst().map(Option::label).orElse(Component.empty()) : triggerLabel;
        Button.draw(context, x, y, width, height, label, ARROW, Button.TextAlignment.LEFT);
    }

    public void drawTrigger(ModularGuiContext context, int x, int y, int width) {
        Component label = triggerLabel == null ? options.stream()
                .filter(option -> Objects.equals(option.value, selected))
                .findFirst().map(Option::label).orElse(Component.empty()) : triggerLabel;
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
            if (item.contains(context.getAbsMouseX(), context.getAbsMouseY())
                    || offset + index == highlighted) {
                graphics.fill(item.x + 1, item.y + 1,
                        item.right() - 1, item.bottom() - 1, 0x28FFFFFF);
            }
            boolean checked = checkable && Objects.equals(option.value, selected);
            int iconSpace = checkable ? CHECKED_SIZE + ITEM_PADDING : 0;
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
            if (!options.isEmpty()) OreSounds.play(OreSounds.Cue.CLICK);
            open = !open && !options.isEmpty();
            if (open) { highlighted = selectedIndex(); revealHighlight(); }
            return true;
        }
        if (!open) {
            return false;
        }
        Rect panel = menuBounds(x, y, width, triggerHeight);
        if (panel.contains(mouseX, mouseY)) {
            for (int index = visibleCount() - 1; index >= 0; index--) {
                if (itemBounds(panel, index).contains(mouseX, mouseY)) {
                    OreSounds.play(OreSounds.Cue.CLICK);
                    select(options.get(offset + index).value);
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
        return new Rect(x, y + triggerHeight, width, menuHeight());
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
