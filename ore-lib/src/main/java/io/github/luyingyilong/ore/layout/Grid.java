package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.debug.DebugProperty;
import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.*;
import java.util.function.Consumer;

/**
 * A regular or masonry grid with optional view recycling
 */
public final class Grid<T> extends Container implements ScrollContent {
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
    private int contentHeight;
    private int contentWidth;
    private int lastWidth = -1;
    private Object anchorKey;
    private int anchorOffset;
    private int restoredScrollY = -1;
    private boolean resetScrollPending;
    private boolean dirty = true;
    private boolean visibilityDirty = true;
    private Consumer<T> onItemClick = item -> { };
    private Rect visibleRegion = new Rect(0, 0, 0, 0);
    private Object focusedKey;
    private Font layoutFont;

    public Grid() {
        focusable(true);
        focusExpansion(1);
    }

    @Override public boolean acceptsFocus() {
        return super.acceptsFocus() && !entries.isEmpty();
    }

    private Entry<T> focusedEntry() {
        for (Entry<T> entry : entries)
            if (Objects.equals(entry.key(), focusedKey)) return entry;
        if (entries.isEmpty()) return null;
        for (Entry<T> entry : entries)
            if (entry.bounds().right() > visibleRegion.x()
                    && entry.bounds().x() < visibleRegion.right()
                    && entry.bounds().bottom() > visibleRegion.y()
                    && entry.bounds().y() < visibleRegion.bottom()) return entry;
        return entries.get(0);
    }

    @Override public Rect focusBounds() {
        Entry<T> entry = focusedEntry();
        if (entry != null && active.get(entry.index()) instanceof Control control) return control.absoluteBounds();
        return entry == null ? absoluteBounds()
                : entry.bounds().translated(absoluteBounds().x(), absoluteBounds().y());
    }

    @Override public boolean onFocusMove(FocusManager.Direction direction) {
        Entry<T> current = focusedEntry();
        if (current == null) return false;
        Entry<T> best = null;
        long bestScore = Long.MAX_VALUE;
        Rect from = current.bounds();
        for (Entry<T> candidate : entries) {
            if (candidate == current) continue;
            Rect to = candidate.bounds();
            long dx = (long) to.x() * 2 + to.width() - ((long) from.x() * 2 + from.width());
            long dy = (long) to.y() * 2 + to.height() - ((long) from.y() * 2 + from.height());
            long primary = switch (direction) {
                case LEFT -> -dx;
                case RIGHT -> dx;
                case UP -> -dy;
                case DOWN -> dy;
            };
            if (primary <= 0) continue;
            long secondary = direction == FocusManager.Direction.LEFT
                    || direction == FocusManager.Direction.RIGHT ? Math.abs(dy) : Math.abs(dx);
            long score = primary * 1000 + secondary;
            if (score < bestScore) { best = candidate; bestScore = score; }
        }
        if (best == null) return false;
        focusedKey = best.key();
        if (parent() instanceof Scroll scroll) scroll.ensureVisible(best.bounds().translated(bounds().x(), bounds().y()));
        return true;
    }

    @Override public boolean activate() {
        Entry<T> entry = focusedEntry();
        if (entry == null) return false;
        onItemClick.accept(entry.item());
        return true;
    }

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

    @Inspectable
    public LayoutMode mode() { return DebugBridge.value(this, "mode", mode); }

    public Grid<T> mode(LayoutMode mode) {
        Objects.requireNonNull(mode);
        if (DebugBridge.previewWrite(this, "mode", mode)) return this;
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
        requestLayout();
    }

    @Override public void invalidateTextLayout() {
        invalidate();
        super.invalidateTextLayout();
    }

    public List<Entry<T>> entries() {
        return List.copyOf(entries);
    }

    public int contentHeight() {
        return contentHeight;
    }

    public int contentWidth() { return contentWidth; }

    @Override public Size contentExtent() { return new Size(contentWidth, contentHeight); }

    @Override public void visibleRegion(Rect region) {
        Rect next = new Rect(Math.max(0, region.x()), Math.max(0, region.y()),
                Math.max(0, region.width()), Math.max(0, region.height()));
        if (!next.equals(visibleRegion)) {
            visibleRegion = next;
            visibilityDirty = true;
            updateVisible();
        }
    }

    @Override public int consumeRestoredScrollY() {
        int result = restoredScrollY;
        restoredScrollY = -1;
        anchorKey = null;
        resetScrollPending = false;
        return result;
    }

