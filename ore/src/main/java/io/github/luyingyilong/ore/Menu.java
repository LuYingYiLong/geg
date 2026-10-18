package io.github.luyingyilong.ore;

import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeKey;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Padding;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.ScrollContent;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Box-based, single-selection menu without an outer background or inset. Wrap in Scroll for overflow. */
public final class Menu extends Container implements ScrollContent {
    private final Box sections = new Box().gap(8);
    private final Label empty = new Label().color(OreTheme.TEXT_WHITE).wrap(Label.Wrap.WORD);
    private final WidgetThemeKey<WidgetTheme> dividerTheme;
    private final Supplier<Button> buttonFactory;
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final List<Label> emptyLabels = new ArrayList<>();
    private int emptyColor = OreTheme.TEXT_WHITE;
    private List<MenuItem> items = List.of();
    private Consumer<MenuItem> onSelect = item -> { };
    private String selectedKey;
    private Size extent = new Size(0, 0);
    private Rect visibleRegion = new Rect(0, 0, 0, 0);
    private Anchor anchor;
    private int restoredScrollY = -1;

    public Menu() { this(OreTheme.H_DIVIDER, Button::new); }

    Menu(WidgetThemeKey<WidgetTheme> dividerTheme, Supplier<Button> buttonFactory) {
        this.dividerTheme = dividerTheme;
        this.buttonFactory = Objects.requireNonNull(buttonFactory);
        sections.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        empty.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        super.addChild(sections);
        rebuild();
    }

    public Menu items(List<MenuItem> items) {
        List<MenuItem> next = List.copyOf(items);
        validateKeys(next, new HashSet<>());
        rememberAnchor();
        this.items = next;
        rebuild();
        return this;
    }

    public List<MenuItem> items() { return items; }
    public Menu onSelect(Consumer<MenuItem> callback) { onSelect = Objects.requireNonNull(callback); return this; }
    public String selectedKey() { return selectedKey; }
    /** Update selection without triggering an action. Missing keys clear the selection. */
    public Menu selectedKey(String key) {
        selectedKey = buttons.containsKey(key) ? key : null;
        buttons.forEach((id, button) -> button.buttonPressedNoSignal(Objects.equals(id, selectedKey)));
        return this;
    }
    public Button button(String key) { return buttons.get(key); }
    public Menu empty(Component message) { empty.text(message); return this; }
    public int emptyColor() { return emptyColor; }
    public Menu emptyColor(int color) {
        emptyColor = color;
        empty.color(color);
        for (Label label : emptyLabels) label.color(color);
        return this;
    }

    @Override public Control addChild(Control child) {
        throw new UnsupportedOperationException("Use Menu.items to populate the menu");
    }
    @Override public boolean removeChild(Control child) { return false; }

    private static void validateKeys(List<MenuItem> items, Set<String> keys) {
        for (MenuItem item : items) {
            if (!keys.add(item.key())) throw new IllegalArgumentException("Duplicate menu key: " + item.key());
            validateKeys(item.children(), keys);
        }
    }

    private void rebuild() {
        Map<String, Button> previous = new HashMap<>(buttons);
        for (Button button : previous.values()) button.parent().removeChild(button);
        for (Control child : sections.children()) sections.removeChild(child);
        buttons.clear();
        emptyLabels.clear();
        append(sections, items, previous, false);
        if (buttons.isEmpty() && items.isEmpty()) {
            if (empty.parent() != null) empty.parent().removeChild(empty);
            sections.add(empty);
        }
        selectedKey(selectedKey);
        requestLayout();
    }

    private void append(Box parent, List<MenuItem> items, Map<String, Button> previous, boolean disabled) {
        Box run = null;
        for (MenuItem item : items) {
            if (item.type() == MenuItem.Type.GROUP) {
                run = null;
                Box group = verticalBox();
                Label title = new Label(item.label()).color(OreTheme.TEXT_SECONDARY)
                        .overflow(Label.Overflow.ELLIPSIS).align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
                title.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
                Padding heading = new Padding(new Padding.Insets(0, Button.PADDING_X, 0, Button.PADDING_X)).child(title);
                heading.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
                group.add(heading).add(new Divider(dividerTheme));
                Box children = verticalBox();
                append(children, item.children(), previous, disabled || item.disabled());
                group.add(children);
                parent.add(group);
            } else if (item.type() == MenuItem.Type.EMPTY) {
                if (run == null) { run = verticalBox(); parent.add(run); }
                Label label = new Label(item.label()).color(emptyColor).wrap(Label.Wrap.WORD)
                        .align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
                label.minimum(0, Button.HEIGHT);
                emptyLabels.add(label);
                Padding inset = new Padding(new Padding.Insets(0, Button.PADDING_X, 0, Button.PADDING_X)).child(label);
                inset.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
                run.add(inset);
            } else {
                if (run == null) { run = verticalBox(); parent.add(run); }
                Button button = previous.get(item.key());
                if (button == null) button = Objects.requireNonNull(buttonFactory.get());
                button.label(item.label()).icon(item.icon()).type(Button.Type.MENU)
                        .alignment(Button.TextAlignment.LEFT).toggleButton(true).disabled(disabled || item.disabled())
                        .onPressed(() -> { selectedKey(item.key()); onSelect.accept(item); });
                button.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
                buttons.put(item.key(), button);
                run.add(button);
            }
        }
    }

    private static Box verticalBox() {
        Box box = new Box().gap(0);
        box.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        return box;
    }

    @Override public Size measureSize(Font font, Size available) {
        Size desired = sections.measureSize(font, available);
        return new Size(Math.max(minimumWidth(), Math.min(available.width(), desired.width())),
                Math.max(minimumHeight(), desired.height()));
    }
    @Override public int measureWidth(Font font, int available) { return measureSize(font, new Size(available, available)).width(); }
    @Override public int measureHeight(Font font, int available) { return measureSize(font, new Size(available, available)).height(); }

    @Override public void layout(Rect bounds, Font font) {
        if (bounds.width() != bounds().width()) rememberAnchor();
        super.layout(bounds, font);
        int height = Math.max(bounds.height(), sections.measureSize(font, new Size(bounds.width(), bounds.height())).height());
        extent = new Size(bounds.width(), height);
        fitChild(sections, new Rect(0, 0, extent.width(), extent.height()), font);
        if (anchor != null && !buttons.isEmpty()) {
            List<Button> ordered = List.copyOf(buttons.values());
            Button target = buttons.getOrDefault(anchor.key(), ordered.get(Math.min(anchor.index(), ordered.size() - 1)));
            restoredScrollY = Math.max(0, target.absoluteBounds().y() - absoluteBounds().y() + anchor.offset());
        }
        // Scroll may measure several widths while deciding which bars to show.
        // Keep the original key through those provisional layouts until it consumes the anchor.
    }

    private void rememberAnchor() {
        if (anchor != null) return;
        int index = 0;
        for (var entry : buttons.entrySet()) {
            Rect area = entry.getValue().absoluteBounds();
            int top = area.y() - absoluteBounds().y();
            if (top + area.height() > visibleRegion.y()) {
                anchor = new Anchor(entry.getKey(), index, visibleRegion.y() - top);
                return;
            }
            index++;
        }
    }

    @Override public Size contentExtent() { return extent; }
    @Override public void visibleRegion(Rect region) { visibleRegion = region; }
    @Override public int consumeRestoredScrollY() {
        int y = restoredScrollY;
        restoredScrollY = -1;
        anchor = null;
        return y;
    }
    @Override public void clearScrollAnchor() { anchor = null; restoredScrollY = -1; }
    private record Anchor(String key, int index, int offset) { }
}
