package io.github.luyingyilong.ore.control.navigation;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Divider;
import io.github.luyingyilong.ore.control.layout.Padding;
import io.github.luyingyilong.ore.control.layout.ScrollContent;

import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalScope;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeKey;
import io.github.luyingyilong.ore.theme.ThemePadding;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Supplier;

/**
 * Box-based, single-selection menu without an outer background or inset. Wrap in Scroll for overflow.
 */
public final class Menu extends Container implements ScrollContent {
    public static final ThemeKey<Integer> EMPTY_FONT_COLOR = ThemeKey.of("MenuEmpty", "font_color", Integer.class,
            ThemeKey.Kind.COLOR, OreTheme.TEXT_WHITE, false);
    private static final ThemePadding PADDING = ThemePadding.of("Menu", "", 0, 8, 0, 8);
    public static final ThemeKey<Integer> PADDING_TOP = PADDING.top();
    public static final ThemeKey<Integer> PADDING_RIGHT = PADDING.right();
    public static final ThemeKey<Integer> PADDING_BOTTOM = PADDING.bottom();
    public static final ThemeKey<Integer> PADDING_LEFT = PADDING.left();

    private final Box sections = new Box().setGap(8);
    private final Label empty = new Label().setThemeTypeVariation("MenuEmpty").setWrap(Label.Wrap.WORD);
    private final ThemeKey<io.github.luyingyilong.ore.theme.StyleBox> dividerTheme;
    private final Supplier<Button> buttonFactory;
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final List<Padding> itemInsets = new ArrayList<>();
    private final SignalSource<MenuItem> itemActivated = new SignalSource<>(this, "itemActivated", "MenuItem");
    private final SignalSource<String> selectionChanged = new SignalSource<>(this, "selectionChanged", "String");
    private final Map<String, SignalScope> bindings = new HashMap<>();
    private List<MenuItem> items = List.of();
    private String selectedKey;
    private Size extent = new Size(0, 0);
    private Rect visibleRegion = new Rect(0, 0, 0, 0);
    private Anchor anchor;
    private int restoredScrollY = -1;

    public Menu() {
        this(OreTheme.H_DIVIDER, Button::new);
    }

    Menu(ThemeKey<io.github.luyingyilong.ore.theme.StyleBox> dividerTheme, Supplier<Button> buttonFactory) {
        this.dividerTheme = dividerTheme;
        ownPooled(empty);
        empty.setThemeParent(this);
        getSignalScope().own(this::releaseBindings);
        this.buttonFactory = Objects.requireNonNull(buttonFactory);
        sections.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        empty.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        updateChildren(java.util.List.of(sections));
        rebuild();
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Menu");
        db.registerVariation("MenuEmpty", "Label");
        db.register(EMPTY_FONT_COLOR);
        PADDING.register(db);
    }

    private static void validateKeys(List<MenuItem> items, Set<String> keys) {
        for (MenuItem item : items) {
            if (!keys.add(item.key())) throw new IllegalArgumentException("Duplicate menu key: " + item.key());
            validateKeys(item.children(), keys);
        }
    }

    private static Box verticalBox() {
        Box box = new Box().setGap(0);
        box.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        return box;
    }

    @Override
    protected void validateChildren(java.util.List<Control> proposed) {
        if (!isUpdatingChildren()) throw new IllegalStateException("Menu has a fixed skeleton; use setItems");
    }

    @Override
    protected void childrenChanged() {
        for (var entry : List.copyOf(buttons.entrySet())) {
            if (entry.getValue().isAlive() && isDescendant(entry.getValue())) continue;
            buttons.remove(entry.getKey());
            SignalScope binding = bindings.remove(entry.getKey());
            if (binding != null) binding.close();
        }
    }

    private boolean isDescendant(Control node) {
        for (Control parent = node.getParent(); parent != null; parent = parent.getParent())
            if (parent == this) return true;
        return false;
    }

    public List<MenuItem> getItems() {
        return items;
    }

    public Menu setItems(List<MenuItem> items) {
        List<MenuItem> next = List.copyOf(items);
        validateKeys(next, new HashSet<>());
        rememberAnchor();
        this.items = next;
        rebuild();
        return this;
    }

    public Signal<MenuItem> itemActivated() {
        return itemActivated.signal();
    }

    public Signal<String> selectionChanged() {
        return selectionChanged.signal();
    }

    public String getSelectedKey() {
        return selectedKey;
    }

    /**
     * Update selection without triggering an action. Missing keys clear the selection.
     */
    public Menu setSelectedKey(String key) {
        String previous = selectedKey;
        setSelectedNoSignal(key);
        if (!Objects.equals(previous, selectedKey)) selectionChanged.emit(selectedKey);
        return this;
    }

    public Menu setSelectedNoSignal(String key) {
        selectedKey = buttons.containsKey(key) ? key : null;
        buttons.forEach((id, button) -> button.setButtonPressedNoSignal(Objects.equals(id, selectedKey)));
        return this;
    }

    public Button getButton(String key) {
        return buttons.get(key);
    }

    public Menu setEmpty(Component message) {
        empty.setText(message);
        return this;
    }

