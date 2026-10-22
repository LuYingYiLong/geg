package io.github.luyingyilong.ore.control.navigation;

import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.signal.DispatchGuard;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Top-aligned tabs with retained pages and a single keyboard stop in the header. */
public final class Tabs extends Container {
    public static final ThemeKey<StyleBox> NORMAL = style("normal", false);
    public static final ThemeKey<StyleBox> HOVER = style("hover", false);
    public static final ThemeKey<StyleBox> PRESSED = style("pressed", true);
    public static final ThemeKey<StyleBox> SELECTED = style("selected", true);
    public static final ThemeKey<StyleBox> DISABLED = style("disabled", false);
    public static final ThemeKey<StyleBox> SELECTED_INDICATOR = ThemeKey.of("Tabs", "selected_indicator", StyleBox.class,
            ThemeKey.Kind.STYLE, new StyleBoxFlat(0xFFFFFFFF, ThemeInsets.ZERO, 0, ThemeInsets.ZERO, ThemeInsets.ZERO), false);
    public static final ThemeKey<Integer> FONT_COLOR = color("font_color", OreTheme.TEXT_WHITE);
    public static final ThemeKey<Integer> FONT_HOVER_COLOR = color("font_hover_color", OreTheme.TEXT_WHITE);
    public static final ThemeKey<Integer> FONT_PRESSED_COLOR = color("font_pressed_color", OreTheme.TEXT_WHITE);
    public static final ThemeKey<Integer> FONT_SELECTED_COLOR = color("font_selected_color", OreTheme.TEXT_WHITE);
    public static final ThemeKey<Integer> FONT_DISABLED_COLOR = color("font_disabled_color", 0x80FFFFFF);
    public static final ThemeKey<Integer> HEADER_HEIGHT = integer("header_height", 22);
    public static final ThemeKey<Integer> MINIMUM_ITEM_WIDTH = integer("minimum_item_width", 24);
    public static final ThemeKey<Integer> ICON_GAP = integer("icon_gap", 4);
    public static final ThemeKey<Integer> ICON_SIZE = integer("icon_size", 8);
    public static final ThemeKey<Integer> ITEM_GAP = integer("item_gap", 0);
    public static final ThemeKey<Integer> INDICATOR_HEIGHT = integer("selected_indicator_height", 1);
    public static final ThemeKey<Integer> INDICATOR_MAX_WIDTH = integer("selected_indicator_max_width", 16);
    public static final ThemeKey<Integer> INDICATOR_BOTTOM_GAP = integer("selected_indicator_bottom_gap", 1);
    public static final ThemeKey<Float> INDICATOR_WIDTH_RATIO = new ThemeKey<>("Tabs", "selected_indicator_width_ratio",
            Float.class, ThemeKey.Kind.FLOAT, 0.33333334F, false, v -> Float.isFinite(v) && v >= 0 && v <= 1);

    private final List<Entry> entries = new ArrayList<>();
    final TabsBar bar = new TabsBar(this);
    final Scroll header = new Scroll().setHorizontal(Scroll.BarMode.NEVER).setVertical(Scroll.BarMode.DISABLED).setContent(bar);
    private final SignalSource<String> selectionChanged = new SignalSource<>(this, "selectionChanged", "String");
    private final SignalSource<String> tabActivated = new SignalSource<>(this, "tabActivated", "String");
    private String selectedKey;
    private int mutationDepth;
    private int maxTabWidth;

    public Tabs() {
        header.setDebugName("Tabs header viewport");
        bar.setDebugName("Tabs tab bar");
        updateChildren(List.of(header));
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Tabs");
        for (var key : List.of(NORMAL, HOVER, PRESSED, SELECTED, DISABLED, SELECTED_INDICATOR,
                FONT_COLOR, FONT_HOVER_COLOR, FONT_PRESSED_COLOR, FONT_SELECTED_COLOR, FONT_DISABLED_COLOR,
                HEADER_HEIGHT, MINIMUM_ITEM_WIDTH, ICON_GAP, ICON_SIZE, ITEM_GAP, INDICATOR_HEIGHT, INDICATOR_MAX_WIDTH,
                INDICATOR_BOTTOM_GAP, INDICATOR_WIDTH_RATIO)) db.register(key);
        db.register(ThemeKey.of("Tabs", "focus", StyleBox.class, ThemeKey.Kind.STYLE, OreTheme.FOCUS.defaultValue(), false));
    }

