package io.github.luyingyilong.ore.control.display;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.layout.Box;

import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeKey;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Responsive read-only descriptions, composed entirely of retained Boxes and Labels without a background.
 */
public final class Descriptions extends Container {
    public static final ThemeKey<Float> TITLE_FONT_SIZE = new ThemeKey<>(
            "DescriptionsTitle", "font_size", Float.class, ThemeKey.Kind.FLOAT, 8.0F, true,
            value -> Float.isFinite(value) && value > 0);
    public static final ThemeKey<Integer> TITLE_FONT_COLOR = ThemeKey.of("DescriptionsTitle", "font_color", Integer.class,
            ThemeKey.Kind.COLOR, OreTheme.TEXT_WHITE, false);
    public static final ThemeKey<Integer> LABEL_FONT_COLOR = ThemeKey.of("DescriptionsLabel", "font_color", Integer.class,
            ThemeKey.Kind.COLOR, OreTheme.TEXT_SECONDARY, false);
    public static final ThemeKey<Integer> CONTENT_FONT_COLOR = ThemeKey.of("DescriptionsContent", "font_color", Integer.class,
            ThemeKey.Kind.COLOR, OreTheme.TEXT_WHITE, false);
    private final Box box = new Box().setGap(6);
    private final Label title = new Label().setThemeTypeVariation("DescriptionsTitle").setWrap(Label.Wrap.CHARACTER);
    private final Map<String, DescriptionItem> items = new LinkedHashMap<>();
    private final List<Box> rows = new ArrayList<>();
    private final ArrayDeque<Control> spareCells = new ArrayDeque<>();
    private Layout layout = Layout.HORIZONTAL;
    private int columns = 3;
    private int minimumColumnWidth = 96;
    private int rowGap = 6;
    private int columnGap = 8;
    private int itemGap = 4;
    private boolean colon = true;
    private List<DescriptionItem> arranged = List.of();
    private int arrangedColumns;
    private boolean arranging;
    public Descriptions() {
        title.setThemeParent(this);
        title.setVisible(false);
        title.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        box.addChild(title);
        updateChildren(List.of(box));
        setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Descriptions");
        db.registerVariation("DescriptionsTitle", "Label");
        db.registerVariation("DescriptionsLabel", "LabelSecondary");
        db.registerVariation("DescriptionsContent", "Label");
        db.register(TITLE_FONT_SIZE);
        db.register(TITLE_FONT_COLOR);
        db.register(LABEL_FONT_COLOR);
        db.register(CONTENT_FONT_COLOR);
    }

    private static void collectItems(Control node, List<DescriptionItem> out) {
        if (node instanceof DescriptionItem item) {
            out.add(item);
            return;
        }
        for (Control child : node.getChildren()) collectItems(child, out);
    }

