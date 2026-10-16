package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Overlay;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Retained cell buttons use normal Button events and remain safe across virtual-row recycling. */
public final class TableControlCellTest {
    private record Item(int id, boolean favorite) { }

    private static final class Actions extends Container {
        private Item row;
        private final Button buy;
        private final Button favorite;

        private Actions(List<String> events) {
            buy = new Button(cue -> events.add(cue.name())).size(Button.Size.SMALL)
                    .onPressed(() -> events.add(row.id() + ":buy"));
            favorite = new Button(cue -> events.add(cue.name())).size(Button.Size.SMALL).toggleButton(true)
                    .onPressed(() -> events.add(row.id() + ":favorite:" + favoriteState()));
            addChild(buy);
            addChild(favorite);
        }

        private boolean favoriteState() { return favorite.buttonPressed(); }
        private void bind(Item row) {
            this.row = row;
            favorite.buttonPressedNoSignal(row.favorite());
        }

        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            int width = (bounds.width() - 6) / 2;
            int top = (bounds.height() - 18) / 2;
            fitChild(buy, new Rect(2, top, width, 18), font);
            fitChild(favorite, new Rect(width + 4, top, width, 18), font);
        }
    }

    public static void main(String[] args) {
        List<String> events = new ArrayList<>();
        int[] created = {0};
        Table<Item> table = new Table<Item>().rowKey(Item::id)
                .column(new Table.Column<>("name", Component.literal("Name"), 40, 40, 0,
                        width -> 20, (context, row, bounds) -> { }))
                .column(new Table.Column<>("actions", Component.literal("Actions"), 110, 160, 1,
                        width -> 20, new Table.ControlCell<>(() -> {
                            created[0]++;
                            return new Actions(events);
                        }, Actions::bind)));
        List<Item> rows = IntStream.range(0, 500).mapToObj(i -> new Item(i, false)).toList();
        table.rows(rows);
        Overlay overlay = new Overlay();
        Window window = new Window(true).content(table).overlay(overlay);
        window.layoutViewport(200, 100, null);
        check(!table.acceptsFocus() && created[0] < 10);
        Actions first = actions(table, 0);
        Rect buy = first.buy.absoluteBounds();
        Rect favorite = first.favorite.absoluteBounds();
        int x = buy.x() + 2;
        int y = buy.y() + 2;
        check(buy.height() == favorite.height() && buy.height() == 18);
        check(window.pointerPressed(x, y, null) && events.isEmpty());
        check(window.focusManager().focused() == first.buy);
        check(window.pointerReleased(favorite.x() + 2, favorite.y() + 2) && events.isEmpty());
        window.pointerPressed(x, y, null);
        window.pointerReleased(x, y);
        check(events.equals(List.of("CLICK", "0:buy")));
        check(!window.pointerReleased(x, y));
        events.clear();
        window.pointerPressed(favorite.x() + 2, favorite.y() + 2, null);
        window.pointerReleased(favorite.x() + 2, favorite.y() + 2);
        check(first.favorite.buttonPressed() && events.equals(List.of("CLICK", "0:favorite:true")));

        events.clear();
        first.buy.disabled(true);
        window.pointerPressed(x, y, null);
        window.pointerReleased(x, y);
        check(events.isEmpty() && !first.buy.acceptsFocus());
        first.buy.disabled(false);
        window.pointerPressed(x, y, null);
        table.rows(List.copyOf(rows));
        window.layoutViewport(200, 100, null);
        window.pointerReleased(x, y);
        check(events.equals(List.of("CLICK", "0:buy"))); // Equal snapshots preserve in-flight clicks.

        events.clear();
        window.pointerPressed(x, y, null);
        table.setScroll(23);
        window.pointerReleased(x, y);
        check(events.isEmpty());
        table.setScroll(0);
        window.pointerPressed(x, y, null);
        Button focusedBeforeRefresh = actions(table, 0).buy;
        table.rows(IntStream.range(0, 500).mapToObj(i -> new Item(i, true)).toList());
        window.layoutViewport(200, 100, null);
        window.pointerReleased(x, y);
        check(events.isEmpty() && actions(table, 0).favorite.buttonPressed());
        check(window.focusManager().focused() == focusedBeforeRefresh);

        // Inserting a row changes indices, but stable keys preserve the original button instances.
        List<Item> inserted = new ArrayList<>();
        inserted.add(new Item(-1, false));
        inserted.addAll(IntStream.range(0, 500).mapToObj(i -> new Item(i, true)).toList());
        table.rows(inserted);
        window.layoutViewport(200, 100, null);
        check(actions(table, 0).buy == focusedBeforeRefresh);
        table.rows(inserted.subList(1, inserted.size()));
        window.layoutViewport(200, 100, null);
        check(actions(table, 0).buy == focusedBeforeRefresh);

        // Scroll through the entire data source: callbacks must use the rebound row, not the old row.
        int initialCreated = created[0];
        for (int scroll = 0; scroll < table.contentHeight(); scroll += 70) table.setScroll(scroll);
        check(created[0] < 20 && created[0] <= initialCreated + 8);
        check(window.focusManager().focused() == null); // Recycling cannot focus an unrelated record.
        table.setScroll(1000);
        Table.Row<Item> visible = table.visibleRows().stream()
                .filter(row -> row.bounds().y() >= table.scroll()).findFirst().orElseThrow();
        Actions current = actions(table, visible.index());
        Rect target = current.buy.absoluteBounds();
        window.pointerPressed(target.x() + 2, target.y() + 2, null);
        window.pointerReleased(target.x() + 2, target.y() + 2);
        check(events.equals(List.of("CLICK", visible.item().id() + ":buy")));
        events.clear();

        // Keyboard focuses real Button nodes, reveals buffered rows, and fires only after release.
        table.setScroll(0);
        first = actions(table, 0);
        window.focusManager().requestFocus(first.buy, null);
        window.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, null);
        check(window.focusManager().focused() == first.favorite);
        window.keyPressed(GLFW.GLFW_KEY_SPACE, 0, null);
        window.keyPressed(GLFW.GLFW_KEY_SPACE, 0, null);
        check(events.isEmpty());
        window.keyReleased(GLFW.GLFW_KEY_SPACE, 0, null);
        check(events.equals(List.of("CLICK", "0:favorite:false")));
        for (int i = 0; i < 15; i++) window.keyPressed(GLFW.GLFW_KEY_DOWN, 0, null);
        check(table.scroll() > 0 && window.focusManager().focused() instanceof Button);
        Rect focus = window.focusManager().focused().absoluteBounds();
        check(focus.y() >= table.bodyBounds().y() && focus.bottom() <= table.bodyBounds().bottom());

        // Header and buffered rows never receive body clicks, including compact layouts.
        events.clear();
        table.setScroll(10);
        window.pointerPressed(45, 10, null);
        window.pointerReleased(45, 10);
        check(events.isEmpty());
        for (int width : new int[]{127, 128, 129}) {
            table.scrollToTop();
            window.layoutViewport(width, 150, null);
            check(table.compact());
            target = actions(table, 0).buy.absoluteBounds();
            window.pointerPressed(target.x() + 1, target.y() + 1, null);
            window.pointerReleased(target.x() + 1, target.y() + 1);
            check(events.equals(List.of("CLICK", "0:buy")));
            events.clear();
        }

        target = actions(table, 0).buy.absoluteBounds();
        window.pointerPressed(target.x() + 1, target.y() + 1, null);
        Control popup = new Control() { @Override public boolean focusScope() { return true; } };
        overlay.add(popup);
        window.layoutViewport(129, 150, null);
        window.pointerReleased(target.x() + 1, target.y() + 1);
        check(events.isEmpty());
    }

    private static Actions actions(Table<Item> table, int index) {
        return table.children().stream().flatMap(row -> row.children().stream())
                .filter(Actions.class::isInstance).map(Actions.class::cast)
                .filter(actions -> actions.row.id() == index).findFirst().orElseThrow();
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Retained table Button regression");
    }
}