    private static ThemeKey<StyleBox> style(String name, boolean inset) {
        int height = inset ? 5 : 7;
        var region = TextureRegion.full(ResourceLocation.fromNamespaceAndPath("ore", "textures/gui/tabs/tab_" + name + ".png"), 5, height);
        return ThemeKey.of("Tabs", name, StyleBox.class, ThemeKey.Kind.STYLE,
                new StyleBoxTexture(region, new ThemeInsets(2, 2, 2, inset ? 2 : 4), new ThemeInsets(8, 4, 8, 4),
                        ThemeInsets.ZERO, StyleBoxTexture.AxisMode.STRETCH, StyleBoxTexture.AxisMode.STRETCH, true, 1,
                        inset ? new ThemeInsets(0, 2, 0, 0) : ThemeInsets.ZERO,
                        inset ? new Offset(0, 2) : Offset.ZERO), true);
    }

    private static ThemeKey<Integer> color(String name, int value) {
        return ThemeKey.of("Tabs", name, Integer.class, ThemeKey.Kind.COLOR, value, false);
    }

    private static ThemeKey<Integer> integer(String name, int value) {
        return new ThemeKey<>("Tabs", name, Integer.class, ThemeKey.Kind.INTEGER, value, true,
                v -> v >= 0 && v <= 1_000_000);
    }

    public List<TabItem> getItems() { return entries.stream().map(e -> e.item).toList(); }
    public TabItem getItem(String key) { Entry entry = entry(key); return entry == null ? null : entry.item; }
    public String getSelectedKey() { return selectedKey; }
    public int getHeaderScrollX() { return header.getScrollX(); }

    /** Zero leaves each tab at its natural width; positive values cap its complete rectangle. */
    @io.github.luyingyilong.ore.debug.Inspectable(min = 0, max = Integer.MAX_VALUE)
    public int getMaxTabWidth() { return io.github.luyingyilong.ore.debug.DebugBridge.value(this, "maxTabWidth", maxTabWidth); }

    public Tabs setMaxTabWidth(int width) {
        checkAlive();
        if (width < 0) throw new IllegalArgumentException("Maximum tab width must be non-negative");
        if (io.github.luyingyilong.ore.debug.DebugBridge.previewWrite(this, "maxTabWidth", width)) return this;
        if (maxTabWidth != width) {
            cancelHeaderGesture();
            maxTabWidth = width;
            bar.revealFocus = true;
            requestLayout();
        }
        return this;
    }

    public Signal<String> selectionChanged() { return selectionChanged.signal(); }
    public Signal<String> tabActivated() { return tabActivated.signal(); }

    public Tabs addItem(TabItem item) {
        checkAlive(); Objects.requireNonNull(item);
        if (entry(item.getKey()) != null) throw new IllegalArgumentException("Duplicate tab key: " + item.getKey());
        validateContent(item.getContent());
        String before = selectedKey;
        try (var guard = DispatchGuard.enter()) {
            mutationDepth++;
            try {
                Entry entry = new Entry(item);
                entries.add(entry);
                synchronizeChildren();
                if (selectedKey == null && !item.getDisabled()) changeSelection(item.getKey());
            } finally { mutationDepth--; }
            if (!Objects.equals(before, selectedKey)) selectionChanged.emit(selectedKey);
        }
        return this;
    }

    public Tabs setItem(TabItem item) {
        checkAlive(); Objects.requireNonNull(item);
        Entry entry = requireEntry(item.getKey());
        if (item.getContent() != entry.item.getContent()) validateContent(item.getContent());
        String before = selectedKey;
        try (var guard = DispatchGuard.enter()) {
            mutationDepth++;
            try {
                cancelHeaderGesture();
                if (item.getContent() != entry.item.getContent()) {
                    releasePageInteraction(entry);
                    entry.page.setContent(item.getContent());
                }
                entry.item = item;
                entry.caption.update(item);
                if (Objects.equals(selectedKey, item.getKey()) && item.getDisabled())
                    changeSelection(fallback(entries.indexOf(entry), false));
                else if (selectedKey == null && !item.getDisabled()) changeSelection(item.getKey());
                bar.synchronizeFocus();
                requestLayout();
            } finally { mutationDepth--; }
            if (!Objects.equals(before, selectedKey)) selectionChanged.emit(selectedKey);
        }
        return this;
    }

