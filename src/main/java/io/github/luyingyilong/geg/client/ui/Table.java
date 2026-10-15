package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Panel;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/** Fixed-header table whose rows use the Ore bar theme and shared draw/hit geometry. */
public final class Table<T> extends Container {
    public interface Cell<T> {
        void draw(ModularGuiContext context, T row, Rect bounds);

        default int measureHeight(Font font, T row, int width) {
            return 0;
        }

        default boolean click(T row, Rect bounds, int mouseX, int mouseY) {
            return false;
        }
    }

    /** Text cell with configurable clipping and wrapping; its measured height feeds row layout. */
    public static final class TextCell<T> implements Cell<T> {
        private final Function<T, Component> text;
        private final int color;
        private Label.Overflow overflow = Label.Overflow.ELLIPSIS;
        private Label.Wrap wrap = Label.Wrap.NONE;
        private Label.Horizontal horizontal = Label.Horizontal.LEFT;
        private Label.Vertical vertical = Label.Vertical.CENTER;
        private int maxLines = Integer.MAX_VALUE;
        private int horizontalInset = 4;
        private int verticalInset = 2;

        public TextCell(Function<T, Component> text, int color) {
            this.text = Objects.requireNonNull(text);
            this.color = color;
        }

        public TextCell<T> overflow(Label.Overflow overflow) {
            this.overflow = Objects.requireNonNull(overflow);
            return this;
        }

        public TextCell<T> wrap(Label.Wrap wrap) {
            this.wrap = Objects.requireNonNull(wrap);
            return this;
        }

        public TextCell<T> maxLines(int maxLines) {
            if (maxLines < 1) throw new IllegalArgumentException("At least one line is required");
            this.maxLines = maxLines;
            return this;
        }

        public TextCell<T> align(Label.Horizontal horizontal, Label.Vertical vertical) {
            this.horizontal = Objects.requireNonNull(horizontal);
            this.vertical = Objects.requireNonNull(vertical);
            return this;
        }

        public TextCell<T> inset(int horizontal, int vertical) {
            if (horizontal < 0 || vertical < 0) throw new IllegalArgumentException("Negative cell inset");
            horizontalInset = horizontal;
            verticalInset = vertical;
            return this;
        }

        @Override public int measureHeight(Font font, T row, int width) {
            if (font == null) return 0;
            Label label = label(row);
            return label.measureHeight(font, Math.max(1, width - 2 * horizontalInset))
                    + 2 * verticalInset;
        }

        @Override public void draw(ModularGuiContext context, T row, Rect bounds) {
            Rect inner = new Rect(bounds.x() + horizontalInset, bounds.y() + verticalInset,
                    Math.max(0, bounds.width() - 2 * horizontalInset),
                    Math.max(0, bounds.height() - 2 * verticalInset));
            if (inner.width() == 0 || inner.height() == 0) return;
            Label label = label(row);
            label.layout(inner, context.getFont());
            label.draw(context, 0, 0);
        }

        private Label label(T row) {
            return new Label(Objects.requireNonNull(text.apply(row))).color(color)
                    .overflow(overflow).wrap(wrap).maxLines(maxLines).align(horizontal, vertical);
        }
    }

    public static final class Column<T> {
        private final String key;
        private final Component title;
        private final int minimumWidth;
        private final int preferredWidth;
        private final int grow;
        private final IntUnaryOperator compactHeight;
        private final Cell<T> cell;

        public Column(String key, Component title, int minimumWidth, int preferredWidth,
                      int grow, IntUnaryOperator compactHeight, Cell<T> cell) {
            this.key = Objects.requireNonNull(key);
            this.title = Objects.requireNonNull(title);
            this.minimumWidth = Math.max(1, minimumWidth);
            this.preferredWidth = Math.max(this.minimumWidth, preferredWidth);
            this.grow = Math.max(0, grow);
            this.compactHeight = Objects.requireNonNull(compactHeight);
            this.cell = Objects.requireNonNull(cell);
        }

        public String key() { return key; }
        public Component title() { return title; }
    }

    public record Row<T>(int index, T item, Object key, Rect bounds) { }

    private static final int HEADER_HEIGHT = 19;
    private static final int ROW_HEIGHT = 23;
    private static final int COMPACT_INSET = 4;
    private static final int COMPACT_LABEL_HEIGHT = 10;

