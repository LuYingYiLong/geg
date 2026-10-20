package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.theme.ThemePadding;
import io.github.luyingyilong.ore.theme.IconTheme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;

import io.github.luyingyilong.ore.render.*;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.OreSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * Retained trigger. The owning Window creates and routes its Overlay menu.
 */
public final class Dropdown<T> extends Control {
    private static final ThemePadding PADDING = ThemePadding.of("Dropdown", "", 1, 1, 1, 1);
    public static final ThemeKey<Integer> PADDING_TOP = PADDING.top();
    public static final ThemeKey<Integer> PADDING_RIGHT = PADDING.right();
    public static final ThemeKey<Integer> PADDING_BOTTOM = PADDING.bottom();
    public static final ThemeKey<Integer> PADDING_LEFT = PADDING.left();


    public static final ThemeKey<TextureRegion> ARROW_ICON = IconTheme.item("Dropdown", "arrow", IconTheme.ARROW_DOWN);
    public static final ThemeKey<TextureRegion> CHECKED_ICON = IconTheme.item("Dropdown", "checked", IconTheme.CHECKED);

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Dropdown");
        PADDING.register(db);
        IconTheme.registerTheme(db);
        db.register(ARROW_ICON);
        db.registerFallback(ARROW_ICON, IconTheme.ARROW_DOWN);
        db.register(CHECKED_ICON);
        db.registerFallback(CHECKED_ICON, IconTheme.CHECKED);
    }


    @Override public String getThemeState() { return "selected="+String.valueOf(selected)+", open="+open; }
    public static final int ITEM_OVERLAP = 1;
    private static final TextureSource ARROW = TextureSource.themed(ARROW_ICON);
    private final Button triggerButton;
    private final Button.Size size;
    private final int maxVisibleItems;
    private final SignalSource<T> selectionChanged = new SignalSource<>(this, "selectionChanged", "T");
    private final SignalSource<T> itemActivated = new SignalSource<>(this, "itemActivated", "T");
    private List<Option<T>> options = List.of();
    private T selected;
    private Component triggerLabel;
    private boolean checkable = true, showArrow = true, open, held;
    private int activationKey = -1;

    public Dropdown(T initialValue, int maxVisibleItems) {
        this(initialValue, maxVisibleItems, Button.Size.MEDIUM);
    }

    public Dropdown(T initialValue, int maxVisibleItems, Button.Size size) {
        selected = initialValue;
        this.maxVisibleItems = Math.max(1, maxVisibleItems);
        this.size = Objects.requireNonNull(size);
        triggerButton = ownPart("trigger", new Button().setSize(size).setAlignment(Button.TextAlignment.LEFT).setTrailingIcon(ARROW));
        setMinimum(0, size.getHeight());
        setFocusable(true);

    }

    public Signal<T> selectionChanged() {
        return selectionChanged.signal();
    }

    public Signal<T> itemActivated() {
        return itemActivated.signal();
    }

    public Dropdown<T> setSelectedNoSignal(T value) {
        if (value != null && options.stream().noneMatch(option -> Objects.equals(option.value(), value)))
            throw new IllegalArgumentException("Unknown dropdown option");
        if (!Objects.equals(selected, value)) {
            selected = value;
            requestLayout();
        }
        return this;
    }

    public Dropdown<T> setTriggerLabel(Component label) {
        triggerLabel = Objects.requireNonNull(label);
        requestLayout();
        return this;
    }

    public TextureSource getIcon() {
        return triggerButton.getIcon();
    }

    public Dropdown<T> setIcon(TextureSource icon) {
        triggerButton.setIcon(icon);
        requestLayout();
        return this;
    }

    public TextureRect getIconControl() { return triggerButton.getIconControl(); }
    public TextureRect getArrowControl() { return triggerButton.getTrailingIconControl(); }

    /** Presentation part; Dropdown retains activation and Overlay routing. */
    public Button getTriggerButton() { return triggerButton; }

    public boolean getShowArrow() {
        return showArrow;
    }

    public Dropdown<T> setShowArrow(boolean value) {
        showArrow = value;
        triggerButton.setTrailingIcon(value ? ARROW : null);
        requestLayout();
        return this;
    }

    public boolean getCheckable() {
        return checkable;
    }

    public Dropdown<T> setCheckable(boolean value) {
        checkable = value;
        return this;
    }

    public Button.Size getControlSize() {
        return size;
    }

    public int getMaxVisibleItems() {
        return maxVisibleItems;
    }

    public List<Option<T>> getOptions() {
        return options;
    }

    public void setOptions(List<Option<T>> values) {
        List<Option<T>> next = List.copyOf(values);
        if (!options.equals(next)) {
            close();
            options = next;
            requestLayout();
        }
        if (options.stream().noneMatch(option -> Objects.equals(option.value(), selected)))
            setSelected(options.isEmpty() ? null : options.get(0).value());
    }

    public T getSelected() {
        return selected;
    }

    public Dropdown<T> setSelected(T value) {
        T before = selected;
        setSelectedNoSignal(value);
        if (!Objects.equals(before, selected)) selectionChanged.emit(selected);
        return this;
    }

    public boolean isOpen() {
        return open;
    }

    public void select(T value) {
        close();
        if (options.stream().noneMatch(option -> Objects.equals(option.value(), value))) return;
        setSelected(value);
        itemActivated.emit(value);
    }

    public void close() {
        open = false;
        cancelInteraction();
    }

    @Override
    public void cancelInteraction() {
        held = false;
        activationKey = -1;
    }

    @Override
    public void onFocusChanged(boolean focused) {
        if (!focused) cancelInteraction();
    }

    @Override
    public boolean acceptsFocus() {
        return super.acceptsFocus() && !options.isEmpty();
    }

    private Component text() {
        return triggerLabel != null ? triggerLabel : options.stream().filter(option -> Objects.equals(option.value(), selected)).findFirst().map(Option::label).orElse(Component.empty());
    }

    @Override
    public int measureWidth(Font font, int available) {
        triggerButton.setLabel(text());
        return Math.max(super.measureWidth(font, available), triggerButton.measureWidth(font, available));
    }

    @Override
    public int measureHeight(Font font, int available) {
        return Math.max(super.measureHeight(font, available), triggerButton.measureHeight(font, available));
    }

    @Override
    protected void render(DrawContext context, int x, int y) {
        Rect area = getScreenBounds(x, y);
        triggerButton.setLabel(text());
        DebugBridge.backend().internal(this, "trigger", triggerButton);
        boolean hovered = area.contains(context.getAbsMouseX(), context.getAbsMouseY());
        triggerButton.drawPresentation(context, area, hovered, activationKey != -1 || held && hovered);
    }

    @Override
    protected boolean pointerDown(int x, int y) {
        if (!hit(x, y) || options.isEmpty()) return false;
        held = true;
        return true;
    }

    @Override
    protected boolean pointerUp(int x, int y, boolean over) {
        boolean pending = held;
        held = false;
        if (pending && over) activate();
        return pending;
    }

    @Override
    public boolean activate() {
        if (!getVisible() || options.isEmpty()) return false;
        OreSounds.play(OreSounds.Cue.CLICK);
        open = !open;
        return true;
    }

    @Override
    public boolean onActivationPressed(int key) {
        if (!acceptsFocus()) return false;
        if (activationKey == -1) activationKey = key;
        return true;
    }

    @Override
    public boolean onActivationReleased(int key) {
        if (activationKey != key) return false;
        activationKey = -1;
        return activate();
    }

    public record Option<T>(T value, Component label) {
    }
}