    /** Detaches the content for reuse. Only generated header/page wrappers are queued for deletion. */
    public TabItem removeItem(String key) {
        checkAlive();
        Entry entry = entry(key);
        if (entry == null) return null;
        String before = selectedKey;
        try (var guard = DispatchGuard.enter()) {
            mutationDepth++;
            try {
                cancelHeaderGesture();
                releasePageInteraction(entry);
                int index = entries.indexOf(entry);
                entries.remove(entry);
                if (Objects.equals(selectedKey, key)) changeSelection(fallback(index, true));
                entry.page.clearContent();
                synchronizeChildren();
                entry.caption.queueFree("tab removed");
                entry.page.queueFree("tab removed");
            } finally { mutationDepth--; }
            if (!Objects.equals(before, selectedKey)) selectionChanged.emit(selectedKey);
        }
        return entry.item;
    }

    public Tabs moveItem(String key, int index) {
        checkAlive();
        Entry entry = requireEntry(key);
        if (index < 0 || index >= entries.size()) throw new IndexOutOfBoundsException(index);
        if (entries.indexOf(entry) == index) return this;
        mutationDepth++;
        try {
            cancelHeaderGesture();
            entries.remove(entry); entries.add(index, entry);
            synchronizeChildren();
            bar.revealFocus = true;
        } finally { mutationDepth--; }
        return this;
    }

    public Tabs setSelectedKey(String key) {
        String before = selectedKey;
        setSelectedKeyNoSignal(key);
        if (!Objects.equals(before, selectedKey)) selectionChanged.emit(selectedKey);
        return this;
    }

    public Tabs setSelectedKeyNoSignal(String key) {
        checkAlive();
        if (key == null) {
            if (entries.stream().anyMatch(e -> !e.item.getDisabled())) throw new IllegalArgumentException("An enabled page must be selected");
        } else if (requireEntry(key).item.getDisabled()) throw new IllegalArgumentException("Tab is disabled: " + key);
        changeSelection(key);
        return this;
    }

    void activate(String key) {
        Entry target = entry(key);
        if (target == null || target.item.getDisabled() || !isInputAccessible()) return;
        OreSounds.play(OreSounds.Cue.CLICK);
        setSelectedKey(key);
        tabActivated.emit(key);
    }

    private void changeSelection(String key) {
        if (Objects.equals(selectedKey, key)) return;
        Entry previous = entry(selectedKey);
        if (previous != null) releasePageInteraction(previous);
        cancelHeaderGesture();
        selectedKey = key;
        for (Entry entry : List.copyOf(entries)) entry.page.setVisible(Objects.equals(entry.item.getKey(), key));
        bar.focusedKey = key;
        bar.revealFocus = true;
        bar.synchronizeFocus();
        requestLayout();
    }

    /** Called before MUI's normal focus transfer can commit/reset the current editor. */
    void suspendPageEditor() {
        Entry current = entry(selectedKey);
        if (current != null) suspendEditors(current.page);
    }

    private static void suspendEditors(Control node) {
        if (node instanceof InputControl input) input.suspendHostFocus();
        for (Control child : node.getChildren()) if (child.isAlive()) suspendEditors(child);
    }

    private void releasePageInteraction(Entry entry) {
        var window = getWindowContext().getOwner();
        boolean containedFocus = false;
        if (window != null) for (Control node = window.getFocusManager().getFocused(); node != null; node = node.getParent())
            if (node == entry.page) { containedFocus = true; break; }
        suspendEditors(entry.page);
        if (window != null) {
            window.getContext().getPopups().cancelFrom(entry.page);
            if (containedFocus)
                window.getFocusManager().restoreFocus(bar, window.getContext().getMui());
        }
        entry.page.cancelInteractions();
    }

    private void cancelHeaderGesture() {
        bar.cancelInteraction();
        var window = getWindowContext().getOwner();
        if (window != null) window.cancelCaptures(bar, io.github.luyingyilong.ore.event.CancelEvent.Reason.REBOUND);
    }

    private void validateContent(Control content) {
        if (content.getGeometryParent() != null) throw new IllegalStateException("Tab content already has an owner");
        validateChildAttachment(content);
    }

    private void checkAlive() {
        getSignalScope().checkThread();
        if (!isAlive()) throw new IllegalStateException("Tabs is pending/freed");
    }

    Entry entry(String key) {
        return entries.stream().filter(e -> Objects.equals(e.item.getKey(), key)).findFirst().orElse(null);
    }

    private Entry requireEntry(String key) {
        Entry entry = entry(key);
        if (entry == null) throw new IllegalArgumentException("Unknown tab: " + key);
        return entry;
    }

    List<Entry> entries() { return List.copyOf(entries); }

