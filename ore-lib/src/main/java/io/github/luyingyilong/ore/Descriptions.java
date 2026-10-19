package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Responsive read-only descriptions, composed entirely of retained Boxes and Labels without a background. */
public final class Descriptions extends Container {
    public enum Layout { HORIZONTAL, VERTICAL }

    private final Box box = new Box().setGap(6);
    private final Label title = new Label().setWrap(Label.Wrap.CHARACTER).setColor(OreTheme.TEXT_WHITE);
    private final Map<String, DescriptionItem> items = new LinkedHashMap<>();
    private final List<Box> rows = new ArrayList<>();
    private Layout layout = Layout.HORIZONTAL;
    private int columns = 3;
    private int minimumColumnWidth = 96;
    private int rowGap = 6;
    private int columnGap = 8;
    private int itemGap = 4;
    private boolean colon = true;
    private int labelColor = OreTheme.TEXT_SECONDARY;
    private int contentColor = OreTheme.TEXT_WHITE;
    private float fontSize = Label.DEFAULT_FONT_SIZE;
    private List<DescriptionItem> arranged = List.of();
    private int arrangedColumns;

    public Descriptions() {
        title.setVisible(false);
        title.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        box.add(title);
        super.addChild(box);
        setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
    }

    public Descriptions setTitle(Component text) {
        title.setText(Objects.requireNonNull(text));
        title.setVisible(!text.getString().isEmpty());
        return this;
    }
    public Component getTitle() { return title.getText(); }
    public Label getTitleNode() { return title; }
    public Descriptions setTitleColor(int color) { title.setColor(color); return this; }
    public Descriptions setTitleFontSize(float pixels) { title.setFontSize(pixels); return this; }
    public float getTitleFontSize() { return title.getFontSize(); }
    public Descriptions setLabelColor(int color) {
        labelColor = color; for (DescriptionItem item : items.values()) item.getLabelNode().setColor(color); return this;
    }
    public int getLabelColor() { return labelColor; }
    public Descriptions setContentColor(int color) {
        contentColor = color; for (DescriptionItem item : items.values()) item.getContentNode().setColor(color); return this;
    }
    public int getContentColor() { return contentColor; }
    public Descriptions setFontSize(float pixels) {
        if (!(pixels > 0.0F) || !Float.isFinite(pixels)) throw new IllegalArgumentException("Font size must be positive and finite");
        fontSize = pixels;
        for (DescriptionItem item : items.values()) { item.getLabelNode().setFontSize(pixels); item.getContentNode().setFontSize(pixels); }
        requestLayout();
        return this;
    }
    public float getFontSize() { return fontSize; }
    public Descriptions setLayout(Layout layout) { this.layout = Objects.requireNonNull(layout); configureItems(); return this; }
    public Layout getLayout() { return layout; }
    public Descriptions setColon(boolean colon) { this.colon = colon; configureItems(); return this; }
    public boolean getColon() { return colon; }
    public Descriptions setColumns(int columns) {
        if (columns < 1) throw new IllegalArgumentException("At least one column is required");
        this.columns = columns; requestLayout(); return this;
    }
    public int getColumns() { return columns; }
    public Descriptions setMinimumColumnWidth(int pixels) {
        if (pixels < 1) throw new IllegalArgumentException("Column minimum must be positive");
        minimumColumnWidth = pixels; requestLayout(); return this;
    }
    public int getMinimumColumnWidth() { return minimumColumnWidth; }
    public Descriptions setRowGap(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("Negative row gap");
        rowGap = pixels; box.setGap(pixels); return this;
    }
    public int getRowGap() { return rowGap; }
    public Descriptions setColumnGap(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("Negative column gap");
        columnGap = pixels; arrangedColumns = 0; requestLayout(); return this;
    }
    public int getColumnGap() { return columnGap; }
    public Descriptions setItemGap(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("Negative item gap");
        itemGap = pixels; configureItems(); return this;
    }
    public int getItemGap() { return itemGap; }

