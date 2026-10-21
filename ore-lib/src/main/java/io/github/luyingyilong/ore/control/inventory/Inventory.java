package io.github.luyingyilong.ore.control.inventory;

import io.github.luyingyilong.ore.control.display.Label;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.control.inventory.source.InventorySource;
import io.github.luyingyilong.ore.control.inventory.source.MenuInventorySource;
import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * A fixed-column slot view. Scrolling is deliberately supplied by an enclosing Scroll.
 */
public final class Inventory extends Container {
    public static final ThemeKey<Integer> GAP = new ThemeKey<>("Inventory", "gap", Integer.class, ThemeKey.Kind.INTEGER, 0, true, v -> v >= 0 && v <= 1024);
    public static final ThemeKey<Integer> SECTION_GAP = new ThemeKey<>("Inventory", "section_gap", Integer.class, ThemeKey.Kind.INTEGER, 4, true, v -> v >= 0 && v <= 1024);
    public static final ThemeKey<Integer> TITLE_GAP = new ThemeKey<>("Inventory", "title_gap", Integer.class, ThemeKey.Kind.INTEGER, 2, true, v -> v >= 0 && v <= 1024);
    /**
     * Zero follows font metrics; a positive height reserves a fixed title row for texture layouts.
     */
    public static final ThemeKey<Integer> TITLE_HEIGHT = new ThemeKey<>("Inventory", "title_height", Integer.class, ThemeKey.Kind.INTEGER, 0, true, v -> v >= 0 && v <= 1024);
    private final Label title = ownPart("title", new Label(Component.translatable("container.inventory"))
            .setShadow(false).setAlignment(Label.Horizontal.LEFT, Label.Vertical.TOP).setOverflow(Label.Overflow.ELLIPSIS));
    private final SignalSource<Integer> selectionChanged = new SignalSource<>(this, "selectionChanged", "Integer");
    private final SignalSource<Integer> slotActivated = new SignalSource<>(this, "slotActivated", "Integer");
    private final Map<Integer, Slot> slots = new LinkedHashMap<>();
    private InventorySource source;
    private List<Integer> indices = List.of();
    private Mode mode = Mode.READ_ONLY;
    private int columns = 9, selected = -1, sectionStart = -1;
    private boolean showTitle = true;
    public Inventory() {
        updateChildren(List.of(title));
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Inventory");
        db.register(GAP);
        db.register(SECTION_GAP);
        db.register(TITLE_GAP);
        db.register(TITLE_HEIGHT);
    }

    /**
     * Retained title for local text styling and positioning; owned by this Inventory.
     */
    public Label getTitleControl() {
        return title;
    }

    @Inspectable(group = "Appearance")
    public boolean getShowTitle() {
        return DebugBridge.value(this, "showTitle", showTitle);
    }

    public Inventory setShowTitle(boolean value) {
        if (DebugBridge.previewWrite(this, "showTitle", value)) return this;
        if (showTitle != value) {
            showTitle = value;
            title.setVisible(value);
            requestLayout();
        }
        return this;
    }

