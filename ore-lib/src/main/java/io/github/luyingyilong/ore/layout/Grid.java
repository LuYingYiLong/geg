package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.signal.*;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.debug.DebugProperty;
import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.*;

/**
 * A regular or masonry grid with optional view recycling
 */
public final class Grid<T> extends Container implements ScrollContent {
    private final List<Entry<T>> entries = new ArrayList<>();
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
    private final SignalSource<T> itemActivated = new SignalSource<>(this, "itemActivated", "T");
    private Rect visibleRegion = new Rect(0, 0, 0, 0);
    private Object focusedKey;
    private Font layoutFont;

    public Grid() {
        getSignalScope().own(() -> { active.clear(); recycled.clear(); entries.clear(); visibleIndices.clear(); });
        setFocusable(true);
        setFocusExpansion(1);
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

    @Override public Rect getFocusBounds() {
        Entry<T> entry = focusedEntry();
        if (entry != null && active.get(entry.index()) instanceof Control control) return control.getAbsoluteBounds();
        return entry == null ? getAbsoluteBounds()
                : entry.bounds().translated(getAbsoluteBounds().x(), getAbsoluteBounds().y());
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
        if (getParent() instanceof Scroll scroll) scroll.ensureVisible(best.bounds().translated(getBounds().x(), getBounds().y()));
        return true;
    }

    @Override public boolean activate() {
        Entry<T> entry = focusedEntry();
        if (entry == null) return false;
        itemActivated.emit(entry.item());
        return true;
    }

    private static int shortest(int[] heights) {
        int index = 0;
        for (int candidate = 1; candidate < heights.length; candidate++)
            if (heights[candidate] < heights[index]) index = candidate;
        return index;
    }

    public Grid<T> setAdapter(Adapter<T> adapter) {
        if (adapter != null && this.adapter == null && getChildCount() != 0)
            throw new IllegalStateException("A Grid cannot mix fixed children and an adapter");
        if (this.adapter != adapter) {
            recycleAll();
            for (View<T> view : recycled) if (view instanceof Control control) control.queueFree();
            recycled.clear();
        }
        this.adapter = adapter;
        invalidate();
        return this;
    }

    public Signal<T> itemActivated() { return itemActivated.signal(); }

    @Override protected void validateChildren(List<Control> proposed) {
        if (adapter != null && !isUpdatingChildren()
                && proposed.stream().anyMatch(child -> child.getParent() != this))
            throw new IllegalStateException("An adapter Grid owns its views; use setAdapter");
    }

    @Override protected void childrenChanged() {
        if (adapter == null) invalidate();
        else active.values().removeIf(view -> view instanceof Control control && control.getParent() != this);
    }

    @Inspectable
    public LayoutMode getMode() { return DebugBridge.value(this, "mode", mode); }

    public Grid<T> setMode(LayoutMode mode) {
        Objects.requireNonNull(mode);
        if (DebugBridge.previewWrite(this, "mode", mode)) return this;
        this.mode = mode;
        invalidate();
        return this;
    }

    public Grid<T> setColumns(int minCardWidth, int maxColumns, int gap) {
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

    public List<Entry<T>> getEntries() {
        return List.copyOf(entries);
    }

    public int getContentHeight() {
        return contentHeight;
    }

    public int getContentWidth() { return contentWidth; }

    @Override public Size getContentExtent() { return new Size(contentWidth, contentHeight); }

    @Override public void setVisibleRegion(Rect region) {
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

    public int getActiveViews() {
        return active.size();
    }

    public int getColumns() {
        int minCardWidth = DebugBridge.value(this, "minCardWidth", this.minCardWidth);
        int maxColumns = DebugBridge.value(this, "maxColumns", this.maxColumns);
        int gap = DebugBridge.value(this, "gap", this.gap);
        int width = Math.max(1, getBounds().width());
        return Math.max(1, Math.min(maxColumns, (width + gap) / (minCardWidth + gap)));
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            boolean childLayoutChanged = getLayoutDirty();
            layoutFont = font;
            super.layout(localBounds, font);
            if (dirty || lastWidth != localBounds.width() || childLayoutChanged && adapter == null) relayout(font);
            if (childLayoutChanged) visibilityDirty = true;
            if (visibleRegion.width() == 0 && visibleRegion.height() == 0)
                setVisibleRegion(new Rect(0, 0, localBounds.width(), localBounds.height()));
            if (adapter != null) updateVisible();
        }
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
        if (!resetScrollPending && lastWidth != -1 && lastWidth != getBounds().width()
                && !dirty) saveAnchor();
        Map<Object, ArrayDeque<View<T>>> previous = new HashMap<>();
        for (Entry<T> entry : entries) {
            View<T> view = active.get(entry.index());
            if (view instanceof Control control && !control.isAlive()) { removeChild(control); continue; }
            if (view != null) previous.computeIfAbsent(entry.key(), key -> new ArrayDeque<>()).add(view);
        }
        entries.clear();
        lastWidth = getBounds().width();
        dirty = false;
        if (adapter == null) {
            relayoutFixed(font);
            return;
        }
        if (adapter.getSize() == 0) {
            contentHeight = 0;
            contentWidth = 0;
            recycleAll();
            restoredScrollY = 0;
            return;
        }
        int columns = getColumns();
        int cardWidth = Math.max(minCardWidth, (getBounds().width() - (columns - 1) * gap) / columns);
        contentWidth = columns * cardWidth + (columns - 1) * gap;
        int[] heights = new int[columns];
        Arrays.fill(heights, 0);
        int rowY = 0;
        int rowHeight = 0;
        for (int index = 0; index < adapter.getSize(); index++) {
            T item = adapter.getItem(index);
            int column = mode == LayoutMode.ROWS ? index % columns : shortest(heights);
            if (mode == LayoutMode.ROWS && column == 0 && index > 0) {
                rowY += rowHeight + gap;
                rowHeight = 0;
            }
            int cardHeight = Math.max(1, adapter.measureHeight(item, cardWidth, font));
            int cardY = mode == LayoutMode.ROWS ? rowY : heights[column];
            entries.add(new Entry<>(index, item, adapter.getKey(item),
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
                if (view instanceof Control control) { ownPooled(control); control.resetBindingScope(); DebugBridge.backend().binding(control, entry.key()); }
                view.bind(entry.item());
                retained.put(entry.index(), view);
            }
        }
        for (ArrayDeque<View<T>> remaining : previous.values())
            for (View<T> view : remaining) {
                if (view instanceof Control control) { control.resetBindingScope(); removeChild(control); }
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
        List<Control> visible = getChildren().stream()
                .filter(child -> child.getParent() == this && child.isAlive() && child.getVisible()).toList();
        if (visible.isEmpty()) {
            contentWidth = 0;
            contentHeight = 0;
            return;
        }
        int columns = getColumns();
        int cardWidth = Math.max(minCardWidth, (getBounds().width() - (columns - 1) * gap) / columns);
        contentWidth = columns * cardWidth + (columns - 1) * gap;
        int[] heights = new int[columns];
        int rowY = 0;
        int rowHeight = 0;
        for (int index = 0; index < visible.size(); index++) {
            Control child = visible.get(index);
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



    private void pruneDeadViews() {
        recycled.removeIf(view -> view instanceof Control control && !control.isAlive());
        for (int key : List.copyOf(active.keySet())) { View<T> view = active.get(key); if (view instanceof Control control && !control.isAlive()) { active.remove(key); removeChild(control); } }
    }

    private void recycleAll() {
        pruneDeadViews();
        List<View<T>> previous = List.copyOf(active.values());
        for (View<T> view : previous)
            if (view instanceof Control control) { control.resetBindingScope(); removeChild(control); }
        recycled.addAll(previous);
        active.clear();
        visibleIndices.clear();
        visibilityDirty = true;
    }

    private void updateVisible() {
        if (adapter == null || dirty || !visibilityDirty) return;
        pruneDeadViews();
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
                if (view instanceof Control control) { control.resetBindingScope(); removeChild(control); }
                recycled.add(view);
            }
        for (Entry<T> entry : entries)
            if (visible.contains(entry.index()) && !active.containsKey(entry.index())) {
                View<T> view = recycled.isEmpty() ? adapter.createView() : recycled.removeFirst();
                if (view instanceof Control control) { ownPooled(control); control.resetBindingScope(); DebugBridge.backend().binding(control, entry.key()); }
                view.bind(entry.item());
                if (view instanceof Control control) {
                    List<Control> next = new ArrayList<>(getChildren());
                    next.add(control);
                    updateChildren(next);
                }
                active.put(entry.index(), view);
            }
        int childIndex = 0;
        for (int index : visibleIndices) if (active.get(index) instanceof Control control) moveChild(control, childIndex++);
        for (int index : visibleIndices)
            if (active.get(index) instanceof Control control)
                fitChild(control, entries.get(index).bounds(), layoutFont);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            if (getBounds().width() <= 0 || getBounds().height() <= 0) return;
            if (adapter != null) updateVisible();
            Rect origin = getScreenBounds(screenX, screenY);
            Rect viewport = visibleRegion.translated(origin.x(), origin.y());
            if (adapter == null) {
                for (Control child : getChildren()) {
                    if (child.getParent() != this || !child.isAlive() || !child.getVisible()) continue;
                    Rect area = child.getAbsoluteBounds();
                    if (area.bottom() >= getAbsoluteBounds().y() + visibleRegion.y() - visibleRegion.height()
                            && area.y() <= getAbsoluteBounds().y() + visibleRegion.bottom() + visibleRegion.height()) {
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
    }

    public Entry<T> hitItem(int rootX, int rootY) {
        Rect origin = getAbsoluteBounds();
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
        if (!isAlive() || !getVisible()) return null;
        if (adapter == null) return super.hitTop(rootX, rootY);
        Entry<T> entry = hitItem(rootX, rootY);
        if (entry != null) {
            View<T> view = active.get(entry.index());
            if (view instanceof Control control) {
                Control hit = control.hitTop(rootX, rootY);
                if (hit != null) return hit;
            }
        }
        return hit(rootX, rootY) && visibleRegion.contains(rootX - getAbsoluteBounds().x(),
                rootY - getAbsoluteBounds().y()) ? this : null;
    }

    @Override protected boolean pointerDown(int rootX, int rootY) {
        Entry<T> entry = hitItem(rootX, rootY);
        if (entry == null) return false;
        View<T> view = active.get(entry.index());
        if(view instanceof Control control && !control.hit(rootX,rootY))return false;
        focusedKey = entry.key();
        itemActivated.emit(entry.item());
        return true;
    }

    public Control hitFixed(int rootX, int rootY) {
        if (adapter != null) return null;
        for (Control child : getChildren())
            if ((child.isAlive() && child.getVisible())
                    && child.getAbsoluteBounds().contains(rootX, rootY)) return child;
        return null;
    }

    public enum LayoutMode {ROWS, MASONRY}

    public interface Adapter<T> {
        int getSize();

        T getItem(int index);

        Object getKey(T item);

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