    private record TreeState(Map<String, DescriptionItem> items, List<DescriptionItem> arranged, int columns) { }
    @Override protected Object captureTreeState() { return new TreeState(new LinkedHashMap<>(items), arranged, arrangedColumns); }
    @Override protected void restoreTreeState(Object state) {
        if (state instanceof TreeState saved) { items.clear(); items.putAll(saved.items()); arranged = saved.arranged(); arrangedColumns = saved.columns(); }
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public Component getTitle() {
        return title.getText();
    }

    public Descriptions setTitle(Component text) {
        title.setText(Objects.requireNonNull(text));
        title.setVisible(!text.getString().isEmpty());
        return this;
    }

    public Label getTitleNode() {
        return title;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public Layout getLayout() {
        return layout;
    }

    public Descriptions setLayout(Layout layout) {
        this.layout = Objects.requireNonNull(layout);
        configureItems();
        return this;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public boolean getColon() {
        return colon;
    }

    public Descriptions setColon(boolean colon) {
        this.colon = colon;
        configureItems();
        return this;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public int getColumns() {
        return columns;
    }

    public Descriptions setColumns(int columns) {
        if (columns < 1) throw new IllegalArgumentException("At least one column is required");
        this.columns = columns;
        requestLayout();
        return this;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public int getMinimumColumnWidth() {
        return minimumColumnWidth;
    }

    public Descriptions setMinimumColumnWidth(int pixels) {
        if (pixels < 1) throw new IllegalArgumentException("Column minimum must be positive");
        minimumColumnWidth = pixels;
        requestLayout();
        return this;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public int getRowGap() {
        return rowGap;
    }

    public Descriptions setRowGap(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("Negative row gap");
        rowGap = pixels;
        box.setGap(pixels);
        return this;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public int getColumnGap() {
        return columnGap;
    }

    public Descriptions setColumnGap(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("Negative column gap");
        columnGap = pixels;
        arrangedColumns = 0;
        requestLayout();
        return this;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public int getItemGap() {
        return itemGap;
    }

    public Descriptions setItemGap(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("Negative item gap");
        itemGap = pixels;
        configureItems();
        return this;
    }

    /**
     * Add values with stable keys, so callers can update individual labels without replacing nodes.
     */
    public Descriptions addItem(String key, Component label, Component content) {
        return addItem(new DescriptionItem(key, label, content));
    }

    public Descriptions addItem(DescriptionItem item) {
        Objects.requireNonNull(item);
        if (items.containsKey(item.getKey()))
            throw new IllegalArgumentException("Duplicate description: " + item.getKey());
        if (item.getParent() != null) throw new IllegalStateException("Description already has a parent");
        item.configure(layout, colon, itemGap);
        item.setThemeParent(this);
        box.addChild(item);
        requestLayout();
        return this;
    }

    public DescriptionItem getItem(String key) {
        DescriptionItem item = items.get(key);
        if (item == null) throw new IllegalArgumentException("Unknown description: " + key);
        return item;
    }

    public List<DescriptionItem> getItems() {
        return List.copyOf(items.values());
    }

    @Override
    protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Descriptions has a fixed skeleton; use addItem/removeItem/clearItems");
    }

    @Override
    protected void childrenChanged() {
        if (arranging) return;
        rows.removeIf(row -> row.getParent() != box);
        List<DescriptionItem> present = new ArrayList<>();
        collectItems(box, present);
        items.values().removeIf(item -> !item.isAlive() || !present.contains(item));
        for (DescriptionItem item : present) if (item.isAlive()) items.putIfAbsent(item.getKey(), item);
        arrangedColumns = 0;
    }

    public boolean removeItem(String key) {
        DescriptionItem item = items.remove(key);
        if (item == null) return false;
        if (item.getParent() != null) item.getParent().removeChild(item);
        requestLayout();
        return true;
    }

    public Descriptions clearItems() {
        for (String key : List.copyOf(items.keySet())) {
            DescriptionItem item = items.get(key);
            removeItem(key);
            item.queueFree();
        }
        return this;
    }

    public int effectiveColumns(int width) {
        return Math.max(1, Math.min(columns, (int) (((long) Math.max(0, width) + columnGap) / ((long) columnMinimum() + columnGap))));
    }

    private int columnMinimum() {
        return Math.max(minimumColumnWidth, items.values().stream().filter(c -> c.isAlive() && c.getVisible()).mapToInt(Control::getMinimumWidth).max().orElse(0));
    }

    private void configureItems() {
        for (DescriptionItem item : items.values()) item.configure(layout, colon, itemGap);
        requestLayout();
    }

    private void arrange(int width) {
        List<DescriptionItem> visible = items.values().stream().filter(c -> c.isAlive() && c.getVisible()).toList();
        int count = effectiveColumns(width);
        if (arranged.equals(visible) && arrangedColumns == count) return;
        arranging = true;
        try {
            for (DescriptionItem item : List.copyOf(items.values()))
                if (item.getParent() != null) item.getParent().removeChild(item);
            // Keep row identities across responsive layouts; unused cells retain pool ownership.
            for (Box row : rows) for (Control child : row.getChildren()) {
                row.removeChild(child);
                if (child.isAlive()) spareCells.addLast(child);
            }
            int rowIndex = 0;
            for (int index = 0; index < visible.size(); index += count) {
                if (rowIndex == rows.size()) {
                    Box row = ownPooled(new Box().setOrientation(Box.Orientation.HORIZONTAL));
                    row.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
                    rows.add(row);
                    box.addChild(row);
                }
                Box row = rows.get(rowIndex++);
                row.setGap(columnGap).setVisible(true);
                for (int column = 0; column < count; column++) {
                    if (index + column < visible.size()) {
                        row.addChild(visible.get(index + column));
                    } else {
                        spareCells.removeIf(cell -> !cell.isAlive());
                        Control cell = spareCells.isEmpty()
                                ? ownPooled(new Control().setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL))
                                : spareCells.removeFirst();
                        row.addChild(cell);
                    }
                }
            }
            for (int index = rowIndex; index < rows.size(); index++) rows.get(index).setVisible(false);
            // Hidden items remain owned by this descriptions tree and can be shown again later.
            for (DescriptionItem item : items.values())
                if (!item.getVisible()) {
                    box.addChild(item);
                }
            arranged = visible;
            arrangedColumns = count;
        } finally {
            arranging = false;
        }
    }

    private Size measure(Font font, int offeredWidth) {
        int width = Math.max(getMinimumWidth(), items.values().stream().anyMatch(Control::getVisible)
                ? Math.max(columnMinimum(), offeredWidth) : offeredWidth);
        // Scroll may probe several widths before choosing a viewport. Measurement must not
        // reparent items or create rendering controls for those provisional widths.
        List<DescriptionItem> visible = items.values().stream().filter(c -> c.isAlive() && c.getVisible()).toList();
        int count = effectiveColumns(width);
        int height = title.getVisible() ? title.measureHeight(font, width) : 0;
        int columnWidth = Math.max(1, (width - Math.max(0, count - 1) * columnGap) / count);
        for (int index = 0; index < visible.size(); index += count) {
            int rowHeight = 0;
            for (int column = index; column < Math.min(visible.size(), index + count); column++)
                rowHeight = Math.max(rowHeight, visible.get(column).measureSize(font, new Size(columnWidth, Integer.MAX_VALUE)).height());
            if (height > 0) height += rowGap;
            height += rowHeight;
        }
        return new Size(width, Math.max(getMinimumHeight(), height));
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        return measure(font, available.width());
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return measure(font, available).width();
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return measure(font, available).height();
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            Size measured = measure(font, bounds.width());
            arrange(measured.width());
            super.layout(bounds, font);
            // Boxes receive explicit measured row heights; each item still lays out its own label/value Box.
            int columnWidth = Math.max(1, (measured.width() - Math.max(0, arrangedColumns - 1) * columnGap) / Math.max(1, arrangedColumns));
            for (Box row : rows) {
                if (!row.getVisible()) continue;
                int height = row.getChildren().stream().filter(DescriptionItem.class::isInstance)
                        .mapToInt(child -> child.measureSize(font, new Size(columnWidth, Integer.MAX_VALUE)).height()).max().orElse(0);
                if (row.getMinimumHeight() != height) row.setMinimum(0, height);
            }
            fitChild(box, new Rect(0, 0, measured.width(), Math.max(measured.height(), bounds.height())), font);
        }
    }

    public enum Layout {HORIZONTAL, VERTICAL}
}