    @Override
    protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Inventory owns its slots; use setSource/setSlotIndices");
    }

    @Override
    protected void childrenChanged() {
        slots.values().removeIf(slot -> slot.getParent() != this);
        if (!isUpdatingChildren()) {
            indices = indices.stream().filter(slots::containsKey).toList();
            if (!indices.contains(selected) && selected != -1) {
                selected = -1;
                if (isAlive()) selectionChanged.emit(-1);
            }
        }
    }

    public InventorySource getSource() {
        return source;
    }

    public Inventory setSource(InventorySource next) {
        if (source == next) return this;
        if (next != null && next.isClosed()) throw new IllegalStateException("Inventory source is closed");
        if (mode == Mode.CONTAINER && !(next instanceof io.github.luyingyilong.ore.control.inventory.source.MenuInventorySource))
            throw new IllegalArgumentException("Container mode requires a Menu source");
        if (mode == Mode.CONTAINER) {
            var session = ((MenuInventorySource) next).getSession();
            if (session.getSource() != next) throw new IllegalArgumentException("Use the session's Menu source");
            for (int i = 0; i < next.getSlotCount(); i++) session.validateView(slots.get(i), i);
        }
        cancelInteractions();
        var old = List.copyOf(slots.values());
        updateChildren(List.of(title));
        slots.clear();
        old.forEach(Control::queueFree);
        source = next;
        selected = -1;
        sectionStart = -1;
        indices = next == null ? List.of() : java.util.stream.IntStream.range(0, next.getSlotCount()).boxed().toList();
        rebuild();
        return this;
    }

    public Mode getMode() {
        return mode;
    }

    public Inventory setMode(Mode value) {
        Objects.requireNonNull(value);
        if (mode == value) return this;
        if (value == Mode.CONTAINER && !(source instanceof MenuInventorySource))
            throw new IllegalArgumentException("Container mode requires a Menu source");
        if (value == Mode.CONTAINER) ((MenuInventorySource) source).getSession().validateViews(slots.values());
        cancelInteractions();
        mode = value;
        slots.values().forEach(slot -> slot.setMode(value));
        return this;
    }

    public int getColumns() {
        return columns;
    }

    public Inventory setColumns(int n) {
        if (n < 1 || n > 1024) throw new IllegalArgumentException("Invalid inventory columns");
        columns = n;
        sectionStart = -1;
        requestLayout();
        return this;
    }

    public List<Integer> getSlotIndices() {
        return indices;
    }

    public Inventory setSlotIndices(List<Integer> values) {
        Objects.requireNonNull(values);
        var next = List.copyOf(values);
        var unique = new HashSet<Integer>();
        for (int i : next) {
            if (source == null) throw new IllegalStateException("Set source first");
            Objects.checkIndex(i, source.getSlotCount());
            if (!unique.add(i)) throw new IllegalArgumentException("Duplicate slot index");
        }
        if (indices.equals(next)) return this;
        if (mode == Mode.CONTAINER) {
            var session = ((MenuInventorySource) source).getSession();
            for (int i : next) session.validateView(slots.get(i), i);
        }
        cancelInteractions();
        indices = next;
        sectionStart = -1;
        if (!indices.contains(selected)) setSelected(-1);
        rebuild();
        return this;
    }

    /**
     * Explicit menu IDs in display order: main inventory first, then hotbar.
     */
    public Inventory setPlayerSlots(List<Integer> main, List<Integer> hotbar) {
        if (main.size() != 27 || hotbar.size() != 9)
            throw new IllegalArgumentException("Player layout needs 27 main and 9 hotbar slots");
        var all = new ArrayList<>(main);
        all.addAll(hotbar);
        setSlotIndices(all);
        columns = 9;
        sectionStart = 27;
        requestLayout();
        return this;
    }

    public Slot getSlotControl(int sourceIndex) {
        return slots.get(sourceIndex);
    }

    public int getSelected() {
        return selected;
    }

    public Inventory setSelected(int value) {
        int old = selected;
        setSelectedNoSignal(value);
        if (old != selected) selectionChanged.emit(selected);
        return this;
    }

    public Inventory setSelectedNoSignal(int value) {
        if (value != -1 && !indices.contains(value)) throw new IllegalArgumentException("Selection is not displayed");
        selected = value;
        slots.forEach((i, slot) -> slot.setSelected(i == value));
        return this;
    }

    public Signal<Integer> selectionChanged() {
        return selectionChanged.signal();
    }

    public Signal<Integer> slotActivated() {
        return slotActivated.signal();
    }

    private void rebuild() {
        Map<Integer, Slot> previous = new LinkedHashMap<>(slots);
        var next = new ArrayList<Control>();
        next.add(title);
        slots.clear();
        for (int index : indices) {
            Slot slot = previous.remove(index);
            if (slot == null || !slot.isAlive()) {
                slot = new Slot().setBinding(source.getBinding(index)).setMode(mode);
                final Slot view = slot;
                slot.slotActivated().connect(this, i -> {
                    if (view.getParent() == this && source != null) {
                        setSelected(i);
                        slotActivated.emit(i);
                    }
                });
            }
            slot.setSelected(index == selected);
            slots.put(index, slot);
            next.add(slot);
        }
        updateChildren(next);
        previous.values().forEach(Control::queueFree);
        requestLayout();
    }

    private int cellWidth() {
        return slots.values().stream().filter(Control::isAlive).mapToInt(Control::getMinimumWidth).max().orElse(Slot.SIZE);
    }

    private int cellHeight() {
        return slots.values().stream().filter(Control::isAlive).mapToInt(Control::getMinimumHeight).max().orElse(Slot.SIZE);
    }

    private int rows() {
        return (indices.size() + columns - 1) / columns;
    }

    private int titleHeight(Font font) {
        if (!getShowTitle()) return 0;
        int height = getThemeItem(TITLE_HEIGHT);
        return height > 0 ? height : title.lineHeight(font);
    }

    private int slotsTop(Font font) {
        return titleHeight(font) + (getShowTitle() && !indices.isEmpty() ? getThemeItem(TITLE_GAP) : 0);
    }

    @Override
    public int getMinimumWidth() {
        return Math.max(super.getMinimumWidth(), indices.isEmpty() ? 0 : Math.min(columns, indices.size()) * cellWidth() + Math.max(0, Math.min(columns, indices.size()) - 1) * getThemeItem(GAP));
    }

    @Override
    public int getMinimumHeight() {
        return Math.max(super.getMinimumHeight(), slotsTop(getLayoutFont()) + rows() * cellHeight() + Math.max(0, rows() - 1) * getThemeItem(GAP) + (sectionStart > 0 ? getThemeItem(SECTION_GAP) : 0));
    }

    @Override
    public int measureWidth(Font font, int available) {
        return Math.max(getMinimumWidth(), getShowTitle() ? title.measureWidth(font, available) : 0);
    }

    @Override
    public int measureHeight(Font font, int available) {
        return getMinimumHeight();
    }

    @Override
    public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        title.setVisible(getShowTitle());
        // Lift the title visually without changing the reserved row or any slot positions.
        layoutChild(title, new Rect(0, -2, bounds.width(), titleHeight(font)), font);
        int w = cellWidth(), h = cellHeight(), gap = getThemeItem(GAP), top = slotsTop(font), i = 0;
        for (Slot child : List.copyOf(slots.values()))
            if (child.isAlive() && child.getParent() == this) {
                layoutChild(child, new Rect((i % columns) * (w + gap), top + (i / columns) * (h + gap) + (sectionStart > 0 && i >= sectionStart ? getThemeItem(SECTION_GAP) : 0), w, h), font);
                i++;
            }
    }

    public enum Mode {READ_ONLY, SELECT, CONTAINER}
}