    private void rebuild() {
        Map<String, Button> previous = new HashMap<>(buttons);
        for (Button button : previous.values()) if (button.getParent() != null) button.getParent().removeChild(button);
        for (SignalScope binding : bindings.values()) binding.close();
        bindings.clear();
        if (empty.getParent() != null) empty.getParent().removeChild(empty);
        for (Control child : sections.getChildren()) {
            if (child.getParent() != null) child.getParent().removeChild(child);
            child.queueFree();
        }
        buttons.clear();
        itemInsets.clear();
        append(sections, items, previous, false);
        if (buttons.isEmpty() && items.isEmpty()) {
            if (empty.getParent() != null) empty.getParent().removeChild(empty);
            sections.addChild(empty);
        }
        for (Map.Entry<String, Button> entry : previous.entrySet())
            if (!buttons.containsKey(entry.getKey())) entry.getValue().queueFree();
        setSelectedNoSignal(selectedKey);
        requestLayout();
    }

    private void append(Box parent, List<MenuItem> items, Map<String, Button> previous, boolean disabled) {
        Box run = null;
        for (MenuItem item : items) {
            if (item.type() == MenuItem.Type.GROUP) {
                run = null;
                Box group = verticalBox();
                Label title = new Label(item.label()).setThemeTypeVariation("LabelSecondary")
                        .setOverflow(Label.Overflow.ELLIPSIS).setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
                title.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
                Padding heading = new Padding(PADDING.getLayoutInsets(this)).setChild(title);
                itemInsets.add(heading);
                heading.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
                group.addChild(heading);
                group.addChild(new Divider(dividerTheme));
                Box children = verticalBox();
                append(children, item.children(), previous, disabled || item.disabled());
                group.addChild(children);
                parent.addChild(group);
            } else if (item.type() == MenuItem.Type.EMPTY) {
                if (run == null) {
                    run = verticalBox();
                    parent.addChild(run);
                }
                Label label = new Label(item.label()).setThemeTypeVariation("MenuEmpty").setWrap(Label.Wrap.WORD)
                        .setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
                label.setThemeParent(this);
                label.setMinimum(0, Button.HEIGHT);
                Padding inset = new Padding(PADDING.getLayoutInsets(this)).setChild(label);
                itemInsets.add(inset);
                inset.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
                run.addChild(inset);
            } else {
                if (run == null) {
                    run = verticalBox();
                    parent.addChild(run);
                }
                Button button = previous.get(item.key());
                if (button == null) button = Objects.requireNonNull(buttonFactory.get());
                button.setLabel(item.label()).setIcon(item.icon()).setThemeTypeVariation("MenuButton")
                        .setAlignment(Button.TextAlignment.LEFT).setToggleButton(true).setDisabled(disabled || item.disabled())
                ;
                SignalScope binding = new SignalScope();
                bindings.put(item.key(), binding);
                button.pressed().connect(binding, () -> {
                    setSelectedKey(item.key());
                    itemActivated.emit(item);
                });
                button.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
                buttons.put(item.key(), button);
                run.addChild(button);
            }
        }
    }

    private void releaseBindings() {
        for (SignalScope binding : bindings.values()) binding.close();
        bindings.clear();

    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        syncPadding();
        Size desired = sections.measureSize(font, available);
        return new Size(Math.max(getMinimumWidth(), Math.min(available.width(), desired.width())),
                Math.max(getMinimumHeight(), desired.height()));
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).width();
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).height();
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            syncPadding();
            if (bounds.width() != getBounds().width()) rememberAnchor();
            super.layout(bounds, font);
            int height = Math.max(bounds.height(), sections.measureSize(font, new Size(bounds.width(), bounds.height())).height());
            extent = new Size(bounds.width(), height);
            fitChild(sections, new Rect(0, 0, extent.width(), extent.height()), font);
            if (anchor != null && !buttons.isEmpty()) {
                List<Button> ordered = List.copyOf(buttons.values());
                Button target = buttons.getOrDefault(anchor.key(), ordered.get(Math.min(anchor.index(), ordered.size() - 1)));
                restoredScrollY = Math.max(0, target.getAbsoluteBounds().y() - getAbsoluteBounds().y() + anchor.offset());
            }
            // Scroll may measure several widths while deciding which bars to show.
            // Keep the original key through those provisional layouts until it consumes the anchor.
        }
    }

    private void rememberAnchor() {
        if (anchor != null) return;
        int index = 0;
        for (var entry : buttons.entrySet()) {
            Rect area = entry.getValue().getAbsoluteBounds();
            int top = area.y() - getAbsoluteBounds().y();
            if (top + area.height() > visibleRegion.y()) {
                anchor = new Anchor(entry.getKey(), index, visibleRegion.y() - top);
                return;
            }
            index++;
        }
    }

    private void syncPadding() {
        var insets = PADDING.getLayoutInsets(this);
        for (Padding node : List.copyOf(itemInsets)) if (node.isAlive()) node.setInsets(insets);
    }

    @Override
    public Size getContentExtent() {
        return extent;
    }

    @Override
    public void setVisibleRegion(Rect region) {
        visibleRegion = region;
    }

    @Override
    public int consumeRestoredScrollY() {
        int y = restoredScrollY;
        restoredScrollY = -1;
        anchor = null;
        return y;
    }

    @Override
    public void clearScrollAnchor() {
        anchor = null;
        restoredScrollY = -1;
    }

    private record Anchor(String key, int index, int offset) {
    }
}
