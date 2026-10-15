package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;
import java.util.function.Consumer;

/**
 * A regular or masonry grid with optional view recycling
 */
public final class Grid<T> extends Container {
    private final List<Entry<T>> entries = new ArrayList<>();
    private final List<Control> fixedChildren = new ArrayList<>();
    private final Map<Integer, View<T>> active = new HashMap<>();
    private final ArrayDeque<View<T>> recycled = new ArrayDeque<>();
    private final List<Integer> visibleIndices = new ArrayList<>();
    private Adapter<T> adapter;
    private LayoutMode mode = LayoutMode.MASONRY;
    private int minCardWidth = 96;
    private int maxColumns = 4;
    private int gap = 4;
    private int scroll;
    private int horizontalScroll;
    private int contentHeight;
    private int contentWidth;
    private int lastWidth = -1;
    private Object anchorKey;
    private int anchorOffset;
    private boolean resetScrollPending;
    private boolean dirty = true;
    private boolean visibilityDirty = true;
    private Consumer<T> onItemClick = item -> { };
    private int visibleScroll = -1;
    private int visibleHeight = -1;

    private static int shortest(int[] heights) {
        int index = 0;
        for (int candidate = 1; candidate < heights.length; candidate++)
            if (heights[candidate] < heights[index]) index = candidate;
        return index;
    }

    public Grid<T> adapter(Adapter<T> adapter) {
        if (adapter != null && !fixedChildren.isEmpty())
            throw new IllegalStateException("A Grid cannot mix fixed children and an adapter");
        this.adapter = adapter;
        invalidate();
        return this;
    }

    public Grid<T> onItemClick(Consumer<T> onItemClick) {
        this.onItemClick = Objects.requireNonNull(onItemClick);
        return this;
    }

    public Grid<T> add(Control child) {
        addChild(child);
        return this;
    }

    @Override public Grid<T> addChild(Control child) {
        if (adapter != null) throw new IllegalStateException("A Grid cannot mix fixed children and an adapter");
        super.addChild(child);
        fixedChildren.add(child);
        invalidate();
        return this;
    }

    @Override public boolean removeChild(Control child) {
        if (!super.removeChild(child)) return false;
        fixedChildren.remove(child);
        invalidate();
        return true;
    }

    public Grid<T> mode(LayoutMode mode) {
        this.mode = mode;
        invalidate();
        return this;
    }

    public Grid<T> columns(int minCardWidth, int maxColumns, int gap) {
        this.minCardWidth = Math.max(1, minCardWidth);
        this.maxColumns = Math.max(1, maxColumns);
        this.gap = Math.max(0, gap);
        invalidate();
        return this;
    }

    public void invalidate() {
        if (!resetScrollPending) saveAnchor();
        dirty = true;
    }

    public List<Entry<T>> entries() {
        return List.copyOf(entries);
    }

    public int contentHeight() {
        return contentHeight;
    }

    public int contentWidth() { return contentWidth; }
    public int horizontalScroll() { return horizontalScroll; }
    public boolean scrollHorizontalBy(int amount) {
        int maximum = Math.max(0, contentWidth - bounds().width());
        if (maximum == 0) return false;
        horizontalScroll = Math.max(0, Math.min(maximum, horizontalScroll + amount));
        return true;
    }

    public int scroll() {
        return scroll;
    }

    public int activeViews() {
        return active.size();
    }

    public int columns() {
        int width = Math.max(1, bounds().width());
        return Math.max(1, Math.min(maxColumns, (width + gap) / (minCardWidth + gap)));
    }

    public void scrollBy(int amount) {
        setScroll(scroll + amount);
    }

    public void setScroll(int scroll) {
        this.scroll = Math.max(0, Math.min(Math.max(0, contentHeight - bounds().height()), scroll));
        if (!dirty && adapter != null) updateVisible();
    }

    public void scrollToTop() {
        anchorKey = null;
        resetScrollPending = true;
        scroll = 0;
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        if (dirty || lastWidth != localBounds.width()) relayout(font);
        else setScroll(scroll);
        if (adapter != null) updateVisible();
    }

    private void saveAnchor() {
        for (Entry<T> entry : entries) {
            if (entry.bounds().bottom() > scroll && entry.bounds().y() <= scroll) {
                anchorKey = entry.key();
                anchorOffset = scroll - entry.bounds().y();
                return;
            }
        }
        anchorKey = null;
    }

