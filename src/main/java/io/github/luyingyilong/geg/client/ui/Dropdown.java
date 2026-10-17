package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.util.OreIcons;
import io.github.luyingyilong.geg.client.ui.util.OreSounds;
import io.github.luyingyilong.geg.client.ui.util.ReleaseGesture;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    private final Button triggerButton;
    private List<Option<T>> options = List.of();
    private T selected;
    private Component triggerLabel;
    private boolean checkable = true;
    private boolean showArrow = true;
    private Font menuFont;
    private int contentMenuWidth;
    private boolean menuMeasureDirty = true;
    private int offset;
    private boolean open;
    private int highlighted;
    private Consumer<T> onSelect = value -> { };
    private static final int TRIGGER = 0;
    private static final int OPTION = 1;
    private static final int DISMISS = 2;
    private final ReleaseGesture<Target<T>> gesture = new ReleaseGesture<>();
    private Rect pointerAnchor;

    public Dropdown(T initialValue, int maxVisibleItems) {
        this(initialValue, maxVisibleItems, Button.Size.MEDIUM);
    }

    public Dropdown(T initialValue, int maxVisibleItems, Button.Size size) {
        this.selected = initialValue;
        this.maxVisibleItems = Math.max(1, maxVisibleItems);
        this.size = Objects.requireNonNull(size);
        triggerButton = new Button().size(size).alignment(Button.TextAlignment.LEFT).trailingIcon(ARROW);
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
        requestLayout();
        return this;
    }

    public Dropdown<T> icon(Button.Icon icon) {
        triggerButton.icon(icon);
        requestLayout();
        return this;
    }
    public Dropdown<T> icon(ResourceLocation texture, int width, int height) {
        return icon(new Button.Icon(texture, width, height));
    }
    public Button.Icon icon() { return triggerButton.icon(); }
    public Dropdown<T> type(Button.Type type) { triggerButton.type(type); return this; }
    public Button.Type type() { return triggerButton.type(); }
    public Dropdown<T> showArrow(boolean showArrow) {
        this.showArrow = showArrow;
        triggerButton.trailingIcon(showArrow ? ARROW : null);
        requestLayout();
        return this;
    }
    public boolean showArrow() { return showArrow; }

    private Component triggerText() {
        return triggerLabel == null ? options.stream()
                .filter(option -> Objects.equals(option.value, selected))
                .findFirst().map(Option::label).orElse(Component.empty()) : triggerLabel;
    }

    @Override public int measureWidth(Font font, int available) {
        triggerButton.label(triggerText());
        return Math.max(super.measureWidth(font, available), triggerButton.measureWidth(font, available));
    }

    @Override public int measureHeight(Font font, int available) {
        return Math.max(super.measureHeight(font, available), triggerButton.measureHeight(font, available));
    }

    @Override public void layout(io.github.luyingyilong.geg.client.ui.layout.Rect bounds, Font font) {
        super.layout(bounds, font);
        measureMenu(font);
    }

    @Override public void invalidateTextLayout() {
        menuMeasureDirty = true;
        super.invalidateTextLayout();
    }

    public Dropdown<T> checkable(boolean checkable) {
        this.checkable = checkable;
        menuMeasureDirty = true;
        return this;
    }

    public int menuHeight() {
        int count = visibleCount();
        return 2 * PANEL_PADDING + count * size.height()
                - Math.max(0, count - 1) * ITEM_OVERLAP;
    }

    @Override protected boolean onPointerPressed(int rootX, int rootY) {
        if (!clickAt(0, 0, rootX, rootY)) return false;
        capturePointer();
        return true;
    }

    public void setOptions(List<Option<T>> options) {
        List<Option<T>> next = List.copyOf(options);
        if (!this.options.equals(next)) {
            cancelInteraction();
            menuMeasureDirty = true;
            requestLayout();
        }
        this.options = next;
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
        cancelInteraction();
        open = false;
        if (options.stream().anyMatch(option -> Objects.equals(option.value, value))) {
            boolean changed = !Objects.equals(selected, value);
            selected = value;
            if (changed) requestLayout();
            if (changed || !checkable) onSelect.accept(selected);
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        cancelInteraction();
    }

    @Override public boolean activate() {
        if (!visible() || options.isEmpty()) return false;
        perform(keyTarget());
        return true;
    }

    private Target<T> keyTarget() {
        return open && !options.isEmpty() ? new Target<>(OPTION, highlighted, options.get(highlighted).value)
                : new Target<>(TRIGGER, -1, null);
    }

    @Override public boolean onActivationPressed(int keyCode) {
        if (!visible() || options.isEmpty()) return false;
        gesture.keyDown(keyCode, keyTarget());
        return true;
    }

    @Override public boolean onActivationReleased(int keyCode) {
        var release = gesture.keyUp(keyCode, visible() && !options.isEmpty() ? keyTarget() : null);
        if (release.target() != null) perform(release.target());
        return release.handled();
    }

    @Override public boolean releasePointer(int rootX, int rootY, boolean over) {
        var area = absoluteBounds();
        return release(area.x(), area.y(), area.width(), area.height(), rootX, rootY, over);
    }

    /** Completes manually positioned triggers/menus using the original anchor. */
    public boolean releaseAt(int mouseX, int mouseY) {
        if (pointerAnchor == null) return false;
        Rect anchor = pointerAnchor;
        return release(anchor.x, anchor.y, anchor.width, anchor.height, mouseX, mouseY, true);
    }

    private boolean release(int x, int y, int width, int height, int mouseX, int mouseY, boolean allowed) {
        var release = gesture.pointerUp(targetAt(x, y, width, height, mouseX, mouseY), allowed && visible());
        pointerAnchor = null;
        if (release.target() != null) perform(release.target());
        return release.handled();
    }

    @Override public void cancelInteraction() { gesture.cancel(); pointerAnchor = null; }
    @Override public void onFocusChanged(boolean focused) { if (!focused) cancelInteraction(); }

    private void perform(Target<T> target) {
        if (target.kind == TRIGGER) {
            if (!options.isEmpty()) OreSounds.play(OreSounds.Cue.CLICK);
            open = !open && !options.isEmpty();
            if (open) { highlighted = selectedIndex(); revealHighlight(); }
        } else if (target.kind == OPTION) {
            OreSounds.play(OreSounds.Cue.CLICK);
            select(target.value);
        } else close();
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
        if (direction != FocusManager.Direction.UP && direction != FocusManager.Direction.DOWN) return false;
        cancelInteraction();
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
        drawTrigger(context, x, y, width, height, false);
    }

    public void drawTrigger(ModularGuiContext context, int x, int y, int width, int height, boolean pressed) {
        measureMenu(context.getFont());
        triggerButton.label(triggerText());
        boolean hovered = Button.contains(x, y, width, height, context.getAbsMouseX(), context.getAbsMouseY());
        Target<T> trigger = new Target<>(TRIGGER, -1, null);
        triggerButton.drawPresentation(context,
                new io.github.luyingyilong.geg.client.ui.layout.Rect(x, y, width, height), hovered,
                pressed || gesture.keyPressed(trigger) || hovered && gesture.pointerPressed(trigger));
    }

    public void drawTrigger(ModularGuiContext context, int x, int y, int width) {
        drawTrigger(context, x, y, width, size.height());
    }

    public void drawMenu(ModularGuiContext context, int x, int y, int width, int triggerHeight) {
        if (!open || options.isEmpty()) {
            return;
        }
        GuiGraphics graphics = context.getGraphics();
        measureMenu(context.getFont());
        Rect panel = menuBounds(x, y, width, triggerHeight);
        WidgetThemeEntry<WidgetTheme> panelEntry = context.getTheme()
                .getWidgetTheme(OreTheme.DROPDOWN_PANEL);
        WidgetTheme panelStyle = panelEntry != null
                ? panelEntry.theme() : context.getTheme().getPanelTheme().theme();
        drawBackground(context, panel, panelStyle);

        WidgetThemeEntry<WidgetTheme> itemEntry = context.getTheme()
                .getWidgetTheme(OreTheme.DROPDOWN_ITEM);
        WidgetTheme itemStyle = itemEntry != null ? itemEntry.theme() : panelStyle;
        for (int index = 0; index < visibleCount(); index++) {
            Option<T> option = options.get(offset + index);
            Rect item = itemBounds(panel, index);
            drawBackground(context, item, itemStyle);
            boolean hovered = item.contains(context.getAbsMouseX(), context.getAbsMouseY());
            Target<T> target = new Target<>(OPTION, offset + index, option.value);
            if (gesture.keyPressed(target) || hovered && gesture.pointerPressed(target)) {
                graphics.fill(item.x + 1, item.y + 1, item.right() - 1, item.bottom() - 1, 0x1A000000);
            } else if (hovered
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

    /** Begins a pointer gesture; call releaseAt on mouse release for detached menus. */
    public boolean click(int x, int y, int width, int triggerHeight,
                         int mouseX, int mouseY) {
        if (!visible()) return false;
        Target<T> target = targetAt(x, y, width, triggerHeight, mouseX, mouseY);
        if (target == null) return false;
        if (!gesture.pointerPending()) {
            pointerAnchor = new Rect(x, y, width, triggerHeight);
            gesture.pointerDown(target);
        }
        return true;
    }

    private Target<T> targetAt(int x, int y, int width, int triggerHeight, int mouseX, int mouseY) {
        if (Button.contains(x, y, width, triggerHeight, mouseX, mouseY))
            return options.isEmpty() ? null : new Target<>(TRIGGER, -1, null);
        if (!open) return null;
        Rect panel = menuBounds(x, y, width, triggerHeight);
        if (panel.contains(mouseX, mouseY)) {
            for (int index = visibleCount() - 1; index >= 0; index--) {
                if (itemBounds(panel, index).contains(mouseX, mouseY)) {
                    return new Target<>(OPTION, offset + index, options.get(offset + index).value);
                }
            }
        }
        return new Target<>(DISMISS, -1, null);
    }

    public boolean click(int x, int y, int width, int mouseX, int mouseY) {
        return click(x, y, width, size.height(), mouseX, mouseY);
    }

    public boolean scroll(double delta) {
        if (!open) {
            return false;
        }
        cancelInteraction();
        offset = Math.max(0, Math.min(Math.max(0, options.size() - maxVisibleItems),
                offset - (int) Math.signum(delta)));
        return true;
    }

    private int visibleCount() {
        return Math.min(maxVisibleItems, options.size() - offset);
    }

    private Rect menuBounds(int x, int y, int width, int triggerHeight) {
        int menuWidth = Math.max(width, contentMenuWidth);
        return new Rect(Math.max(0, x + width - menuWidth), y + triggerHeight, menuWidth, menuHeight());
    }

    private void measureMenu(Font font) {
        if (font == null || !menuMeasureDirty && menuFont == font) return;
        menuFont = font;
        int textWidth = 0;
        for (Option<T> option : options)
            textWidth = Math.max(textWidth, new Label(option.label).measureWidth(font, Integer.MAX_VALUE));
        contentMenuWidth = 2 * PANEL_PADDING + 2 * ITEM_PADDING + textWidth
                + (checkable ? CHECKED_SIZE + ITEM_PADDING : 0);
        menuMeasureDirty = false;
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

    private record Target<T>(int kind, int index, T value) { }

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