    @Override public void clearScrollAnchor() {
        anchorKey = null;
        restoredScrollY = -1;
        resetScrollPending = true;
        dirty = true;
        requestLayout();
    }

    public int activeViews() {
        return active.size();
    }

    public int columns() {
        int minCardWidth = DebugBridge.value(this, "minCardWidth", this.minCardWidth);
        int maxColumns = DebugBridge.value(this, "maxColumns", this.maxColumns);
        int gap = DebugBridge.value(this, "gap", this.gap);
        int width = Math.max(1, bounds().width());
        return Math.max(1, Math.min(maxColumns, (width + gap) / (minCardWidth + gap)));
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        boolean childLayoutChanged = layoutDirty();
        layoutFont = font;
        super.layout(localBounds, font);
        if (dirty || lastWidth != localBounds.width() || childLayoutChanged && adapter == null) relayout(font);
        if (childLayoutChanged) visibilityDirty = true;
        if (visibleRegion.width() == 0 && visibleRegion.height() == 0)
            visibleRegion(new Rect(0, 0, localBounds.width(), localBounds.height()));
        if (adapter != null) updateVisible();
    }

    private void saveAnchor() {
        if (anchorKey != null) return;
        for (Entry<T> entry : entries) {
            if (entry.bounds().bottom() > visibleRegion.y()
                    && entry.bounds().y() <= visibleRegion.y()) {
                anchorKey = entry.key();
                anchorOffset = visibleRegion.y() - entry.bounds().y();
                return;
            }
        }
        anchorKey = null;
    }

    private void relayout(Font font) {
        LayoutMode mode = DebugBridge.value(this, "mode", this.mode);
        int minCardWidth = DebugBridge.value(this, "minCardWidth", this.minCardWidth);
        int gap = DebugBridge.value(this, "gap", this.gap);
        if (!resetScrollPending && lastWidth != -1 && lastWidth != bounds().width()
                && !dirty) saveAnchor();
        Map<Object, ArrayDeque<View<T>>> previous = new HashMap<>();
        for (Entry<T> entry : entries) {
            View<T> view = active.get(entry.index());
            if (view != null) previous.computeIfAbsent(entry.key(), key -> new ArrayDeque<>()).add(view);
        }
        entries.clear();
        lastWidth = bounds().width();
        dirty = false;
        if (adapter == null) {
            relayoutFixed(font);
            return;
        }
        if (adapter.size() == 0) {
            contentHeight = 0;
            contentWidth = 0;
            recycleAll();
            restoredScrollY = 0;
            return;
        }
        int columns = columns();
        int cardWidth = Math.max(minCardWidth, (bounds().width() - (columns - 1) * gap) / columns);
        contentWidth = columns * cardWidth + (columns - 1) * gap;
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
        if (resetScrollPending) restoredScrollY = 0;
        else if (anchorKey != null) {
            for (Entry<T> entry : entries)
                if (anchorKey.equals(entry.key())) {
                    restoredScrollY = entry.bounds().y() + anchorOffset;
                    break;
                }
        }
        // Provisional Scroll measurements may cross a column breakpoint every frame.
        // Keep each item's view (and its marquee/input state) attached to its stable key.
        Map<Integer, View<T>> retained = new HashMap<>();
        for (Entry<T> entry : entries) {
            ArrayDeque<View<T>> candidates = previous.get(entry.key());
            View<T> view = candidates == null ? null : candidates.pollFirst();
            if (view != null) {
                if (view instanceof Control control) DebugBridge.backend().binding(control, entry.key());
                view.bind(entry.item());
                retained.put(entry.index(), view);
            }
        }
        for (ArrayDeque<View<T>> remaining : previous.values())
            for (View<T> view : remaining) {
                if (view instanceof Control control) super.removeChild(control);
                recycled.add(view);
            }
        active.clear();
        active.putAll(retained);
        visibilityDirty = true;
    }

