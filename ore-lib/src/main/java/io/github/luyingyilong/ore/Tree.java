package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.IconTheme;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Stable-key tree with virtual rows. Wrap in Scroll; this control owns no scroll offset.
 */
public final class Tree<T> extends Container implements ScrollContent {

    public static final ThemeKey<TextureRegion> EXPANDED_ICON = IconTheme.item("Tree", "expanded_icon", IconTheme.ARROW_DOWN_LIGHT);
    public static final ThemeKey<TextureRegion> COLLAPSED_ICON = IconTheme.item("Tree", "collapsed_icon", IconTheme.ARROW_RIGHT_LIGHT);
    private static final int SWITCHER_WIDTH = 16;
    private final Set<String> collapsed = new HashSet<>();
    private final List<Entry<T>> visible = new ArrayList<>();
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final ArrayDeque<Row> recycled = new ArrayDeque<>();
    private final Map<String, Item<T>> byKey = new LinkedHashMap<>();
    private final Set<String> branches = new HashSet<>();
    private final Label widthProbe = ownPart("widthProbe", new Label());
    private final SignalSource<T> selectionChanged = new SignalSource<>(this, "selectionChanged", "T");
    private final SignalSource<Expansion<T>> expansionChanged = new SignalSource<>(this, "expansionChanged", "Expansion<T>");
    private List<Item<T>> items = List.of();
    private String selected = "", cursor = "", query = "";
    private Rect region = new Rect(0, 0, 0, 0);
    private Font font;
    private int contentWidth;
    private int rowHeight = 22, indent = 10;
    private boolean disabled, selectable = true, blockNode = true, showIcon, showSwitcher = true, defaultExpandAll = true;
    private BiFunction<Item<T>, Boolean, TextureSource> switcherIcon = (item, expanded) -> TextureSource.themed(expanded ? EXPANDED_ICON : COLLAPSED_ICON);
    public Tree() {
        getSignalScope().own(() -> {
            rows.clear();
            recycled.clear();
        });
        setFocusable(true);
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Tree");
        IconTheme.registerTheme(db);
        db.register(EXPANDED_ICON);
        db.registerFallback(EXPANDED_ICON, IconTheme.ARROW_DOWN_LIGHT);
        db.register(COLLAPSED_ICON);
        db.registerFallback(COLLAPSED_ICON, IconTheme.ARROW_RIGHT_LIGHT);
    }

