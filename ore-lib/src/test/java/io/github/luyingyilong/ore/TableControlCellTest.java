package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Window;
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
            buy = Signals.configure(new Button(cue -> events.add(cue.name())).setSize(Button.Size.SMALL), connected -> connected.pressed().connect(connected, () -> events.add(row.id() + ":buy")));
            favorite = Signals.configure(new Button(cue -> events.add(cue.name())).setSize(Button.Size.SMALL).setToggleButton(true), connected -> connected.pressed().connect(connected, () -> events.add(row.id() + ":favorite:" + favoriteState())));
            addChild(buy);
            addChild(favorite);
        }

        private boolean favoriteState() { return favorite.getButtonPressed(); }
        private void bind(Item row) {
            this.row = row;
            favorite.setButtonPressedNoSignal(row.favorite());
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
        Table<Item> table = new Table<Item>().setRowKey(Item::id)
                .column(new Table.Column<>("name", Component.literal("Name"), 40, 40, 0,
                        width -> 20, (context, row, bounds) -> { }))
                .column(new Table.Column<>("actions", Component.literal("Actions"), 110, 160, 1,
                        width -> 20, new Table.ControlCell<>(() -> {
                            created[0]++;
                            return new Actions(events);
                        }, Actions::bind)));
        List<Item> rows = IntStream.range(0, 500).mapToObj(i -> new Item(i, false)).toList();
        table.setRows(rows);
        Overlay overlay = new Overlay();
        Window window = new Window(true).setContent(table).setOverlay(overlay);
        window.layoutViewport(200, 100, null);
        check(!table.acceptsFocus() && created[0] < 10);
        Actions first = actions(table, 0);
        Rect buy = first.buy.getAbsoluteBounds();
        Rect favorite = first.favorite.getAbsoluteBounds();
        int x = buy.x() + 2;
        int y = buy.y() + 2;
        check(buy.height() == favorite.height() && buy.height() == 18);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null) && events.isEmpty());
        check(window.getFocusManager().getFocused() == first.buy);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,favorite.x() + 2, favorite.y() + 2) && events.isEmpty());
        io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y);
        check(events.equals(List.of("CLICK", "0:buy")));
        check(!io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y));
        events.clear();
        io.github.luyingyilong.ore.event.WindowInputs.press(window,favorite.x() + 2, favorite.y() + 2, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,favorite.x() + 2, favorite.y() + 2);
        check(first.favorite.getButtonPressed() && events.equals(List.of("CLICK", "0:favorite:true")));

        events.clear();
        first.buy.setDisabled(true);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y);
        check(events.isEmpty() && !first.buy.acceptsFocus());
        first.buy.setDisabled(false);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null);
        table.setRows(List.copyOf(rows));
        window.layoutViewport(200, 100, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y);
        check(events.equals(List.of("CLICK", "0:buy"))); // Equal snapshots preserve in-flight clicks.

        events.clear();
        io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null);
        table.setScroll(23);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y);
        check(events.isEmpty());
        table.setScroll(0);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null);
        Button focusedBeforeRefresh = actions(table, 0).buy;
        table.setRows(IntStream.range(0, 500).mapToObj(i -> new Item(i, true)).toList());
        window.layoutViewport(200, 100, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y);
        check(events.isEmpty() && actions(table, 0).favorite.getButtonPressed());
        check(window.getFocusManager().getFocused() == focusedBeforeRefresh);

        // Inserting a row changes indices, but stable keys preserve the original button instances.
        List<Item> inserted = new ArrayList<>();
        inserted.add(new Item(-1, false));
        inserted.addAll(IntStream.range(0, 500).mapToObj(i -> new Item(i, true)).toList());
        table.setRows(inserted);
        window.layoutViewport(200, 100, null);
        check(actions(table, 0).buy == focusedBeforeRefresh);
        table.setRows(inserted.subList(1, inserted.size()));
        window.layoutViewport(200, 100, null);
        check(actions(table, 0).buy == focusedBeforeRefresh);

        // Scroll through the entire data source: callbacks must use the rebound row, not the old row.
        int initialCreated = created[0];
        for (int scroll = 0; scroll < table.getContentHeight(); scroll += 70) table.setScroll(scroll);
        check(created[0] < 20 && created[0] <= initialCreated + 8);
        check(window.getFocusManager().getFocused() == null); // Recycling cannot focus an unrelated record.
        table.setScroll(1000);
        Table.Row<Item> visible = table.getVisibleRows().stream()
                .filter(row -> row.bounds().y() >= table.getScroll()).findFirst().orElseThrow();
        Actions current = actions(table, visible.index());
        Rect target = current.buy.getAbsoluteBounds();
        io.github.luyingyilong.ore.event.WindowInputs.press(window,target.x() + 2, target.y() + 2, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,target.x() + 2, target.y() + 2);
        check(events.equals(List.of("CLICK", visible.item().id() + ":buy")));
        events.clear();

        // Keyboard focuses real Button nodes, reveals buffered rows, and fires only after release.
        table.setScroll(0);
        first = actions(table, 0);
        window.getFocusManager().requestFocus(first.buy, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null);
        check(window.getFocusManager().getFocused() == first.favorite);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null);
        check(events.isEmpty());
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_SPACE, 0, null);
        check(events.equals(List.of("CLICK", "0:favorite:false")));
        for (int i = 0; i < 15; i++) io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_DOWN, 0, null);
        check(table.getScroll() > 0 && window.getFocusManager().getFocused() instanceof Button);
        Rect focus = window.getFocusManager().getFocused().getAbsoluteBounds();
        check(focus.y() >= table.getBodyBounds().y() && focus.bottom() <= table.getBodyBounds().bottom());

        // Header and buffered rows never receive body clicks, including compact layouts.
        events.clear();
        table.setScroll(10);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 10, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,45, 10);
        check(events.isEmpty());
        for (int width : new int[]{127, 128, 129}) {
            table.scrollToTop();
            window.layoutViewport(width, 150, null);
            check(table.getCompact());
            target = actions(table, 0).buy.getAbsoluteBounds();
            io.github.luyingyilong.ore.event.WindowInputs.press(window,target.x() + 1, target.y() + 1, null);
            io.github.luyingyilong.ore.event.WindowInputs.release(window,target.x() + 1, target.y() + 1);
            check(events.equals(List.of("CLICK", "0:buy")));
            events.clear();
        }

        target = actions(table, 0).buy.getAbsoluteBounds();
        io.github.luyingyilong.ore.event.WindowInputs.press(window,target.x() + 1, target.y() + 1, null);
        Control popup = new Control() { @Override public boolean getFocusScope() { return true; } };
        overlay.add(popup);
        window.layoutViewport(129, 150, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,target.x() + 1, target.y() + 1);
        check(events.isEmpty());
    }

    private static Actions actions(Table<Item> table, int index) {
        return table.getChildren().stream().flatMap(row -> row.getChildren().stream())
                .filter(Actions.class::isInstance).map(Actions.class::cast)
                .filter(actions -> actions.row.id() == index).findFirst().orElseThrow();
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Retained table Button regression");
    }
}