    private final List<Column<T>> columns = new ArrayList<>();
    private final List<Row<T>> layoutRows = new ArrayList<>();
    private final Map<Integer, RowView> activeViews = new HashMap<>();
    private final ArrayDeque<RowView> recycledViews = new ArrayDeque<>();
    private Panel rowBackground;
    private List<T> rows = List.of();
    private Function<T, Object> rowKey;
    private Consumer<T> onRowClick = row -> { };
    private Component empty = Component.empty();
    private int headerColor = 0xFFD8D9DC;
    private int[] columnWidths = new int[0];
    private int scroll;
    private int contentHeight;
    private int lastWidth = -1;
    private Font lastFont;
    private boolean compact;
    private boolean dirty = true;
    private boolean resetScroll;
    private Object anchorKey;
    private int anchorOffset;

    public Table<T> column(Column<T> column) {
        Objects.requireNonNull(column);
        if (columns.stream().anyMatch(existing -> existing.key.equals(column.key)))
            throw new IllegalArgumentException("Duplicate table column: " + column.key);
        columns.add(column);
        dirty = true;
        return this;
    }

    public Table<T> rowKey(Function<T, Object> rowKey) {
        this.rowKey = Objects.requireNonNull(rowKey);
        dirty = true;
        return this;
    }

    public Table<T> onRowClick(Consumer<T> onRowClick) {
        this.onRowClick = Objects.requireNonNull(onRowClick);
        return this;
    }

    public Table<T> empty(Component empty) {
        this.empty = Objects.requireNonNull(empty);
        return this;
    }

    public Table<T> headerColor(int color) {
        headerColor = color;
        return this;
    }

    public void rows(List<T> rows) {
        if (!resetScroll) saveAnchor();
        this.rows = List.copyOf(rows);
        dirty = true;
    }

    public void scrollToTop() {
        scroll = 0;
        anchorKey = null;
        resetScroll = true;
    }

    public void scrollBy(int pixels) {
        setScroll(scroll + pixels);
    }

