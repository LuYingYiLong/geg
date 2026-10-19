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

    private final Box box = new Box().gap(6);
    private final Label title = new Label().wrap(Label.Wrap.CHARACTER).color(OreTheme.TEXT_WHITE);
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
        title.visible(false);
        title.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        box.add(title);
        super.addChild(box);
        sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
    }

    public Descriptions title(Component text) {
        title.text(Objects.requireNonNull(text));
        title.visible(!text.getString().isEmpty());
        return this;
    }
    public Component title() { return title.text(); }
    public Label titleNode() { return title; }
    public Descriptions titleColor(int color) { title.color(color); return this; }
    public Descriptions titleFontSize(float pixels) { title.fontSize(pixels); return this; }
    public float titleFontSize() { return title.fontSize(); }
    public Descriptions labelColor(int color) {
        labelColor = color; for (DescriptionItem item : items.values()) item.labelNode().color(color); return this;
    }
    public int labelColor() { return labelColor; }
    public Descriptions contentColor(int color) {
        contentColor = color; for (DescriptionItem item : items.values()) item.contentNode().color(color); return this;
    }
    public int contentColor() { return contentColor; }
    public Descriptions fontSize(float pixels) {
        if (!(pixels > 0.0F) || !Float.isFinite(pixels)) throw new IllegalArgumentException("Font size must be positive and finite");
        fontSize = pixels;
        for (DescriptionItem item : items.values()) { item.labelNode().fontSize(pixels); item.contentNode().fontSize(pixels); }
        requestLayout();
        return this;
    }
    public float fontSize() { return fontSize; }
    public Descriptions layout(Layout layout) { this.layout = Objects.requireNonNull(layout); configureItems(); return this; }
    public Layout layout() { return layout; }
    public Descriptions colon(boolean colon) { this.colon = colon; configureItems(); return this; }
    public boolean colon() { return colon; }
    public Descriptions columns(int columns) {
        if (columns < 1) throw new IllegalArgumentException("At least one column is required");
        this.columns = columns; requestLayout(); return this;
    }
    public int columns() { return columns; }
    public Descriptions minimumColumnWidth(int pixels) {
        if (pixels < 1) throw new IllegalArgumentException("Column minimum must be positive");
        minimumColumnWidth = pixels; requestLayout(); return this;
    }
    public int minimumColumnWidth() { return minimumColumnWidth; }
    public Descriptions rowGap(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("Negative row gap");
        rowGap = pixels; box.gap(pixels); return this;
    }
    public int rowGap() { return rowGap; }
    public Descriptions columnGap(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("Negative column gap");
        columnGap = pixels; arrangedColumns = 0; requestLayout(); return this;
    }
    public int columnGap() { return columnGap; }
    public Descriptions itemGap(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("Negative item gap");
        itemGap = pixels; configureItems(); return this;
    }
    public int itemGap() { return itemGap; }

    /** Add values with stable keys, so callers can update individual labels without replacing nodes. */
    public Descriptions add(String key, Component label, Component content) {
        return add(new DescriptionItem(key, label, content));
    }
    public Descriptions add(DescriptionItem item) {
        Objects.requireNonNull(item);
        if (items.containsKey(item.key())) throw new IllegalArgumentException("Duplicate description: " + item.key());
        if (item.parent() != null) throw new IllegalStateException("Description already has a parent");
        item.configure(layout, colon, itemGap);
        item.labelNode().color(labelColor).fontSize(fontSize);
        item.contentNode().color(contentColor).fontSize(fontSize);
        items.put(item.key(), item);
        box.add(item);
        requestLayout();
        return this;
    }
    public DescriptionItem item(String key) {
        DescriptionItem item = items.get(key);
        if (item == null) throw new IllegalArgumentException("Unknown description: " + key);
        return item;
    }
    public List<DescriptionItem> items() { return List.copyOf(items.values()); }
    @Override public Descriptions addChild(Control child) {
        if (!(child instanceof DescriptionItem item)) throw new IllegalArgumentException("Descriptions accepts DescriptionItem only");
        return add(item);
    }
    @Override public boolean removeChild(Control child) {
        return child instanceof DescriptionItem item && items.get(item.key()) == item && remove(item.key());
    }
    public boolean remove(String key) {
        DescriptionItem item = items.remove(key);
        if (item == null) return false;
        item.parent().removeChild(item);
        requestLayout();
        return true;
    }
    public Descriptions clear() { for (String key : List.copyOf(items.keySet())) { DescriptionItem item = items.get(key); remove(key); item.queueFree(); } return this; }

    public int effectiveColumns(int width) {
        return Math.max(1, Math.min(columns, (int) (((long) Math.max(0, width) + columnGap) / ((long) columnMinimum() + columnGap))));
    }

    private int columnMinimum() {
        return Math.max(minimumColumnWidth, items.values().stream().filter(c -> c.isAlive() && c.visible()).mapToInt(Control::minimumWidth).max().orElse(0));
    }

    private void configureItems() {
        for (DescriptionItem item : items.values()) item.configure(layout, colon, itemGap);
        requestLayout();
    }

    private void arrange(int width) {
        List<DescriptionItem> visible = items.values().stream().filter(c -> c.isAlive() && c.visible()).toList();
        int count = effectiveColumns(width);
        if (arranged.equals(visible) && arrangedColumns == count) return;
        for (DescriptionItem item : items.values()) item.parent().removeChild(item);
        for (Box row : rows) { if (row.parent() != null) row.parent().removeChild(row); row.queueFree(); }
        rows.clear();
        for (int index = 0; index < visible.size(); index += count) {
            Box row = new Box().orientation(Box.Orientation.HORIZONTAL).gap(columnGap);
            row.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
            for (int column = 0; column < count; column++) {
                if (index + column < visible.size()) row.add(visible.get(index + column));
                else row.add(new Control().sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL));
            }
            rows.add(row);
            box.add(row);
        }
        // Hidden items remain owned by this descriptions tree and can be shown again later.
        for (DescriptionItem item : items.values()) if (!item.visible()) box.add(item);
        arranged = visible;
        arrangedColumns = count;
    }

    private Size measure(Font font, int offeredWidth) {
        int width = Math.max(minimumWidth(), items.values().stream().anyMatch(Control::visible)
                ? Math.max(columnMinimum(), offeredWidth) : offeredWidth);
        arrange(width);
        int height = title.visible() ? title.measureHeight(font, width) : 0;
        int columnWidth = Math.max(1, (width - Math.max(0, arrangedColumns - 1) * columnGap) / Math.max(1, arrangedColumns));
        for (Box row : rows) {
            int rowHeight = 0;
            for (Control child : row.children()) if (child instanceof DescriptionItem item) {
                rowHeight = Math.max(rowHeight, item.measureSize(font, new Size(columnWidth, Integer.MAX_VALUE)).height());
            }
            if (height > 0) height += rowGap;
            height += rowHeight;
        }
        return new Size(width, Math.max(minimumHeight(), height));
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
                int height = row.children().stream().filter(DescriptionItem.class::isInstance)
                        .mapToInt(child -> child.measureSize(font, new Size(columnWidth, Integer.MAX_VALUE)).height()).max().orElse(0);
                if (row.minimumHeight() != height) row.minimum(0, height);
            }
            fitChild(box, new Rect(0, 0, measured.width(), Math.max(measured.height(), bounds.height())), font);
        }
    }
}