    /** Add values with stable keys, so callers can update individual labels without replacing nodes. */
    public Descriptions add(String key, Component label, Component content) {
        return add(new DescriptionItem(key, label, content));
    }
    public Descriptions add(DescriptionItem item) {
        Objects.requireNonNull(item);
        if (items.containsKey(item.getKey())) throw new IllegalArgumentException("Duplicate description: " + item.getKey());
        if (item.getParent() != null) throw new IllegalStateException("Description already has a parent");
        item.configure(layout, colon, itemGap);
        item.getLabelNode().setColor(labelColor).setFontSize(fontSize);
        item.getContentNode().setColor(contentColor).setFontSize(fontSize);
        items.put(item.getKey(), item);
        box.add(item);
        requestLayout();
        return this;
    }
    public DescriptionItem getItem(String key) {
        pruneFreedItems();
        DescriptionItem item = items.get(key);
        if (item == null) throw new IllegalArgumentException("Unknown description: " + key);
        return item;
    }
    public List<DescriptionItem> getItems() { pruneFreedItems(); return List.copyOf(items.values()); }
    @Override public Descriptions addChild(Control child) {
        if (!(child instanceof DescriptionItem item)) throw new IllegalArgumentException("Descriptions accepts DescriptionItem only");
        return add(item);
    }
    @Override public boolean removeChild(Control child) {
        return child instanceof DescriptionItem item && items.get(item.getKey()) == item && remove(item.getKey());
    }
    public boolean remove(String key) {
        DescriptionItem item = items.remove(key);
        if (item == null) return false;
        if (item.getParent() != null) item.getParent().removeChild(item);
        requestLayout();
        return true;
    }
    public Descriptions clear() { for (String key : List.copyOf(items.keySet())) { DescriptionItem item = items.get(key); remove(key); item.queueFree(); } return this; }

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
        pruneFreedItems();
        List<DescriptionItem> visible = items.values().stream().filter(c -> c.isAlive() && c.getVisible()).toList();
        int count = effectiveColumns(width);
        if (arranged.equals(visible) && arrangedColumns == count) return;
        for (DescriptionItem item : items.values()) if (item.getParent() != null) item.getParent().removeChild(item);
        for (Box row : rows) { if (row.getParent() != null) row.getParent().removeChild(row); row.queueFree(); }
        rows.clear();
        for (int index = 0; index < visible.size(); index += count) {
            Box row = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(columnGap);
            row.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
            for (int column = 0; column < count; column++) {
                if (index + column < visible.size()) row.add(visible.get(index + column));
                else row.add(new Control().setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL));
            }
            rows.add(row);
            box.add(row);
        }
        // Hidden items remain owned by this descriptions tree and can be shown again later.
        for (DescriptionItem item : items.values()) if (!item.getVisible()) box.add(item);
        arranged = visible;
        arrangedColumns = count;
    }

    private void pruneFreedItems() {
        items.values().removeIf(item -> {
            if (item.isAlive()) return false;
            if (item.getParent() != null) item.getParent().removeChild(item);
            return true;
        });
    }

    private Size measure(Font font, int offeredWidth) {
        int width = Math.max(getMinimumWidth(), items.values().stream().anyMatch(Control::getVisible)
                ? Math.max(columnMinimum(), offeredWidth) : offeredWidth);
        arrange(width);
        int height = title.getVisible() ? title.measureHeight(font, width) : 0;
        int columnWidth = Math.max(1, (width - Math.max(0, arrangedColumns - 1) * columnGap) / Math.max(1, arrangedColumns));
        for (Box row : rows) {
            int rowHeight = 0;
            for (Control child : row.getChildren()) if (child instanceof DescriptionItem item) {
                rowHeight = Math.max(rowHeight, item.measureSize(font, new Size(columnWidth, Integer.MAX_VALUE)).height());
            }
            if (height > 0) height += rowGap;
            height += rowHeight;
        }
        return new Size(width, Math.max(getMinimumHeight(), height));
    }

    @Override public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0); return measure(font, available.width()); }
    @Override public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0; return measure(font, available).width(); }
    @Override public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0; return measure(font, available).height(); }

    @Override public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            Size measured = measure(font, bounds.width());
            super.layout(bounds, font);
            // Boxes receive explicit measured row heights; each item still lays out its own label/value Box.
            int columnWidth = Math.max(1, (measured.width() - Math.max(0, arrangedColumns - 1) * columnGap) / Math.max(1, arrangedColumns));
            for (Box row : rows) {
                int height = row.getChildren().stream().filter(DescriptionItem.class::isInstance)
                        .mapToInt(child -> child.measureSize(font, new Size(columnWidth, Integer.MAX_VALUE)).height()).max().orElse(0);
                if (row.getMinimumHeight() != height) row.setMinimum(0, height);
            }
            fitChild(box, new Rect(0, 0, measured.width(), Math.max(measured.height(), bounds.height())), font);
        }
    }
}
