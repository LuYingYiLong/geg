package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.theme.OreIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Stable-key tree with virtual rows. Wrap in Scroll; this control owns no scroll offset.
 */
public final class Tree<T> extends Container implements ScrollContent {
    private static final int SWITCHER_WIDTH = 16;
    private final Set<String> collapsed = new HashSet<>();
    private final List<Entry<T>> visible = new ArrayList<>();
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final ArrayDeque<Row> recycled = new ArrayDeque<>();
    private final Map<String, Item<T>> byKey = new LinkedHashMap<>();
    private final Set<String> branches = new HashSet<>();
    private final Label widthProbe = new Label();
    private List<Item<T>> items = List.of();
    private String selected = "", cursor = "", query = "";
    private Rect region = new Rect(0, 0, 0, 0);
    private Font font;
    private int contentWidth;
    private Consumer<T> onSelect = value -> {
    };
    private Consumer<Expansion<T>> onExpand = event -> {
    };
    private int rowHeight = 22, indent = 10;
    private boolean disabled, selectable = true, blockNode = true, showIcon, showSwitcher = true, defaultExpandAll = true;
    private BiFunction<Item<T>, Boolean, Button.Icon> switcherIcon = (item, expanded) -> new Button.Icon(
            expanded ? OreIcons.ARROW_DOWN_LIGHT : OreIcons.ARROW_RIGHT_LIGHT, 7, 7);
    public Tree() {
        focusable(true);
    }

    public Tree<T> items(List<Item<T>> items) {
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

    public List<Item<T>> items() {
        return items;
    }

    public int rowHeight() {
        return rowHeight;
    }

    public Tree<T> rowHeight(int height) {
        if (height < Button.Size.SMALL.height())
            throw new IllegalArgumentException("Tree row height must be at least 18");
        if (rowHeight != height) {
            rowHeight = height;
            rebuild();
        }
        return this;
    }

    public int indent() {
        return indent;
    }

    public Tree<T> indent(int indent) {
        if (indent < 0) throw new IllegalArgumentException("Negative tree indent");
        this.indent = indent;
        rebuild();
        return this;
    }

    public boolean disabled() {
        return disabled;
    }

    public Tree<T> disabled(boolean disabled) {
        this.disabled = disabled;
        if (disabled) cancelInteraction();
        updateRows();
        return this;
    }

    @Override
    public boolean acceptsFocus() {
        return !disabled && super.acceptsFocus();
    }

    public boolean selectable() {
        return selectable;
    }

    public Tree<T> selectable(boolean selectable) {
        this.selectable = selectable;
        updateRows();
        return this;
    }

    public boolean blockNode() {
        return blockNode;
    }

    public Tree<T> blockNode(boolean block) {
        blockNode = block;
        updateRows();
        return this;
    }

    public boolean showIcon() {
        return showIcon;
    }

    public Tree<T> showIcon(boolean show) {
        showIcon = show;
        rebuild();
        return this;
    }

    public boolean showSwitcher() {
        return showSwitcher;
    }

    public Tree<T> showSwitcher(boolean show) {
        showSwitcher = show;
        rebuild();
        return this;
    }

    public Tree<T> switcherIcon(BiFunction<Item<T>, Boolean, Button.Icon> icon) {
        switcherIcon = Objects.requireNonNull(icon);
        updateRows();
        return this;
    }

    public BiFunction<Item<T>, Boolean, Button.Icon> switcherIcon() {
        return switcherIcon;
    }

    /**
     * Applies to subsequently added nodes; expandAll changes existing nodes.
     */
    public Tree<T> defaultExpandAll(boolean expand) {
        defaultExpandAll = expand;
        return this;
    }

    public boolean defaultExpandAll() {
        return defaultExpandAll;
    }

    public Set<String> expandedKeys() {
        Set<String> result = new LinkedHashSet<>();
        for (String key : byKey.keySet()) if (branches.contains(key) && !collapsed.contains(key)) result.add(key);
        return Collections.unmodifiableSet(result);
    }

    /**
     * Programmatic changes are silent; onExpand reports user actions only.
     */
    public Tree<T> expandedKeys(Collection<String> keys) {
        if (!byKey.keySet().containsAll(keys)) throw new IllegalArgumentException("Unknown tree expansion key");
        collapsed.clear();
        collapsed.addAll(branches);
        collapsed.removeAll(keys);
        rebuild();
        return this;
    }

    public Tree<T> expandAll(boolean expand) {
        if (expand) collapsed.clear();
        else collapsed.addAll(branches);
        rebuild();
        return this;
    }

    public Tree<T> onExpand(Consumer<Expansion<T>> callback) {
        onExpand = Objects.requireNonNull(callback);
        return this;
    }

    public Tree<T> search(String query) {
        String next = query.toLowerCase(Locale.ROOT);
        if (!this.query.equals(next)) {
            this.query = next;
            rebuild();
        }
        return this;
    }

    public Tree<T> onSelect(Consumer<T> callback) {
        onSelect = Objects.requireNonNull(callback);
        return this;
    }

    public Tree<T> selected(String key) {
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
            if (visible.get(i).item.key().equals(key) && parent() instanceof Scroll scroll)
                ensureEntryVisible(i, scroll);
        return this;
    }

    @Override
    public Rect focusBounds() {
        for (int i = 0; i < visible.size(); i++)
            if (visible.get(i).item.key().equals(cursor))
                return new Rect(absoluteBounds().x(), absoluteBounds().y() + i * rowHeight, bounds().width(), rowHeight);
        return absoluteBounds();
    }

    public String selected() {
        return selected;
    }

    public int visibleRows() {
        return visible.size();
    }

    public int instantiatedRows() {
        return rows.size();
    }

    public Tree<T> expanded(String key, boolean expanded) {
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
        expanded(entry.item.key(), expand);
        onExpand.accept(new Expansion<>(entry.item, expand, expandedKeys()));
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
            selected = cursor;
            updateRows();
            onSelect.accept(entry.item.value());
        }
        if (parent() instanceof Scroll scroll) for (int i = 0; i < visible.size(); i++)
            if (visible.get(i).item.key().equals(cursor)) ensureEntryVisible(i, scroll);
    }