    @Override
    protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren() && proposed.stream().anyMatch(child -> child.getParent() != this))
            throw new IllegalStateException("Tree owns virtual rows; use setItems");
    }

    @Override
    protected void childrenChanged() {
        rows.values().removeIf(row -> row.getParent() != this);
    }

    public List<Item<T>> getItems() {
        return items;
    }

    public Tree<T> setItems(List<Item<T>> items) {
        Set<String> keys = new HashSet<>();
        for (Item<T> item : items)
            if (item.key().isEmpty() || !keys.add(item.key()))
                throw new IllegalArgumentException("Tree keys must be unique and nonempty");
        Map<String, Item<T>> next = new LinkedHashMap<>();
        items.forEach(item -> next.put(item.key(), item));
        Set<String> checked = new HashSet<>();
        for (Item<T> item : items) {
            Set<String> path = new HashSet<>();
            for (Item<T> node = item; node != null && !checked.contains(node.key()); node = next.get(node.parent())) {
                if (!path.add(node.key())) throw new IllegalArgumentException("Cyclic tree");
                if (!node.parent().isEmpty() && !next.containsKey(node.parent()))
                    throw new IllegalArgumentException("Missing tree parent: " + node.parent());
            }
            checked.addAll(path);
        }
        collapsed.retainAll(keys);
        if (!defaultExpandAll) for (String key : keys) if (!byKey.containsKey(key)) collapsed.add(key);
        this.items = List.copyOf(items);
        byKey.clear();
        byKey.putAll(next);
        branches.clear();
        items.forEach(item -> {
            if (!item.parent().isEmpty()) branches.add(item.parent());
        });
        if (!keys.contains(selected)) selected = "";
        if (!keys.contains(cursor)) cursor = selected;
        rebuild();
        return this;
    }

    public int getRowHeight() {
        return rowHeight;
    }

    public Tree<T> setRowHeight(int height) {
        if (height < Button.Size.SMALL.getHeight())
            throw new IllegalArgumentException("Tree row height must be at least 18");
        if (rowHeight != height) {
            rowHeight = height;
            rebuild();
        }
        return this;
    }

    public int getIndent() {
        return indent;
    }

    public Tree<T> setIndent(int indent) {
        if (indent < 0) throw new IllegalArgumentException("Negative tree indent");
        this.indent = indent;
        rebuild();
        return this;
    }

    public boolean getDisabled() {
        return disabled;
    }

    public Tree<T> setDisabled(boolean disabled) {
        this.disabled = disabled;
        if (disabled) cancelInteraction();
        updateRows();
        return this;
    }

    @Override
    public boolean acceptsFocus() {
        return !disabled && super.acceptsFocus();
    }

    public boolean getSelectable() {
        return selectable;
    }

    public Tree<T> setSelectable(boolean selectable) {
        this.selectable = selectable;
        updateRows();
        return this;
    }

    public boolean getBlockNode() {
        return blockNode;
    }

    public Tree<T> setBlockNode(boolean block) {
        blockNode = block;
        updateRows();
        return this;
    }

    public boolean getShowIcon() {
        return showIcon;
    }

    public Tree<T> setShowIcon(boolean show) {
        showIcon = show;
        rebuild();
        return this;
    }

    public boolean getShowSwitcher() {
        return showSwitcher;
    }

    public Tree<T> setShowSwitcher(boolean show) {
        showSwitcher = show;
        rebuild();
        return this;
    }

    public BiFunction<Item<T>, Boolean, TextureSource> getSwitcherIcon() {
        return switcherIcon;
    }

    public Tree<T> setSwitcherIcon(BiFunction<Item<T>, Boolean, TextureSource> icon) {
        switcherIcon = Objects.requireNonNull(icon);
        updateRows();
        return this;
    }

    public boolean getDefaultExpandAll() {
        return defaultExpandAll;
    }

    /**
     * Applies to subsequently added nodes; expandAll changes existing nodes.
     */
    public Tree<T> setDefaultExpandAll(boolean expand) {
        defaultExpandAll = expand;
        return this;
    }

    public Set<String> getExpandedKeys() {
        Set<String> result = new LinkedHashSet<>();
        for (String key : byKey.keySet()) if (branches.contains(key) && !collapsed.contains(key)) result.add(key);
        return Collections.unmodifiableSet(result);
    }

    /**
     * Expansion setters notify on actual state changes; NoSignal variants support controlled refresh.
     */
    public Tree<T> setExpandedKeys(Collection<String> keys) {
        Set<String> before = getExpandedKeys();
        setExpandedKeysNoSignal(keys);
        for (String key : branches)
            if (before.contains(key) != !collapsed.contains(key))
                expansionChanged.emit(new Expansion<>(byKey.get(key), !collapsed.contains(key), getExpandedKeys()));
        return this;
    }

    public Tree<T> setExpandedKeysNoSignal(Collection<String> keys) {
        if (!byKey.keySet().containsAll(keys)) throw new IllegalArgumentException("Unknown tree expansion key");
        collapsed.clear();
        collapsed.addAll(branches);
        collapsed.removeAll(keys);
        rebuild();
        return this;
    }

    public Tree<T> expandAll(boolean expand) {
        return setExpandedKeys(expand ? Set.copyOf(branches) : Set.of());
    }

    public Signal<Expansion<T>> expansionChanged() {
        return expansionChanged.signal();
    }

    public Tree<T> setSearch(String query) {
        String next = query.toLowerCase(Locale.ROOT);
        if (!this.query.equals(next)) {
            this.query = next;
            rebuild();
        }
        return this;
    }

    public Signal<T> selectionChanged() {
        return selectionChanged.signal();
    }

    public Tree<T> setSelectedNoSignal(String key) {
        if (selected.equals(key)) return this;
        selected = key;
        cursor = key;
        Set<String> seen = new HashSet<>();
        boolean changed = false;
        for (Item<T> item = byKey.get(key); item != null && seen.add(item.key()); item = byKey.get(item.parent()))
            changed |= collapsed.remove(item.parent());
        if (changed) rebuild();
        else updateRows();
        for (int i = 0; i < visible.size(); i++)
            if (visible.get(i).item.key().equals(key) && getParent() instanceof Scroll scroll)
                ensureEntryVisible(i, scroll);
        return this;
    }

    @Override
    public Rect getFocusBounds() {
        for (int i = 0; i < visible.size(); i++)
            if (visible.get(i).item.key().equals(cursor))
                return new Rect(getAbsoluteBounds().x(), getAbsoluteBounds().y() + i * rowHeight, getBounds().width(), rowHeight);
        return getAbsoluteBounds();
    }

    public String getSelected() {
        return selected;
    }

    public Tree<T> setSelected(String key) {
        String previous = selected;
        setSelectedNoSignal(key);
        if (!Objects.equals(previous, selected))
            selectionChanged.emit(byKey.containsKey(selected) ? byKey.get(selected).value() : null);
        return this;
    }

    public int getVisibleRows() {
        return visible.size();
    }

    public int getInstantiatedRows() {
        return rows.size();
    }

    public Tree<T> setExpanded(String key, boolean expanded) {
        boolean before = !collapsed.contains(key);
        setExpandedNoSignal(key, expanded);
        if (before != expanded) expansionChanged.emit(new Expansion<>(byKey.get(key), expanded, getExpandedKeys()));
        return this;
    }

    public Tree<T> setExpandedNoSignal(String key, boolean expanded) {
        if (!byKey.containsKey(key)) throw new IllegalArgumentException("Unknown tree expansion key");
        if (expanded) collapsed.remove(key);
        else collapsed.add(key);
        rebuild();
        return this;
    }

    private boolean displayedExpanded(String key) {
        return !query.isEmpty() || !collapsed.contains(key);
    }

    private void userExpand(Entry<T> entry, boolean expand) {
        if (disabled || entry.item.disabled() || !entry.branch || displayedExpanded(entry.item.key()) == expand) return;
        setExpanded(entry.item.key(), expand);
    }

    private void rebuild() {
        Map<String, List<Item<T>>> children = new LinkedHashMap<>();
        Map<String, Item<T>> byKey = new HashMap<>();
        for (Item<T> item : items) {
            byKey.put(item.key(), item);
            children.computeIfAbsent(item.parent(), k -> new ArrayList<>()).add(item);
        }
        Set<String> included = new HashSet<>();
        if (!query.isEmpty()) for (Item<T> item : items)
            if (item.text().getString().toLowerCase(Locale.ROOT).contains(query)) {
                Set<String> seen = new HashSet<>();
                for (Item<T> p = item; p != null && seen.add(p.key()); p = byKey.get(p.parent())) included.add(p.key());
            }
        visible.clear();
        walk("", 0, children, included, new HashSet<>());
        measureContentWidth();
        requestLayout();
        updateRows();
    }

    private void walk(String parent, int depth, Map<String, List<Item<T>>> children, Set<String> included, Set<String> seen) {
        for (Item<T> item : children.getOrDefault(parent, List.of())) {
            if (!seen.add(item.key())) throw new IllegalArgumentException("Cyclic tree");
            if (!query.isEmpty() && !included.contains(item.key())) continue;
            boolean branch = children.containsKey(item.key());
            visible.add(new Entry<>(item, depth, branch));
            if (branch && (!collapsed.contains(item.key()) || !query.isEmpty()))
                walk(item.key(), depth + 1, children, included, seen);
        }
    }

    private void select(Entry<T> entry) {
        if (disabled || entry.item.disabled()) return;
        cursor = entry.item.key();
        if (selectable && entry.item.selectable()) {
            setSelected(cursor);
        }
        if (getParent() instanceof Scroll scroll) for (int i = 0; i < visible.size(); i++)
            if (visible.get(i).item.key().equals(cursor)) ensureEntryVisible(i, scroll);
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        return new Size(getMinimumWidth(), getMinimumHeight());
    }

    @Override
    public Size getContentExtent() {
        return new Size(contentWidth, visible.size() * rowHeight);
    }

    @Override
    public void setVisibleRegion(Rect region) {
        this.region = region;
        updateRows();
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            this.font = font;
            measureContentWidth();
            updateRows();
        }
    }

    private void measureContentWidth() {
        contentWidth = getBounds().width();
        for (Entry<T> entry : visible) {
            int width = labelWidth(entry.item);
            contentWidth = Math.max(contentWidth, entry.depth * indent + switcherWidth() + width);
        }
    }

    private void ensureEntryVisible(int index, Scroll scroll) {
        int x = visible.get(index).depth * indent;
        scroll.ensureVisible(new Rect(x, index * rowHeight, Math.min(scroll.getViewport().width(), Math.max(0, contentWidth - x)), rowHeight));
    }

    private int switcherWidth() {
        return showSwitcher ? SWITCHER_WIDTH : 0;
    }

    private int labelWidth(Item<T> item) {
        String text = item.text().getString();
        int width = font == null ? text.codePointCount(0, text.length()) * 6 : widthProbe.setText(item.text()).measureWidth(font, Integer.MAX_VALUE);
        return width + getThemeItem(io.github.luyingyilong.ore.theme.ThemeDB.getDefault().getKey("TextButton", "normal", io.github.luyingyilong.ore.theme.StyleBox.class)).getContentMargin().getHorizontal() + (showIcon && item.icon() != null ? item.icon().resolve(this).getWidth() + 4 : 0);
    }

    private void updateRows() {
        recycled.removeIf(row -> !row.isAlive());
        for (String key : List.copyOf(rows.keySet())) {
            Row row = rows.get(key);
            if (row != null && !row.isAlive()) {
                rows.remove(key);
                removeChild(row);
            }
        }
        int start = Math.max(0, (region.y() - region.height()) / rowHeight);
        int end = Math.min(visible.size(), (region.bottom() + region.height() + rowHeight - 1) / rowHeight);
        Set<String> live = new HashSet<>();
        for (int i = start; i < end; i++) {
            Entry<T> entry = visible.get(i);
            String key = entry.item.key();
            live.add(key);
            Row row = rows.get(key);
            if (row == null) {
                row = recycled.isEmpty() ? ownPooled(new Row()) : recycled.remove();
                rows.put(key, row);
                List<Control> next = new ArrayList<>(getChildren());
                next.add(row);
                updateChildren(next);
                io.github.luyingyilong.ore.debug.DebugBridge.backend().binding(row, key);
            }
            row.bind(entry);
            layoutChild(row, new Rect(0, i * rowHeight, contentWidth, rowHeight), font);
        }
        for (String key : List.copyOf(rows.keySet()))
            if (!live.contains(key)) {
                Row row = rows.remove(key);
                row.resetBindingScope();
                removeChild(row);
                if (recycled.size() < 128) recycled.add(row);
                else row.queueFree();
            }
        int childIndex = 0;
        for (int index = start; index < end; index++) {
            Row row = rows.get(visible.get(index).item.key());
            if (row != null) moveChild(row, childIndex++);
        }
    }

    @Override
    public boolean onFocusMove(FocusManager.Direction direction) {
        if (disabled || visible.isEmpty()) return false;
        int index = -1;
        for (int i = 0; i < visible.size(); i++) if (visible.get(i).item.key().equals(cursor)) index = i;
        if (index < 0) {
            for (Entry<T> entry : visible)
                if (!entry.item.disabled()) {
                    select(entry);
                    break;
                }
            return true;
        }
        Entry<T> current = visible.get(index);
        if (direction == FocusManager.Direction.LEFT || direction == FocusManager.Direction.RIGHT) {
            if (current.item.disabled()) return true;
            if (direction == FocusManager.Direction.RIGHT) {
                if (current.branch && !displayedExpanded(current.item.key())) userExpand(current, true);
                else if (current.branch)
                    for (int i = index + 1; i < visible.size() && visible.get(i).depth > current.depth; i++) {
                        if (!visible.get(i).item.disabled()) {
                            select(visible.get(i));
                            break;
                        }
                    }
            } else if (current.branch && displayedExpanded(current.item.key())) userExpand(current, false);
            else for (Entry<T> entry : visible)
                    if (entry.item.key().equals(current.item.parent())) {
                        select(entry);
                        break;
                    }
            return true;
        }
        int step = direction == FocusManager.Direction.UP ? -1 : 1;
        for (int next = index + step; next >= 0 && next < visible.size(); next += step)
            if (!visible.get(next).item.disabled()) {
                select(visible.get(next));
                break;
            }
        return true;
    }

    public record Item<T>(String key, String parent, Component text, T value,
                          boolean disabled, boolean selectable, TextureSource icon) {
        public Item(String key, String parent, Component text, T value) {
            this(key, parent, text, value, false, true, null);
        }

        public Item {
            Objects.requireNonNull(key);
            Objects.requireNonNull(parent);
            Objects.requireNonNull(text);
        }
    }

    public record Expansion<T>(Item<T> item, boolean expanded, Set<String> expandedKeys) {
    }

    private record Entry<T>(Item<T> item, int depth, boolean branch) {
    }


    private final class Row extends Container {
        private final Button twist = new Button().setThemeTypeVariation("TextButton");
        private final Button label = new Button().setThemeTypeVariation("TextButton").setToggleButton(true).setAlignment(Button.TextAlignment.LEFT);
        private Entry<T> entry;

        Row() {
            twist.setThemeParent(Tree.this);
            label.setThemeParent(Tree.this);
            twist.setFocusable(false);
            label.setFocusable(false);
            addChild(twist);
            addChild(label);
            twist.pressed().connect(twist, () -> userExpand(entry, !displayedExpanded(entry.item.key())));
            label.pressed().connect(label, () -> select(entry));
        }

        void bind(Entry<T> entry) {
            this.entry = entry;
            twist.setVisible(showSwitcher && entry.branch);
            twist.setIcon(switcherIcon.apply(entry.item, displayedExpanded(entry.item.key()))).setDisabled(disabled || entry.item.disabled());
            twist.setSize(rowHeight < Button.Size.MEDIUM.getHeight() ? Button.Size.SMALL : Button.Size.MEDIUM);
            label.setSize(rowHeight < Button.Size.MEDIUM.getHeight() ? Button.Size.SMALL : Button.Size.MEDIUM);
            label.setDisabled(disabled || entry.item.disabled() || !selectable || !entry.item.selectable());
            label.setIcon(showIcon ? entry.item.icon() : null);
            label.setLabel(entry.item.text().copy().withStyle(disabled || entry.item.disabled() || !selectable || !entry.item.selectable()
                    ? net.minecraft.ChatFormatting.GRAY : net.minecraft.ChatFormatting.WHITE));
            label.setButtonPressedNoSignal(selected.equals(entry.item.key()));
        }

        @Override
        public void layout(Rect bounds, Font font) {
            if (!isAlive()) return;
            try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
                super.layout(bounds, font);
                int x = entry.depth * indent, offset = switcherWidth();
                layoutChild(twist, new Rect(x, 0, offset, rowHeight), font);
                int width = Math.max(0, bounds.width() - x - offset);
                layoutChild(label, new Rect(x + offset, 0, blockNode ? width : Math.min(width, labelWidth(entry.item)), rowHeight), font);

            }
        }
    }
}
