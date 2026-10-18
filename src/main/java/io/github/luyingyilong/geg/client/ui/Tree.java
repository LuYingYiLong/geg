package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Stable-key tree with virtual rows. Wrap in Scroll; this control owns no scroll offset. */
public final class Tree<T> extends Container implements ScrollContent {
    public record Item<T>(String key, String parent, Component text, T value) { }
    private record Entry<T>(Item<T> item, int depth, boolean branch) { }
    private final Set<String> collapsed = new HashSet<>();
    private final List<Entry<T>> visible = new ArrayList<>();
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final ArrayDeque<Row> recycled = new ArrayDeque<>();
    private List<Item<T>> items = List.of();
    private String selected = "", query = "";
    private Rect region = new Rect(0, 0, 0, 0);
    private Font font;
    private int contentWidth;
    private final Label widthProbe = new Label();
    private Consumer<T> onSelect = value -> { };
    private static final int ROW = 22, INDENT = 10;

    public Tree() { focusable(true); }
    public Tree<T> items(List<Item<T>> items) {
        Set<String> keys = new HashSet<>();
        for (Item<T> item : items) if (item.key().isEmpty() || !keys.add(item.key())) throw new IllegalArgumentException("Tree keys must be unique and nonempty");
        this.items = List.copyOf(items); collapsed.retainAll(keys); rebuild(); return this;
    }
    public Tree<T> search(String query) { String next = query.toLowerCase(Locale.ROOT); if (!this.query.equals(next)) { this.query = next; rebuild(); } return this; }
    public Tree<T> onSelect(Consumer<T> callback) { onSelect = Objects.requireNonNull(callback); return this; }
    public Tree<T> selected(String key) {
        if (selected.equals(key)) return this;
        selected = key; Map<String, Item<T>> byId = new HashMap<>(); items.forEach(item -> byId.put(item.key(), item));
        Set<String> seen = new HashSet<>(); boolean changed = false;
        for (Item<T> item=byId.get(key); item!=null&&seen.add(item.key()); item=byId.get(item.parent())) changed |= collapsed.remove(item.parent());
        if (changed) rebuild(); else updateRows();
        for (int i=0;i<visible.size();i++) if (visible.get(i).item.key().equals(key) && parent() instanceof Scroll scroll)
            ensureEntryVisible(i, scroll);
        return this;
    }
    @Override public Rect focusBounds() {
        for (int i=0;i<visible.size();i++) if (visible.get(i).item.key().equals(selected))
            return new Rect(absoluteBounds().x(),absoluteBounds().y()+i*ROW,bounds().width(),ROW);
        return absoluteBounds();
    }
    public String selected() { return selected; }
    public int visibleRows() { return visible.size(); }
    public int instantiatedRows() { return rows.size(); }
    public Tree<T> expanded(String key, boolean expanded) { if (expanded) collapsed.remove(key); else collapsed.add(key); rebuild(); return this; }
    private void rebuild() {
        Map<String, List<Item<T>>> children = new LinkedHashMap<>(); Map<String, Item<T>> byKey = new HashMap<>();
        for (Item<T> item : items) { byKey.put(item.key(), item); children.computeIfAbsent(item.parent(), k -> new ArrayList<>()).add(item); }
        Set<String> included = new HashSet<>();
        if (!query.isEmpty()) for (Item<T> item : items) if (item.text().getString().toLowerCase(Locale.ROOT).contains(query)) {
            Set<String> seen = new HashSet<>();
            for (Item<T> p = item; p != null && seen.add(p.key()); p = byKey.get(p.parent())) included.add(p.key());
        }
        visible.clear(); walk("", 0, children, included, new HashSet<>()); measureContentWidth(); requestLayout(); updateRows();
    }
    private void walk(String parent, int depth, Map<String, List<Item<T>>> children, Set<String> included, Set<String> seen) {
        for (Item<T> item : children.getOrDefault(parent, List.of())) {
            if (!seen.add(item.key())) throw new IllegalArgumentException("Cyclic tree");
            if (!query.isEmpty() && !included.contains(item.key())) continue;
            boolean branch = children.containsKey(item.key()); visible.add(new Entry<>(item, depth, branch));
            if (branch && (!collapsed.contains(item.key()) || !query.isEmpty())) walk(item.key(), depth + 1, children, included, seen);
        }
    }
    private void select(Entry<T> entry) { selected = entry.item.key(); updateRows(); onSelect.accept(entry.item.value()); }
    @Override public Size measureSize(Font font, Size available) { return new Size(minimumWidth(), minimumHeight()); }
    @Override public Size contentExtent() { return new Size(contentWidth, visible.size() * ROW); }
    @Override public void visibleRegion(Rect region) { this.region = region; updateRows(); }
    @Override public void layout(Rect bounds, Font font) { super.layout(bounds, font); this.font = font; measureContentWidth(); updateRows(); }
    private void measureContentWidth() {
        contentWidth = bounds().width();
        for (Entry<T> entry : visible) {
            String text = entry.item.text().getString();
            int width = font == null ? text.codePointCount(0, text.length()) * 6
                    : widthProbe.text(entry.item.text()).measureWidth(font, Integer.MAX_VALUE);
            contentWidth = Math.max(contentWidth, entry.depth * INDENT + 16 + width + 2 * Button.PADDING_X);
        }
    }
    private void ensureEntryVisible(int index, Scroll scroll) {
        int x = visible.get(index).depth * INDENT;
        scroll.ensureVisible(new Rect(x, index * ROW, Math.min(scroll.viewport().width(), Math.max(0, contentWidth-x)), ROW));
    }
    private void updateRows() {
        int start = Math.max(0, (region.y() - region.height()) / ROW);
        int end = Math.min(visible.size(), (region.bottom() + region.height() + ROW - 1) / ROW);
        Set<String> live = new HashSet<>();
        for (int i = start; i < end; i++) {
            Entry<T> entry = visible.get(i); String key = entry.item.key(); live.add(key);
            Row row = rows.get(key);
            if (row == null) { row = recycled.isEmpty() ? new Row() : recycled.remove(); rows.put(key, row); super.addChild(row);
                io.github.luyingyilong.geg.client.ui.debug.DebugBridge.backend().binding(row,key); }
            row.bind(entry); layoutChild(row, new Rect(0, i * ROW, contentWidth, ROW), font);
        }
        for (String key : List.copyOf(rows.keySet())) if (!live.contains(key)) { Row row=rows.remove(key); super.removeChild(row); if (recycled.size() < 128) recycled.add(row); }
    }
    @Override public boolean onFocusMove(FocusManager.Direction direction) {
        int index = 0; for (int i = 0; i < visible.size(); i++) if (visible.get(i).item.key().equals(selected)) index = i;
        if (visible.isEmpty()) return false;
        if (direction == FocusManager.Direction.LEFT || direction == FocusManager.Direction.RIGHT) {
            expanded(visible.get(index).item.key(), direction == FocusManager.Direction.RIGHT); return true;
        }
        index = Math.max(0, Math.min(visible.size() - 1, index + (direction == FocusManager.Direction.UP ? -1 : 1)));
        select(visible.get(index)); if (parent() instanceof Scroll scroll) ensureEntryVisible(index, scroll); return true;
    }
    private final class Row extends Container {
        private final Button twist = new Button().type(Button.Type.TEXT);
        private final Button label = new Button().type(Button.Type.TEXT).toggleButton(true).alignment(Button.TextAlignment.LEFT);
        private Entry<T> entry;
        Row() {
            twist.focusable(false); label.focusable(false); super.addChild(twist); super.addChild(label);
            twist.onPress(() -> expanded(entry.item.key(), collapsed.contains(entry.item.key())));
            label.onPress(() -> select(entry));
        }
        void bind(Entry<T> entry) {
            this.entry = entry; twist.visible(entry.branch); twist.label(Component.literal(collapsed.contains(entry.item.key()) ? ">" : "v").withStyle(net.minecraft.ChatFormatting.WHITE));
            label.label(entry.item.text().copy().withStyle(net.minecraft.ChatFormatting.WHITE)); label.buttonPressedNoSignal(selected.equals(entry.item.key()));
        }
        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font); int x = entry.depth * INDENT;
            layoutChild(twist, new Rect(x, 0, 16, ROW), font); layoutChild(label, new Rect(x + 16, 0, Math.max(0, bounds.width() - x - 16), ROW), font);
        }
    }
}