    private void relayout(Font font) {
        if (!resetScrollPending && lastWidth != -1 && lastWidth != bounds().width()) saveAnchor();
        entries.clear();
        lastWidth = bounds().width();
        dirty = false;
        if (adapter == null) {
            relayoutFixed(font);
            resetScrollPending = false;
            return;
        }
        if (adapter.size() == 0) {
            contentHeight = 0;
            contentWidth = 0;
            horizontalScroll = 0;
            setScroll(0);
            recycleAll();
            resetScrollPending = false;
            return;
        }
        int columns = columns();
        int cardWidth = Math.max(minCardWidth, (bounds().width() - (columns - 1) * gap) / columns);
        contentWidth = columns * cardWidth + (columns - 1) * gap;
        horizontalScroll = Math.min(horizontalScroll, Math.max(0, contentWidth - bounds().width()));
        int[] heights = new int[columns];
        Arrays.fill(heights, 0);
        int rowY = 0;
        int rowHeight = 0;
        for (int index = 0; index < adapter.size(); index++) {
            T item = adapter.item(index);
            int column = mode == LayoutMode.ROWS ? index % columns : shortest(heights);
            if (mode == LayoutMode.ROWS && column == 0 && index > 0) {
                rowY += rowHeight + gap;
                rowHeight = 0;
            }
            int cardHeight = Math.max(1, adapter.measureHeight(item, cardWidth, font));
            int cardY = mode == LayoutMode.ROWS ? rowY : heights[column];
            entries.add(new Entry<>(index, item, adapter.key(item),
                    new Rect(column * (cardWidth + gap), cardY, cardWidth, cardHeight)));
            if (mode == LayoutMode.ROWS) rowHeight = Math.max(rowHeight, cardHeight);
            else heights[column] += cardHeight + gap;
        }
        contentHeight = mode == LayoutMode.ROWS ? rowY + rowHeight
                : Math.max(0, Arrays.stream(heights).max().orElse(0) - gap);
        if (anchorKey != null) {
            for (Entry<T> entry : entries)
                if (anchorKey.equals(entry.key())) {
                    scroll = entry.bounds().y() + anchorOffset;
                    break;
                }
        }
        recycleAll();
        setScroll(scroll);
        anchorKey = null;
        resetScrollPending = false;
    }

    private void relayoutFixed(Font font) {
        int columns = columns();
        int cardWidth = Math.max(minCardWidth, (bounds().width() - (columns - 1) * gap) / columns);
        contentWidth = columns * cardWidth + (columns - 1) * gap;
        horizontalScroll = Math.min(horizontalScroll, Math.max(0, contentWidth - bounds().width()));
        int[] heights = new int[columns];
        int rowY = 0;
        int rowHeight = 0;
        for (int index = 0; index < fixedChildren.size(); index++) {
            Control child = fixedChildren.get(index);
            int column = mode == LayoutMode.ROWS ? index % columns : shortest(heights);
            if (mode == LayoutMode.ROWS && column == 0 && index > 0) {
                rowY += rowHeight + gap;
                rowHeight = 0;
            }
            int height = Math.max(1, child.measureSize(font, new Size(cardWidth, cardWidth)).height());
            int top = mode == LayoutMode.ROWS ? rowY : heights[column];
            child.layout(new Rect(column * (cardWidth + gap), top, cardWidth, height), font);
            if (mode == LayoutMode.ROWS) rowHeight = Math.max(rowHeight, height);
            else heights[column] += height + gap;
        }
        contentHeight = mode == LayoutMode.ROWS ? rowY + rowHeight
                : Math.max(0, Arrays.stream(heights).max().orElse(0) - gap);
        setScroll(scroll);
    }

    private void recycleAll() {
        for (View<T> view : active.values())
            if (view instanceof Control control) super.removeChild(control);
        recycled.addAll(active.values());
        active.clear();
        visibleIndices.clear();
        visibilityDirty = true;
    }