    private void relayoutFixed(Font font) {
        LayoutMode mode = DebugBridge.value(this, "mode", this.mode);
        int minCardWidth = DebugBridge.value(this, "minCardWidth", this.minCardWidth);
        int gap = DebugBridge.value(this, "gap", this.gap);
        if (fixedChildren.isEmpty()) {
            contentWidth = 0;
            contentHeight = 0;
            return;
        }
        int columns = columns();
        int cardWidth = Math.max(minCardWidth, (bounds().width() - (columns - 1) * gap) / columns);
        contentWidth = columns * cardWidth + (columns - 1) * gap;
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
            int height = Math.max(1, child.desiredSize(font, new Size(cardWidth, cardWidth)).height());
            int top = mode == LayoutMode.ROWS ? rowY : heights[column];
            fitChild(child, new Rect(column * (cardWidth + gap), top, cardWidth, height), font);
            if (mode == LayoutMode.ROWS) rowHeight = Math.max(rowHeight, height);
            else heights[column] += height + gap;
        }
        contentHeight = mode == LayoutMode.ROWS ? rowY + rowHeight
                : Math.max(0, Arrays.stream(heights).max().orElse(0) - gap);
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
        if (adapter == null || dirty || !visibilityDirty) return;
        visibilityDirty = false;
        int buffer = visibleRegion.height();
        int start = visibleRegion.y() - buffer;
        int end = visibleRegion.bottom() + buffer;
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
                if (view instanceof Control control) DebugBridge.backend().binding(control, entry.key());
                view.bind(entry.item());
                if (view instanceof Control control) super.addChild(control);
                active.put(entry.index(), view);
            }
        for (int index : visibleIndices)
            if (active.get(index) instanceof Control control)
                fitChild(control, entries.get(index).bounds(), layoutFont);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        DebugBridge.backend().paint(this, context, screenX, screenY);
        if (bounds().width() <= 0 || bounds().height() <= 0) return;
        if (adapter != null) updateVisible();
        Rect origin = screenBounds(screenX, screenY);
        Rect viewport = visibleRegion.translated(origin.x(), origin.y());
        if (adapter == null) {
            for (Control child : fixedChildren) {
                Rect area = child.absoluteBounds();
                if (area.bottom() >= absoluteBounds().y() + visibleRegion.y() - visibleRegion.height()
                        && area.y() <= absoluteBounds().y() + visibleRegion.bottom() + visibleRegion.height()) {
                    child.draw(context, screenX, screenY);
                }
            }
        } else {
            for (int index : visibleIndices) {
                Entry<T> entry = entries.get(index);
                View<T> view = active.get(index);
                if (view == null) continue;
                Rect card = entry.bounds().translated(origin.x(), origin.y());
                boolean hovered = viewport.contains(context.getAbsMouseX(), context.getAbsMouseY())
                        && card.contains(context.getAbsMouseX(), context.getAbsMouseY());
                if (view instanceof Control control) {
                    control.draw(context, screenX, screenY);
                } else view.draw(context, card, hovered);
            }
        }
    }

    public Entry<T> hitItem(int rootX, int rootY) {
        Rect origin = absoluteBounds();
        int localX = rootX - origin.x();
        int localY = rootY - origin.y();
        if (!visibleRegion.contains(localX, localY)) return null;
        for (int index : visibleIndices) {
            Entry<T> entry = entries.get(index);
            if (entry.bounds().contains(localX, localY)) return entry;
        }
        return null;
    }

    @Override public Control hitTop(int rootX, int rootY) {
        if (!visible()) return null;
        if (adapter == null) return super.hitTop(rootX, rootY);
        Entry<T> entry = hitItem(rootX, rootY);
        if (entry != null) {
            View<T> view = active.get(entry.index());
            if (view instanceof Control control) {
                Control hit = control.hitTop(rootX, rootY);
                if (hit != null) return hit;
            }
        }
        return hit(rootX, rootY) && visibleRegion.contains(rootX - absoluteBounds().x(),
                rootY - absoluteBounds().y()) ? this : null;
    }

    @Override protected boolean pointerDown(int rootX, int rootY) {
        Entry<T> entry = hitItem(rootX, rootY);
        if (entry == null) return false;
        View<T> view = active.get(entry.index());
        if(view instanceof Control control && !control.hit(rootX,rootY))return false;
        focusedKey = entry.key();
        onItemClick.accept(entry.item());
        return true;
    }

    public Control hitFixed(int rootX, int rootY) {
        if (adapter != null) return null;
        for (Control child : fixedChildren)
            if (child.visible()
                    && child.absoluteBounds().contains(rootX, rootY)) return child;
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

    @Override public void debugProperties(java.util.List<DebugProperty> out) {
        super.debugProperties(out);
        out.add(DebugProperty.number(this, "minCardWidth", "Layout", () -> DebugBridge.value(this, "minCardWidth", this.minCardWidth), 1, 10000));
        out.add(DebugProperty.number(this, "maxColumns", "Layout", () -> DebugBridge.value(this, "maxColumns", this.maxColumns), 1, 10000));
        out.add(DebugProperty.number(this, "gap", "Layout", () -> DebugBridge.value(this, "gap", this.gap), 0, 10000));
    }

}