    public void setScroll(int pixels) {
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - bodyHeight()), pixels));
        if (!dirty) updateVisible(null);
    }

    public int scroll() { return scroll; }
    public int contentHeight() { return contentHeight; }
    public int activeViews() { return activeViews.size(); }
    public boolean compact() { return compact; }
    public int headerHeight() { return compact ? 0 : HEADER_HEIGHT; }
    public List<Row<T>> layoutRows() { return List.copyOf(layoutRows); }
    public int[] columnWidths() { return columnWidths.clone(); }

    public List<Row<T>> visibleRows() {
        List<Row<T>> visible = new ArrayList<>();
        int bottom = scroll + bodyHeight();
        for (Row<T> row : layoutRows)
            if (row.bounds().bottom() > scroll && row.bounds().y() < bottom) visible.add(row);
        return List.copyOf(visible);
    }

    private int bodyHeight() { return Math.max(0, bounds().height() - headerHeight()); }

    private void saveAnchor() {
        for (Row<T> row : layoutRows) {
            if (row.bounds().bottom() > scroll) {
                anchorKey = row.key();
                anchorOffset = scroll - row.bounds().y();
                return;
            }
        }
        anchorKey = null;
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        if (rowKey == null) throw new IllegalStateException("A table requires a row key");
        if (lastWidth >= 0 && lastWidth != localBounds.width() && !dirty && !resetScroll) saveAnchor();
        super.layout(localBounds, font);
        lastFont = font;
        if (!dirty && lastWidth == localBounds.width()) {
            setScroll(scroll);
            updateVisible(font);
            return;
        }
        lastWidth = localBounds.width();
        dirty = false;
        int minimum = columns.stream().mapToInt(column -> column.minimumWidth).sum();
        compact = localBounds.width() < minimum;
        columnWidths = compact ? new int[0] : calculateColumnWidths(localBounds.width());
        layoutRows.clear();
        int top = 0;
        for (int index = 0; index < rows.size(); index++) {
            T item = rows.get(index);
            int height = compact ? compactRowHeight(item, font) : regularRowHeight(item, font);
            layoutRows.add(new Row<>(index, item, Objects.requireNonNull(rowKey.apply(item)),
                    new Rect(0, top, localBounds.width(), height)));
            top += height;
        }
        contentHeight = top;
        if (!resetScroll && anchorKey != null) {
            for (Row<T> row : layoutRows) if (anchorKey.equals(row.key())) {
                scroll = row.bounds().y() + anchorOffset;
                break;
            }
        }
        setScroll(scroll);
        recycleAll();
        updateVisible(font);
        anchorKey = null;
        resetScroll = false;
    }

    private void recycleAll() {
        for (RowView view : activeViews.values()) {
            super.removeChild(view);
            recycledViews.add(view);
        }
        activeViews.clear();
    }

    private void updateVisible(Font font) {
        int buffer = bodyHeight();
        int start = scroll - buffer;
        int end = scroll + bodyHeight() + buffer;
        Set<Integer> needed = new HashSet<>();
        for (Row<T> row : layoutRows)
            if (row.bounds().bottom() > start && row.bounds().y() < end)
                needed.add(row.index());
        for (int index : List.copyOf(activeViews.keySet())) if (!needed.contains(index)) {
            RowView view = activeViews.remove(index);
            super.removeChild(view);
            recycledViews.add(view);
        }
        for (Row<T> row : layoutRows) if (needed.contains(row.index())) {
            RowView view = activeViews.get(row.index());
            if (view == null) {
                view = recycledViews.isEmpty() ? new RowView() : recycledViews.removeFirst();
                view.bind(row);
                super.addChild(view);
                activeViews.put(row.index(), view);
            }
            view.layout(new Rect(0, headerHeight() + row.bounds().y() - scroll,
                    row.bounds().width(), row.bounds().height()), font);
        }
    }

    private int[] calculateColumnWidths(int width) {
        int[] result = new int[columns.size()];
        int used = 0;
        int weight = 0;
        for (int index = 0; index < columns.size(); index++) {
            Column<T> column = columns.get(index);
            result[index] = column.preferredWidth;
            used += result[index];
            weight += column.grow;
        }
        if (used > width) {
            int shortage = used - width;
            for (int index = 0; index < columns.size() && shortage > 0; index++) {
                int reduction = Math.min(shortage, result[index] - columns.get(index).minimumWidth);
                result[index] -= reduction;
                shortage -= reduction;
            }
        } else if (weight > 0) {
            int remainder = width - used;
            for (int index = 0; index < columns.size(); index++) {
                int grow = columns.get(index).grow;
                if (grow == 0) continue;
                int addition = remainder * grow / weight;
                result[index] += addition;
                remainder -= addition;
                weight -= grow;
            }
        }
        return result;
    }

    private int regularRowHeight(T item, Font font) {
        int height = ROW_HEIGHT;
        for (int index = 0; index < columns.size(); index++)
            height = Math.max(height, columns.get(index).cell.measureHeight(font, item,
                    columnWidths[index]));
        return height;
    }

    private int compactCellHeight(Column<T> column, T item, Font font, int width) {
        return Math.max(1, Math.max(column.compactHeight.applyAsInt(width),
                column.cell.measureHeight(font, item, width)));
    }

    private int compactRowHeight(T item, Font font) {
        int contentWidth = Math.max(0, bounds().width() - 2 * COMPACT_INSET);
        int height = 2 * COMPACT_INSET;
        for (Column<T> column : columns)
            height += COMPACT_LABEL_HEIGHT + compactCellHeight(column, item, font, contentWidth);
        return height;
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (bounds().width() <= 0 || bounds().height() <= 0) return;
        if (rowBackground == null) rowBackground = new Panel(OreTheme.BAR);
        updateVisible(context.getFont());
        Rect area = screenBounds(screenX, screenY);
        if (!compact) drawHeader(context, area);
        Rect body = new Rect(area.x(), area.y() + headerHeight(), area.width(), bodyHeight());
        if (body.height() <= 0) return;
        GuiGraphics graphics = context.getGraphics();
        graphics.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        if (rows.isEmpty()) {
            Label.draw(context, empty, new Rect(body.x() + 2, body.y() + 8,
                            Math.max(0, body.width() - 4), 12), headerColor,
                    Label.Overflow.CLIP, Label.Horizontal.LEFT, Label.Vertical.CENTER);
        } else {
            for (Row<T> row : visibleRows()) {
                RowView view = activeViews.get(row.index());
                if (view != null) view.draw(context, screenX, screenY);
            }
        }
        graphics.disableScissor();
    }

    private void drawHeader(ModularGuiContext context, Rect area) {
        int x = area.x();
        for (int index = 0; index < columns.size(); index++) {
            Column<T> column = columns.get(index);
            Label.draw(context, column.title, new Rect(x + 4, area.y(),
                            Math.max(0, columnWidths[index] - 8), HEADER_HEIGHT), headerColor,
                    Label.Overflow.CLIP, Label.Horizontal.LEFT, Label.Vertical.CENTER);
            x += columnWidths[index];
        }
    }

    private void drawRegularRow(ModularGuiContext context, T item, Rect row) {
        int x = row.x();
        for (int index = 0; index < columns.size(); index++) {
            int width = columnWidths[index];
            drawCell(context, columns.get(index), item, new Rect(x, row.y(), width, row.height()));
            x += width;
        }
    }

    private void drawCell(ModularGuiContext context, Column<T> column, T item, Rect bounds) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        GuiGraphics graphics = context.getGraphics();
        graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        try {
            column.cell.draw(context, item, bounds);
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawCompactRow(ModularGuiContext context, T item, Rect row) {
        int x = row.x() + COMPACT_INSET;
        int width = Math.max(0, row.width() - 2 * COMPACT_INSET);
        int y = row.y() + COMPACT_INSET;
        for (Column<T> column : columns) {
            Label.draw(context, column.title, new Rect(x, y, width, COMPACT_LABEL_HEIGHT),
                    headerColor, Label.Overflow.CLIP, Label.Horizontal.LEFT, Label.Vertical.CENTER);
            y += COMPACT_LABEL_HEIGHT;
            int height = compactCellHeight(column, item, context.getFont(), width);
            drawCell(context, column, item, new Rect(x, y, width, height));
            y += height;
        }
    }

    public Row<T> hitRow(int rootX, int rootY) {
        Rect area = absoluteBounds();
        int bodyY = area.y() + headerHeight();
        if (rootX < area.x() || rootX >= area.right() || rootY < bodyY || rootY >= area.bottom())
            return null;
        int contentY = rootY - bodyY + scroll;
        for (Row<T> row : visibleRows())
            if (row.bounds().y() <= contentY && contentY < row.bounds().bottom()) return row;
        return null;
    }

    public boolean click(int rootX, int rootY) {
        Row<T> row = hitRow(rootX, rootY);
        if (row == null) return false;
        Rect bounds = row.bounds().translated(absoluteBounds().x(),
                absoluteBounds().y() + headerHeight() - scroll);
        if (compact) {
            int x = bounds.x() + COMPACT_INSET;
            int width = Math.max(0, bounds.width() - 2 * COMPACT_INSET);
            int y = bounds.y() + COMPACT_INSET;
            for (Column<T> column : columns) {
                y += COMPACT_LABEL_HEIGHT;
                int height = compactCellHeight(column, row.item(), lastFont, width);
                Rect cell = new Rect(x, y, width, height);
                if (cell.contains(rootX, rootY)
                        && column.cell.click(row.item(), cell, rootX, rootY)) return true;
                y += height;
            }
        } else {
            int x = bounds.x();
            for (int index = 0; index < columns.size(); index++) {
                int width = columnWidths[index];
                Rect cell = new Rect(x, bounds.y(), width, bounds.height());
                if (cell.contains(rootX, rootY)
                        && columns.get(index).cell.click(row.item(), cell, rootX, rootY)) return true;
                x += width;
            }
        }
        onRowClick.accept(row.item());
        return true;
    }

    @Override protected boolean onPointerPressed(int rootX, int rootY) {
        return click(rootX, rootY);
    }

    @Override public Control hitTop(int rootX, int rootY) {
        return hitRow(rootX, rootY) == null ? null : super.hitTop(rootX, rootY);
    }

    private final class RowView extends Control {
        private Row<T> row;

        private void bind(Row<T> row) { this.row = row; }

        @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
            Rect area = screenBounds(screenX, screenY);
            rowBackground.drawAt(context, area);
            if (compact) drawCompactRow(context, row.item(), area);
            else drawRegularRow(context, row.item(), area);
        }
    }
}
