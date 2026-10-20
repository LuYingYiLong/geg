package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.*;

import io.github.luyingyilong.ore.debug.DebugBridge;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.FocusManager;
import io.github.luyingyilong.ore.layout.Panel;
import io.github.luyingyilong.ore.layout.Padding;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.util.ReleaseGesture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/** Fixed-header table whose rows use the Ore bar theme and shared draw/hit geometry. */
public final class Table<T> extends Container {
    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) { io.github.luyingyilong.ore.theme.OreTheme.registerType(db,"Table"); }
    @Override public String getThemeState() { return "layout="+(compact?"compact":"regular")+", scroll="+scroll; }
    public interface Cell<T> {
        void draw(DrawContext context, T row, Rect bounds);

        default void draw(DrawContext context, T row, Rect bounds, CellState state) {
            draw(context, row, bounds);
        }

        default int measureHeight(Control owner, Font font, T row, int width) {
            return 0;
        }

        /** Completed activation, called after a valid release or explicit programmatic click. */
        default boolean click(T row, Rect bounds, int mouseX, int mouseY) {
            return false;
        }

        /** Separate clickable/focusable action rectangles, in left-to-right order. */
        default List<Rect> focusTargets(T row, Rect bounds) { return List.of(); }

        /** One control per buffered row; instances survive recycling and are rebound to the new row. */
        default Control createControl() { return null; }
        default void bindControl(Control control, T row) { }
    }

    /** A retained cell: its child controls own drawing, focus, sounds and input handling. */
    public static final class ControlCell<T, C extends Control> implements Cell<T> {
        private final Supplier<C> factory;
        private final BiConsumer<C, T> binder;

        public ControlCell(Supplier<C> factory, BiConsumer<C, T> binder) {
            this.factory = Objects.requireNonNull(factory);
            this.binder = Objects.requireNonNull(binder);
        }

        @Override public Control createControl() { return Objects.requireNonNull(factory.get()); }
        @Override @SuppressWarnings("unchecked") public void bindControl(Control control, T row) {
            binder.accept((C) control, row);
        }
        @Override public void draw(DrawContext context, T row, Rect bounds) { }
    }

    /** Indices use the same order as Cell.focusTargets, so painting and activation agree. */
    public record CellState(int pointerIndex, int keyIndex) {
        public static final CellState IDLE = new CellState(-1, -1);
        public boolean pressed(int index) { return index >= 0 && (pointerIndex == index || keyIndex == index); }
    }

    /** Text cell with configurable clipping and wrapping; its measured height feeds row layout. */
    public static final class TextCell<T> implements Cell<T> {
        private final Function<T, Component> text;
        private final Integer color;
        private Label.Overflow overflow = Label.Overflow.ELLIPSIS;
        private Label.Wrap wrap = Label.Wrap.NONE;
        private Label.Horizontal horizontal = Label.Horizontal.LEFT;
        private Label.Vertical vertical = Label.Vertical.CENTER;
        private int maxLines = Integer.MAX_VALUE;
        private int horizontalInset = 4;
        private int verticalInset = 2;

        public TextCell(Function<T, Component> text) { this.text=Objects.requireNonNull(text); this.color=null; }
        public TextCell(Function<T, Component> text, int color) {
            this.text = Objects.requireNonNull(text);
            this.color = color;
        }

        public TextCell<T> setOverflow(Label.Overflow overflow) {
            this.overflow = Objects.requireNonNull(overflow);
            return this;
        }

        public TextCell<T> setWrap(Label.Wrap wrap) {
            this.wrap = Objects.requireNonNull(wrap);
            return this;
        }

        public TextCell<T> setMaxLines(int maxLines) {
            if (maxLines < 1) throw new IllegalArgumentException("At least one line is required");
            this.maxLines = maxLines;
            return this;
        }

        public TextCell<T> setAlignment(Label.Horizontal horizontal, Label.Vertical vertical) {
            this.horizontal = Objects.requireNonNull(horizontal);
            this.vertical = Objects.requireNonNull(vertical);
            return this;
        }

        public TextCell<T> setInset(int horizontal, int vertical) {
            if (horizontal < 0 || vertical < 0) throw new IllegalArgumentException("Negative cell inset");
            horizontalInset = horizontal;
            verticalInset = vertical;
            return this;
        }

        @Override public int measureHeight(Control owner, Font font, T row, int width) {
            if (font == null) return 0;
            return Label.measureTextHeight(owner, font, text.apply(row), Math.max(1, width - 2 * horizontalInset), wrap, overflow, maxLines)
                    + 2 * verticalInset;
        }

        @Override public void draw(DrawContext context, T row, Rect bounds) {
            // RowView owns the retained Padding/Label subtree, including clipping and disposal.
        }

        @Override public Control createControl() {
            Label label=new Label().setOverflow(overflow).setWrap(wrap).setMaxLines(maxLines).setAlignment(horizontal,vertical);
            if(color!=null) label.setColor(color);
            return new Padding(insets()).setChild(label);
        }

        @Override public void bindControl(Control control, T row) {
            Padding padding = (Padding) control;
            padding.setInsets(insets());
            Label label=(Label)padding.getChild(0);
            label.setText(Objects.requireNonNull(text.apply(row))).setOverflow(overflow).setWrap(wrap).setMaxLines(maxLines).setAlignment(horizontal,vertical);
            if(color!=null) label.setColor(color);
        }

        private Padding.Insets insets() {
            return new Padding.Insets(verticalInset, horizontalInset, verticalInset, horizontalInset);
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

        public String getKey() { return key; }
        public Component getTitle() { return title; }
    }

    public record Row<T>(int index, T item, Object key, Rect bounds) { }

    private static final int HEADER_HEIGHT = 19;
    private static final int ROW_HEIGHT = 23;
    private static final int COMPACT_INSET = 4;
    private static final int COMPACT_LABEL_HEIGHT = 10;

    private final List<Column<T>> columns = new ArrayList<>();
    private final Map<String, Label> headerLabels = new HashMap<>();
    private final Label emptyLabel = ownPart("emptyLabel", new Label());
    private final List<Row<T>> layoutRows = new ArrayList<>();
    private final Map<Integer, RowView> activeViews = new HashMap<>();
    private final ArrayDeque<RowView> recycledViews = new ArrayDeque<>();
    private Panel rowBackground;
    private List<T> rows = List.of();
    private Function<T, Object> rowKey;
    private final SignalSource<T> rowActivated = new SignalSource<>(this, "rowActivated", "T");
    private Component empty = Component.empty();
    private Integer headerColor;
    private int[] columnWidths = new int[0];
    private int scroll;
    private int contentHeight;
    private int lastWidth = -1;
    private Font lastFont;
    private boolean compact;
    private boolean headerVisible = true;
    private boolean dirty = true;
    private boolean resetScroll;
    private Object anchorKey;
    private int anchorOffset;
    private Object focusedKey;
    private int focusedAction = -1;
    private final ReleaseGesture<PressTarget> gesture = new ReleaseGesture<>();
    private Rect drawingBody;

    public Table() {
        getSignalScope().own(() -> { activeViews.clear(); recycledViews.clear(); headerLabels.clear(); });
        setFocusable(true);
        setFocusExpansion(1);
    }

    @Override public boolean acceptsFocus() {
        return super.acceptsFocus() && !layoutRows.isEmpty()
                && columns.stream().noneMatch(column -> column.cell instanceof ControlCell<?, ?>);
    }

    private Row<T> focusedRow() {
        for (Row<T> row : layoutRows)
            if (Objects.equals(row.key(), focusedKey)) return row;
        if (layoutRows.isEmpty()) return null;
        for (Row<T> row : layoutRows)
            if (row.bounds().bottom() > scroll) return row;
        return layoutRows.get(0);
    }

    @Override public Rect getFocusBounds() {
        Row<T> row = focusedRow();
        if (row == null) return getAbsoluteBounds();
        Rect origin = getAbsoluteBounds();
        List<ActionTarget<T>> targets = focusTargets(row);
        Rect area = focusedAction >= 0 && focusedAction < targets.size()
                ? targets.get(focusedAction).bounds()
                : row.bounds().translated(origin.x(), origin.y() + getHeaderHeight() - scroll);
        int top = Math.max(area.y(), origin.y() + getHeaderHeight());
        int bottom = Math.min(area.bottom(), origin.bottom());
        return new Rect(area.x(), top, area.width(), Math.max(0, bottom - top));
    }

    @Override public boolean onFocusMove(FocusManager.Direction direction) {
        cancelInteraction();
        Row<T> current = focusedRow();
        if (current == null) return false;
        if (direction == FocusManager.Direction.RIGHT) {
            if (focusedAction + 1 >= focusTargets(current).size()) return false;
            focusedAction++;
            return true;
        }
        if (direction == FocusManager.Direction.LEFT) {
            if (focusedAction < 0) return false;
            focusedAction--;
            return true;
        }
        int next = current.index() + (direction == FocusManager.Direction.UP ? -1 : 1);
        if (next < 0 || next >= layoutRows.size()) return false;
        Row<T> row = layoutRows.get(next);
        focusedKey = row.key();
        if (focusedAction >= focusTargets(row).size()) focusedAction = -1;
        int top = row.bounds().y();
        int bottom = row.bounds().bottom();
        if (top < scroll) setScroll(top);
        else if (bottom > scroll + bodyHeight()) setScroll(bottom - bodyHeight());
        return true;
    }

    @Override public boolean activate() {
        Row<T> row = focusedRow();
        if (row == null) return false;
        List<ActionTarget<T>> targets = focusTargets(row);
        if (focusedAction >= 0 && focusedAction < targets.size()) {
            ActionTarget<T> target = targets.get(focusedAction);
            Rect bounds = target.bounds();
            return target.cell().click(row.item(), target.cellBounds(),
                    bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2);
        }
        rowActivated.emit(row.item());
        return true;
    }

    @Override public boolean onActivationPressed(int keyCode) {
        PressTarget target = keyTarget();
        if (target == null || !getVisible()) return false;
        gesture.keyDown(keyCode, target);
        return true;
    }

    @Override public boolean onActivationReleased(int keyCode) {
        var release = gesture.keyUp(keyCode, getVisible() ? keyTarget() : null);
        if (release.target() != null) activate();
        return release.handled();
    }

    private PressTarget keyTarget() {
        Row<T> row = focusedRow();
        if (row == null) return null;
        List<ActionTarget<T>> targets = focusTargets(row);
        if (focusedAction >= 0 && focusedAction < targets.size()) {
            ActionTarget<T> target = targets.get(focusedAction);
            return new PressTarget(row.key(), target.columnKey(), target.index(), row.item());
        }
        return new PressTarget(row.key(), null, -1, row.item());
    }

    @Override public void cancelInteraction() {
        gesture.cancel();
        for (Control child : getChildren()) child.cancelInteractions();
    }
    @Override public void onFocusChanged(boolean focused) { if (!focused) cancelInteraction(); }

    private record PressTarget(Object rowKey, String columnKey, int index, Object snapshot) { }

    private List<ActionTarget<T>> focusTargets(Row<T> row) {
        List<ActionTarget<T>> targets = new ArrayList<>();
        Rect bounds = row.bounds().translated(getAbsoluteBounds().x(),
                getAbsoluteBounds().y() + getHeaderHeight() - scroll);
        bounds=rowContent(bounds);
        if (compact) {
            int x = bounds.x() + COMPACT_INSET;
            int width = Math.max(0, bounds.width() - 2 * COMPACT_INSET);
            int y = bounds.y() + COMPACT_INSET;
            for (Column<T> column : columns) {
                y += COMPACT_LABEL_HEIGHT;
                int height = compactCellHeight(column, row.item(), lastFont, width);
                Rect cell = new Rect(x, y, width, height);
                addTargets(targets, column, row.item(), cell);
                y += height;
            }
        } else {
            int x = bounds.x();
            for (int index = 0; index < columns.size(); index++) {
                Column<T> column = columns.get(index);
                Rect cell = new Rect(x, bounds.y(), columnWidths[index], bounds.height());
                addTargets(targets, column, row.item(), cell);
                x += columnWidths[index];
            }
        }
        return targets;
    }

    private void addTargets(List<ActionTarget<T>> targets, Column<T> column, T item, Rect cell) {
        List<Rect> areas = column.cell.focusTargets(item, cell);
        for (int index = 0; index < areas.size(); index++)
            targets.add(new ActionTarget<>(column.key, index, column.cell, cell, areas.get(index)));
    }

    private record ActionTarget<T>(String columnKey, int index, Cell<T> cell, Rect cellBounds, Rect bounds) { }

    public Table<T> addColumn(Column<T> column) {
        cancelInteraction();
        Objects.requireNonNull(column);
        if (columns.stream().anyMatch(existing -> existing.key.equals(column.key)))
            throw new IllegalArgumentException("Duplicate table column: " + column.key);
        columns.add(column);
        headerLabels.put(column.key, ownPart("header_" + column.key, new Label(column.title)));
        dirty = true;
        return this;
    }

    public Table<T> setRowKey(Function<T, Object> rowKey) {
        cancelInteraction();
        this.rowKey = Objects.requireNonNull(rowKey);
        dirty = true;
        return this;
    }

    public Signal<T> rowActivated() { return rowActivated.signal(); }

    public Table<T> setEmpty(Component empty) {
        this.empty = Objects.requireNonNull(empty);
        return this;
    }

    public Table<T> setHeaderColor(int color) {
        headerColor = color;
        return this;
    }

    public Table<T> setHeaderVisible(boolean visible) {
        if (headerVisible != visible) {
            cancelInteraction();
            headerVisible = visible;
            requestLayout();
        }
        return this;
    }

    public void setRows(List<T> rows) {
        List<T> next = List.copyOf(rows);
        if (this.rows.equals(next)) return;
        cancelInteraction();
        if (!resetScroll) saveAnchor();
        this.rows = next;
        dirty = true;
    }

    @Override public void invalidateTextLayout() {
        cancelInteraction();
        if (!resetScroll) saveAnchor();
        dirty = true;
        super.invalidateTextLayout();
    }

    public void scrollToTop() {
        cancelInteraction();
        scroll = 0;
        anchorKey = null;
        resetScroll = true;
    }

    public void scrollBy(int pixels) {
        setScroll(scroll + pixels);
    }

    public void setScroll(int pixels) {
        int next = Math.max(0, Math.min(Math.max(0, contentHeight - bodyHeight()), pixels));
        if (scroll != next) cancelInteraction();
        scroll = next;
        if (!dirty) updateVisible(null);
    }

    public int getScroll() { return scroll; }
    public int getContentHeight() { return contentHeight; }
    public int getActiveViews() { return activeViews.size(); }
    public boolean getCompact() { return compact; }
    public int getHeaderHeight() { return !headerVisible || compact ? 0 : HEADER_HEIGHT; }
    public List<Row<T>> getLayoutRows() { return List.copyOf(layoutRows); }
    public int[] getColumnWidths() { return columnWidths.clone(); }

    @Override protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren() && proposed.stream().anyMatch(child -> child.getParent() != this))
            throw new IllegalStateException("Table owns virtual rows; use setRows/addColumn");
    }

    @Override protected void childrenChanged() {
        activeViews.values().removeIf(view -> view.getParent() != this);
    }

    public List<Row<T>> getVisibleRows() {
        List<Row<T>> visible = new ArrayList<>();
        int bottom = scroll + bodyHeight();
        for (Row<T> row : layoutRows)
            if (row.bounds().bottom() > scroll && row.bounds().y() < bottom) visible.add(row);
        return List.copyOf(visible);
    }

    private int bodyHeight() { return Math.max(0, getBounds().height() - getHeaderHeight()); }

    /** The same viewport clips painting, pointer input and keyboard focus outlines. */
    public Rect getBodyBounds() {
        Rect area = getAbsoluteBounds();
        return new Rect(area.x(), area.y() + getHeaderHeight(), area.width(), bodyHeight());
    }

    public void revealControl(Control control) {
        Rect area = control.getAbsoluteBounds();
        Rect body = getBodyBounds();
        if (area.y() < body.y()) setScroll(scroll + area.y() - body.y());
        else if (area.bottom() > body.bottom()) setScroll(scroll + area.bottom() - body.bottom());
    }

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
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            // Scroll may lay out this table several times while resolving scrollbar space.
            // Resizing does not invalidate a retained cell's press/release gesture.
            if (!getBounds().equals(localBounds)) gesture.cancel();
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
            compact = localBounds.width()-getThemeItem(OreTheme.BAR).getContentMargin().getHorizontal() < minimum;
            columnWidths = compact ? new int[0] : calculateColumnWidths(Math.max(0,localBounds.width()-getThemeItem(OreTheme.BAR).getContentMargin().getHorizontal()));
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
            rebindViews();
            setScroll(scroll);
            updateVisible(font);
            anchorKey = null;
            resetScroll = false;
        }
    }

    private void rebindViews() {
        Map<Object, Row<T>> next = new HashMap<>();
        for (Row<T> row : layoutRows) next.put(row.key(), row);
        List<RowView> previous = List.copyOf(activeViews.values());
        activeViews.clear();
        for (RowView view : previous) {
            if (!view.isAlive()) { removeChild(view); continue; }
            Row<T> row = next.get(view.row.key());
            if (row == null) recycle(view);
            else {
                view.bind(row);
                activeViews.put(row.index(), view);
            }
        }
    }

    private void recycle(RowView view) {
        // A pooled Button must not silently inherit focus when it is rebound to another record.
        for (Control node = getParent(); node != null; node = node.getParent()) {
            if (node instanceof Window window) {
                window.getFocusManager().forgetSubtree(view);
                break;
            }
        }
        view.resetBindingScope();
        removeChild(view);
        recycledViews.add(view);
    }

    private void updateVisible(Font font) {
        recycledViews.removeIf(view -> !view.isAlive());
        for (int key : List.copyOf(activeViews.keySet())) { RowView view = activeViews.get(key); if (view != null && !view.isAlive()) { activeViews.remove(key); removeChild(view); } }
        int buffer = bodyHeight();
        int start = scroll - buffer;
        int end = scroll + bodyHeight() + buffer;
        Set<Integer> needed = new HashSet<>();
        for (Row<T> row : layoutRows)
            if (row.bounds().bottom() > start && row.bounds().y() < end)
                needed.add(row.index());
        for (int index : List.copyOf(activeViews.keySet())) if (!needed.contains(index)) {
            RowView view = activeViews.remove(index);
            recycle(view);
        }
        int childIndex = 0;
        for (Row<T> row : layoutRows) if (needed.contains(row.index())) {
            RowView view = activeViews.get(row.index());
            if (view == null) {
                view = recycledViews.isEmpty() ? ownPooled(new RowView()) : recycledViews.removeFirst();
                view.bind(row);
                List<Control> next = new ArrayList<>(getChildren());
                next.add(view);
                updateChildren(next);
                activeViews.put(row.index(), view);
            }
            moveChild(view, childIndex++);
            view.layout(new Rect(0, getHeaderHeight() + row.bounds().y() - scroll,
                    row.bounds().width(), row.bounds().height()), font == null ? lastFont : font);
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

    private Rect rowContent(Rect bounds) { return getThemeItem(OreTheme.BAR).getContentRect(bounds); }

    private int regularRowHeight(T item, Font font) {
        int height = ROW_HEIGHT;
        for (int index = 0; index < columns.size(); index++)
            height = Math.max(height, columns.get(index).cell.measureHeight(this, font, item,
                    columnWidths[index]));
        return height+getThemeItem(OreTheme.BAR).getContentMargin().getVertical();
    }

    private int compactCellHeight(Column<T> column, T item, Font font, int width) {
        return Math.max(1, Math.max(column.compactHeight.applyAsInt(width),
                column.cell.measureHeight(this, font, item, width)));
    }

    private int compactRowHeight(T item, Font font) {
        int contentWidth = Math.max(0, getBounds().width() - 2 * COMPACT_INSET-getThemeItem(OreTheme.BAR).getContentMargin().getHorizontal());
        int height = 2 * COMPACT_INSET+getThemeItem(OreTheme.BAR).getContentMargin().getVertical();
        for (Column<T> column : columns)
            height += COMPACT_LABEL_HEIGHT + compactCellHeight(column, item, font, contentWidth);
        return height;
    }

    @Override
    public void draw(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            if (getBounds().width() <= 0 || getBounds().height() <= 0) return;
            if (rowBackground == null) rowBackground = ownPart("rowBackground", new Panel(OreTheme.BAR));
            updateVisible(context.getFont());
            Rect area = getScreenBounds(screenX, screenY);
            if (headerVisible && !compact) drawHeader(context, area);
            Rect body = new Rect(area.x(), area.y() + getHeaderHeight(), area.width(), bodyHeight());
            drawingBody = body;
            if (body.height() <= 0) return;
            GuiGraphics graphics = context.getGraphics();
            context.enableScissor(body.x(), body.y(), body.right(), body.bottom());
            DebugBridge.backend().clip(context.toScreenRect(body));
            if (rows.isEmpty()) {
                drawText(this, "empty", emptyLabel.setText(empty), context, new Rect(body.x() + 2, body.y() + 8,
                                Math.max(0, body.width() - 4), 12));
            } else {
                for (Row<T> row : getVisibleRows()) {
                    RowView view = activeViews.get(row.index());
                    if (view != null) view.draw(context, screenX, screenY);
                }
            }
            context.disableScissor(); DebugBridge.backend().unclip();
        }
    }

    private void drawHeader(DrawContext context, Rect area) {
        int x = area.x()+getThemeItem(OreTheme.BAR).getContentMargin().left();
        for (int index = 0; index < columns.size(); index++) {
            Column<T> column = columns.get(index);
            drawText(this, "header_" + column.key, headerLabels.get(column.key), context,
                    new Rect(x + 4, area.y(), Math.max(0, columnWidths[index] - 8), HEADER_HEIGHT));
            x += columnWidths[index];
        }
    }

    private void drawText(Control owner, String name, Label label, DrawContext context, Rect area) {
        if(headerColor!=null) label.setColor(headerColor); else label.setThemeTypeVariation("LabelSecondary");
        label.setOverflow(Label.Overflow.CLIP).setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        label.layout(area, context.getFont());
        DebugBridge.backend().internal(owner, name, label);
        label.draw(context, 0, 0);
    }

    private void drawRegularRow(DrawContext context, T item, Rect row) {
        row=rowContent(row);
        int x = row.x();
        for (int index = 0; index < columns.size(); index++) {
            int width = columnWidths[index];
            drawCell(context, columns.get(index), item, new Rect(x, row.y(), width, row.height()));
            x += width;
        }
    }

    private void drawCell(DrawContext context, Column<T> column, T item, Rect bounds) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        GuiGraphics graphics = context.getGraphics();
        context.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        DebugBridge.backend().clip(context.toScreenRect(bounds));
        try {
            List<Rect> targets = column.cell.focusTargets(item, bounds);
            int pointerIndex = -1;
            int keyIndex = -1;
            Object key = rowKey.apply(item);
            for (int index = 0; index < targets.size(); index++) {
                PressTarget target = new PressTarget(key, column.key, index, item);
                if (gesture.pointerPressed(target) && targets.get(index).contains(context.getAbsMouseX(), context.getAbsMouseY())
                        && drawingBody != null && drawingBody.contains(context.getAbsMouseX(), context.getAbsMouseY())) pointerIndex = index;
                if (gesture.keyPressed(target)) keyIndex = index;
            }
            column.cell.draw(context, item, bounds, new CellState(pointerIndex, keyIndex));
        } finally {
            context.disableScissor(); DebugBridge.backend().unclip();
        }
    }

    private void drawCompactRow(DrawContext context, T item, Rect row, RowView view) {
        row=rowContent(row);
        int x = row.x() + COMPACT_INSET;
        int width = Math.max(0, row.width() - 2 * COMPACT_INSET);
        int y = row.y() + COMPACT_INSET;
        for (Column<T> column : columns) {
            drawText(view, "caption_" + column.key, view.captions.get(column.key), context,
                    new Rect(x, y, width, COMPACT_LABEL_HEIGHT));
            y += COMPACT_LABEL_HEIGHT;
            int height = compactCellHeight(column, item, context.getFont(), width);
            drawCell(context, column, item, new Rect(x, y, width, height));
            y += height;
        }
    }

    public Row<T> hitRow(int rootX, int rootY) {
        Rect area = getAbsoluteBounds();
        int bodyY = area.y() + getHeaderHeight();
        if (rootX < area.x() || rootX >= area.right() || rootY < bodyY || rootY >= area.bottom())
            return null;
        int contentY = rootY - bodyY + scroll;
        for (Row<T> row : getVisibleRows())
            if (row.bounds().y() <= contentY && contentY < row.bounds().bottom()) return row;
        return null;
    }

    public boolean click(int rootX, int rootY) {
        Row<T> row = hitRow(rootX, rootY);
        if (row == null) return false;
        focusedKey = row.key();
        focusedAction = -1;
        List<ActionTarget<T>> targets = focusTargets(row);
        for (int index = 0; index < targets.size(); index++)
            if (targets.get(index).bounds().contains(rootX, rootY)) {
                focusedAction = index;
                break;
            }
        Rect bounds = row.bounds().translated(getAbsoluteBounds().x(),
                getAbsoluteBounds().y() + getHeaderHeight() - scroll);
        bounds=rowContent(bounds);
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
        rowActivated.emit(row.item());
        return true;
    }

    @Override protected boolean wheel(int x,int y,double delta,boolean horizontal) {
        if(horizontal || !getBodyBounds().contains(x,y))return false;
        int before=scroll;scrollBy(-(int)Math.signum(delta)*(compact?36:23));return before!=scroll;
    }

    @Override protected boolean pointerDown(int rootX, int rootY) {
        PressTarget target = pointerTarget(rootX, rootY);
        if (target == null) return false;
        Row<T> row = hitRow(rootX, rootY);
        focusedKey = row.key();
        focusedAction = -1;
        List<ActionTarget<T>> targets = focusTargets(row);
        for (int index = 0; index < targets.size(); index++)
            if (targets.get(index).columnKey().equals(target.columnKey())
                    && targets.get(index).index() == target.index()) { focusedAction = index; break; }

        gesture.pointerDown(target);
        return true;
    }

    @Override protected boolean pointerUp(int rootX, int rootY, boolean over) {
        var release = gesture.pointerUp(pointerTarget(rootX, rootY), over && getVisible());
        if (release.target() != null) click(rootX, rootY);
        return release.handled();
    }

    private PressTarget pointerTarget(int rootX, int rootY) {
        Row<T> row = hitRow(rootX, rootY);
        if (row == null) return null;
        List<ActionTarget<T>> actions = focusTargets(row);
        for (ActionTarget<T> action : actions) if (action.bounds().contains(rootX, rootY))
            return new PressTarget(row.key(), action.columnKey(), action.index(), row.item());
        Rect bounds = row.bounds().translated(getAbsoluteBounds().x(), getAbsoluteBounds().y() + getHeaderHeight() - scroll);
        bounds=rowContent(bounds);
        if (compact) {
            int y = bounds.y() + COMPACT_INSET;
            for (Column<T> column : columns) {
                y += COMPACT_LABEL_HEIGHT;
                int height = compactCellHeight(column, row.item(), lastFont, Math.max(0, bounds.width() - 2 * COMPACT_INSET));
                if (new Rect(bounds.x() + COMPACT_INSET, y, Math.max(0, bounds.width() - 2 * COMPACT_INSET), height)
                        .contains(rootX, rootY)) return new PressTarget(row.key(), column.key, -1, row.item());
                y += height;
            }
        } else {
            int x = bounds.x();
            for (int index = 0; index < columns.size(); index++) {
                if (new Rect(x, bounds.y(), columnWidths[index], bounds.height()).contains(rootX, rootY))
                    return new PressTarget(row.key(), columns.get(index).key, -1, row.item());
                x += columnWidths[index];
            }
        }
        return new PressTarget(row.key(), null, -1, row.item());
    }

    @Override public Control hitTop(int rootX, int rootY) {
        return hitRow(rootX, rootY) == null ? null : super.hitTop(rootX, rootY);
    }





    private final class RowView extends Container {
        private Row<T> row;
        private final Map<String, Control> controls = new HashMap<>();
        private final Map<String, Label> captions = new HashMap<>();

        @Override protected void childrenChanged() {
            int previousSize = controls.size();
            controls.values().removeIf(control -> control.getParent() != this || !control.isAlive());
            if (controls.size() != previousSize && Table.this.isAlive()) {
                if (!dirty) saveAnchor();
                dirty = true;
                requestLayout();
            }
        }

        private void bind(Row<T> row) {
            // Preserve gestures and open menus when only geometry or an equal snapshot changes.
            // A different record or changed data must never inherit a pending activation.
            if (this.row == null || !Objects.equals(this.row.key(), row.key())
                    || !Objects.equals(this.row.item(), row.item())) { cancelInteractions(); resetBindingScope(); }
            DebugBridge.backend().binding(this, row.key());
            resetBindingScope();
            this.row = row;
            for (Column<T> column : columns) {
                captions.computeIfAbsent(column.key, key -> ownPart("caption_" + key, new Label(column.title)));
                Control control = controls.get(column.key);
                if (control != null && !control.isAlive()) { controls.remove(column.key); removeChild(control); control = null; }
                if (control == null) {
                    control = column.cell.createControl();
                    if (control != null) { controls.put(column.key, control); this.addChild(control); }
                }
                if (control != null) column.cell.bindControl(control, row.item());
            }
            int childIndex = 0;
            for (Column<T> column : columns) {
                Control control = controls.get(column.key);
                if (control != null && control.getParent() == this) moveChild(control, childIndex++);
            }
        }

        @Override public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            Rect inner=rowContent(new Rect(0,0,bounds.width(),bounds.height()));
            int x = inner.x()+(compact ? COMPACT_INSET : 0);
            int y = inner.y()+(compact ? COMPACT_INSET : 0);
            for (int index = 0; index < columns.size(); index++) {
                Column<T> column = columns.get(index);
                int width = compact ? Math.max(0, inner.width() - 2 * COMPACT_INSET) : columnWidths[index];
                if (compact) y += COMPACT_LABEL_HEIGHT;
                int height = compact ? compactCellHeight(column, row.item(), font, width) : inner.height();
                Control control = controls.get(column.key);
                if (control != null) fitChild(control, new Rect(x, y, width, height), font);
                if (compact) y += height;
                else x += width;
            }

        }
    }

        @Override public void draw(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
                render(context, screenX, screenY);
                for (Column<T> column : columns) {
                    Control control = controls.get(column.key);
                    if (control == null || !(control.isAlive() && control.getVisible())) continue;
                    Rect area = control.getScreenBounds(screenX, screenY);
                    context.getGraphics().enableScissor(area.x(), area.y(), area.right(), area.bottom());
                    DebugBridge.backend().clip(context.toScreenRect(area));
                    try { control.draw(context, screenX, screenY); }
                    finally { context.getGraphics().disableScissor(); DebugBridge.backend().unclip(); }
                }
        }
    }

        @Override protected void render(DrawContext context, int screenX, int screenY) {
            Rect area = getScreenBounds(screenX, screenY);
            rowBackground.drawAt(context, area);
            if (compact) drawCompactRow(context, row.item(), area, this);
            else drawRegularRow(context, row.item(), area);
        }


    }
}