    @Override
    public Size measureSize(Font font, Size available) {
        return new Size(minimumWidth(), minimumHeight());
    }

    @Override
    public Size contentExtent() {
        return new Size(contentWidth, visible.size() * rowHeight);
    }

    @Override
    public void visibleRegion(Rect region) {
        this.region = region;
        updateRows();
    }

    @Override
    public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        this.font = font;
        measureContentWidth();
        updateRows();
    }

    private void measureContentWidth() {
        contentWidth = bounds().width();
        for (Entry<T> entry : visible) {
            int width = labelWidth(entry.item);
            contentWidth = Math.max(contentWidth, entry.depth * indent + switcherWidth() + width);
        }
    }

    private void ensureEntryVisible(int index, Scroll scroll) {
        int x = visible.get(index).depth * indent;
        scroll.ensureVisible(new Rect(x, index * rowHeight, Math.min(scroll.viewport().width(), Math.max(0, contentWidth - x)), rowHeight));
    }

    private int switcherWidth() {
        return showSwitcher ? SWITCHER_WIDTH : 0;
    }

    private int labelWidth(Item<T> item) {
        String text = item.text().getString();
        int width = font == null ? text.codePointCount(0, text.length()) * 6 : widthProbe.text(item.text()).measureWidth(font, Integer.MAX_VALUE);
        return width + 2 * Button.PADDING_X + (showIcon && item.icon() != null ? item.icon().width() + 4 : 0);
    }

    private void updateRows() {
        int start = Math.max(0, (region.y() - region.height()) / rowHeight);
        int end = Math.min(visible.size(), (region.bottom() + region.height() + rowHeight - 1) / rowHeight);
        Set<String> live = new HashSet<>();
        for (int i = start; i < end; i++) {
            Entry<T> entry = visible.get(i);
            String key = entry.item.key();
            live.add(key);
            Row row = rows.get(key);
            if (row == null) {
                row = recycled.isEmpty() ? new Row() : recycled.remove();
                rows.put(key, row);
                super.addChild(row);
                io.github.luyingyilong.geg.client.ui.debug.DebugBridge.backend().binding(row, key);
            }
            row.bind(entry);
            layoutChild(row, new Rect(0, i * rowHeight, contentWidth, rowHeight), font);
        }
        for (String key : List.copyOf(rows.keySet()))
            if (!live.contains(key)) {
                Row row = rows.remove(key);
                super.removeChild(row);
                if (recycled.size() < 128) recycled.add(row);
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
                          boolean disabled, boolean selectable, Button.Icon icon) {
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
        private final Button twist = new Button().type(Button.Type.TEXT);
        private final Button label = new Button().type(Button.Type.TEXT).toggleButton(true).alignment(Button.TextAlignment.LEFT);
        private Entry<T> entry;

        Row() {
            twist.focusable(false);
            label.focusable(false);
            super.addChild(twist);
            super.addChild(label);
            twist.onPress(() -> userExpand(entry, !displayedExpanded(entry.item.key())));
            label.onPress(() -> select(entry));
        }

        void bind(Entry<T> entry) {
            this.entry = entry;
            twist.visible(showSwitcher && entry.branch);
            twist.icon(switcherIcon.apply(entry.item, displayedExpanded(entry.item.key()))).disabled(disabled || entry.item.disabled());
            twist.size(rowHeight < Button.Size.MEDIUM.height() ? Button.Size.SMALL : Button.Size.MEDIUM);
            label.size(rowHeight < Button.Size.MEDIUM.height() ? Button.Size.SMALL : Button.Size.MEDIUM);
            label.disabled(disabled || entry.item.disabled() || !selectable || !entry.item.selectable());
            label.icon(showIcon ? entry.item.icon() : null);
            label.label(entry.item.text().copy().withStyle(disabled || entry.item.disabled() || !selectable || !entry.item.selectable()
                    ? net.minecraft.ChatFormatting.GRAY : net.minecraft.ChatFormatting.WHITE));
            label.buttonPressedNoSignal(selected.equals(entry.item.key()));
        }

        @Override
        public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            int x = entry.depth * indent, offset = switcherWidth();
            layoutChild(twist, new Rect(x, 0, offset, rowHeight), font);
            int width = Math.max(0, bounds.width() - x - offset);
            layoutChild(label, new Rect(x + offset, 0, blockNode ? width : Math.min(width, labelWidth(entry.item)), rowHeight), font);
        }
    }
}
