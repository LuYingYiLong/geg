package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.OreIcons;
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
    public static final int ITEM_PADDING = 4, PANEL_PADDING = 1, ITEM_OVERLAP = 1;
    private static final Button.Icon ARROW = new Button.Icon(OreIcons.ARROW_DOWN, 8, 8);
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
        setFocusExpansion(1);
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

    public Dropdown<T> setIcon(ResourceLocation texture, int width, int height) {
        return setIcon(new Button.Icon(texture, width, height));
    }

    public Button.Icon getIcon() {
        return triggerButton.getIcon();
    }

    public Dropdown<T> setIcon(Button.Icon icon) {
        triggerButton.setIcon(icon);
        requestLayout();
        return this;
    }

    public Button.Type getType() {
        return triggerButton.getType();
    }

    public Dropdown<T> setType(Button.Type type) {
        triggerButton.setType(type);
        return this;
    }

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