    private void updateVisible() {
        if (adapter == null) return;
        if (!visibilityDirty && visibleScroll == scroll && visibleHeight == bounds().height()) return;
        visibleScroll = scroll;
        visibleHeight = bounds().height();
        visibilityDirty = false;
        int buffer = bounds().height();
        int start = scroll - buffer;
        int end = scroll + bounds().height() + buffer;
        Set<Integer> visible = new HashSet<>();
        for (Entry<T> entry : entries)
            if (entry.bounds().bottom() >= start && entry.bounds().y() <= end) {
                visible.add(entry.index());
            }
        visibleIndices.clear();
        visibleIndices.addAll(visible);
        visibleIndices.sort(Integer::compareTo);
        for (int index : List.copyOf(active.keySet()))
            if (!visible.contains(index)) {
                View<T> view = active.remove(index);
                if (view instanceof Control control) super.removeChild(control);
                recycled.add(view);
            }
        for (Entry<T> entry : entries)
            if (visible.contains(entry.index()) && !active.containsKey(entry.index())) {
                View<T> view = recycled.isEmpty() ? adapter.createView() : recycled.removeFirst();
                view.bind(entry.item());
                if (view instanceof Control control) super.addChild(control);
                active.put(entry.index(), view);
            }
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (bounds().width() <= 0 || bounds().height() <= 0) return;
        if (adapter != null) updateVisible();
        Rect viewport = screenBounds(screenX, screenY);
        GuiGraphics graphics = context.getGraphics();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        if (adapter == null) {
            for (Control child : fixedChildren) {
                Rect area = child.absoluteBounds();
                if (area.bottom() >= absoluteBounds().y() + scroll - bounds().height()
                        && area.y() <= absoluteBounds().y() + scroll + 2 * bounds().height()) {
                    child.draw(context, screenX - horizontalScroll, screenY - scroll);
                }
            }
        } else {
            for (int index : visibleIndices) {
                Entry<T> entry = entries.get(index);
                View<T> view = active.get(index);
                if (view == null) continue;
                Rect card = entry.bounds().translated(viewport.x() - horizontalScroll,
                        viewport.y() - scroll);
                boolean hovered = viewport.contains(context.getAbsMouseX(), context.getAbsMouseY())
                        && card.contains(context.getAbsMouseX(), context.getAbsMouseY());
                if (view instanceof Control control) {
                    control.layout(new Rect(entry.bounds().x() - horizontalScroll,
                            entry.bounds().y() - scroll, entry.bounds().width(), entry.bounds().height()),
                            context.getFont());
                    control.draw(context, screenX, screenY);
                } else view.draw(context, card, hovered);
            }
        }
        graphics.disableScissor();
    }

    public Entry<T> hitItem(int rootX, int rootY) {
        Rect viewport = absoluteBounds();
        if (!viewport.contains(rootX, rootY)) return null;
        int localX = rootX - viewport.x() + horizontalScroll;
        int localY = rootY - viewport.y() + scroll;
        for (int index : visibleIndices) {
            Entry<T> entry = entries.get(index);
            if (entry.bounds().contains(localX, localY)) return entry;
        }
        return null;
    }

    @Override public Control hitTop(int rootX, int rootY) {
        if (!visible() || !absoluteBounds().contains(rootX, rootY)) return null;
        Entry<T> entry = hitItem(rootX, rootY);
        if (entry != null) {
            View<T> view = active.get(entry.index());
            if (view instanceof Control control) {
                Control hit = control.hitTop(rootX, rootY);
                if (hit != null) return hit;
            }
        }
        return super.hitTop(rootX, rootY);
    }

    @Override public boolean dispatchPointer(int rootX, int rootY) {
        if (!visible() || !absoluteBounds().contains(rootX, rootY)) return false;
        Entry<T> entry = hitItem(rootX, rootY);
        if (entry == null) return false;
        View<T> view = active.get(entry.index());
        if (view instanceof Control control && control.dispatchPointer(rootX, rootY)) return true;
        onItemClick.accept(entry.item());
        return true;
    }

    public Control hitFixed(int rootX, int rootY) {
        if (adapter != null || !absoluteBounds().contains(rootX, rootY)) return null;
        for (Control child : fixedChildren)
            if (child.visible()
                    && child.absoluteBounds().contains(rootX + horizontalScroll, rootY + scroll)) return child;
        return null;
    }

    public enum LayoutMode {ROWS, MASONRY}

    public interface Adapter<T> {
        int size();

        T item(int index);

        Object key(T item);

        int measureHeight(T item, int cardWidth, Font font);

        View<T> createView();
    }

    public interface View<T> {
        void bind(T item);

        void draw(ModularGuiContext context, Rect screenBounds, boolean hovered);
    }

    public record Entry<T>(int index, T item, Object key, Rect bounds) {
    }
}