    private String fallback(int index, boolean removed) {
        for (int i = removed ? index : index + 1; i < entries.size(); i++)
            if (!entries.get(i).item.getDisabled()) return entries.get(i).item.getKey();
        for (int i = index - 1; i >= 0; i--)
            if (!entries.get(i).item.getDisabled()) return entries.get(i).item.getKey();
        return null;
    }

    private void synchronizeChildren() {
        List<Control> children = new ArrayList<>(); children.add(header);
        entries.forEach(e -> children.add(e.page));
        updateChildren(children);
        bar.setCaptions(entries.stream().map(e -> (Control) e.caption).toList());
        bar.synchronizeFocus();
        requestLayout();
    }

    @Override protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren()) throw new IllegalStateException("Tabs has a fixed skeleton; use item APIs");
    }

    @Override protected void childrenChanged() {
        if (mutationDepth != 0 || !isAlive()) return;
        for (Entry entry : List.copyOf(entries))
            if (entry.page.getParent() != this || !entry.caption.isAlive() || !entry.item.getContent().isAlive()
                    || entry.item.getContent().getParent() != entry.page) removeItem(entry.item.getKey());
    }

    @Override public int getMinimumWidth() {
        Entry selected = entry(selectedKey);
        return Math.max(super.getMinimumWidth(), selected == null ? 0 : selected.page.getMinimumWidth());
    }

    @Override public int getMinimumHeight() {
        Entry selected = entry(selectedKey);
        return Math.max(super.getMinimumHeight(), bar.minimumHeight() + (selected == null ? 0 : selected.page.getMinimumHeight()));
    }

    @Override public int measureWidth(Font font, int available) {
        Entry selected = entry(selectedKey);
        return !isAlive() ? 0 : Math.max(getMinimumWidth(), Math.max(Math.min(available, bar.measureWidth(font, available)),
                selected == null ? 0 : selected.page.desiredSize(font, new Size(available, Integer.MAX_VALUE)).width()));
    }

    @Override public int measureHeight(Font font, int available) { return measureSize(font, new Size(available, available)).height(); }

    @Override public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        int headerHeight = bar.measureHeight(font, available.width());
        Entry selected = entry(selectedKey);
        Size page = selected == null ? new Size(0, 0) : selected.page.desiredSize(font,
                new Size(available.width(), Math.max(0, available.height() - headerHeight)));
        return new Size(Math.max(getMinimumWidth(), Math.max(Math.min(available.width(), bar.measureWidth(font, available.width())), page.width())),
                Math.max(getMinimumHeight(), headerHeight + page.height()));
    }

    @Override public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var guard = DispatchGuard.enter()) {
            super.layout(bounds, font);
            int height = bar.measureHeight(font, bounds.width());
            layoutChild(header, new Rect(0, 0, bounds.width(), height), font);
            Entry selected = entry(selectedKey);
            if (selected != null) fitChild(selected.page, new Rect(0, height, bounds.width(), Math.max(0, bounds.height() - height)), font);
            bar.reveal();
        }
    }

    @Override public String getThemeState() { return "selected=" + selectedKey + ", pages=" + entries.size(); }

    static final class Entry {
        TabItem item;
        final Page page;
        final TabsBar.Caption caption;
        Entry(TabItem item) {
            this.item = item;
            page = new Page(item.getKey(), item.getContent());
            caption = new TabsBar.Caption(item);
        }
    }

    static final class Page extends Container {
        Page(String key, Control content) {
            setDebugName("Tab page: " + key);
            setVisible(false);
            updateChildren(List.of(content));
        }
        void setContent(Control content) { updateChildren(List.of(content)); }
        void clearContent() { updateChildren(List.of()); }
        Control content() { return getChildCount() == 0 ? null : getChild(0); }
        @Override protected void validateChildren(List<Control> proposed) {
            if (!isUpdatingChildren()) throw new IllegalStateException("Tab page belongs to Tabs");
        }
        @Override public int getMinimumWidth() { return Math.max(super.getMinimumWidth(), content() == null ? 0 : content().getMinimumWidth()); }
        @Override public int getMinimumHeight() { return Math.max(super.getMinimumHeight(), content() == null ? 0 : content().getMinimumHeight()); }
        @Override public Size measureSize(Font font, Size available) { return content() == null ? getMinimumSize() : content().desiredSize(font, available); }
        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            if (content() != null && content().getVisible()) fitChild(content(), new Rect(0, 0, bounds.width(), bounds.height()), font);
        }
    }
}
