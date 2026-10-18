package io.github.luyingyilong.ore;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.FocusManager;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Release targets retain row identity and action identity across virtual row reuse. */
public final class TableInteractionTest {
    private record Item(int id, int version) { }

    private TableInteractionTest() { }

    public static void main(String[] args) {
        List<String> actions = new ArrayList<>();
        Table<Item> table = new Table<Item>().rowKey(Item::id)
                .column(new Table.Column<>("name", Component.literal("Name"), 40, 40, 0, width -> 20,
                        (context, row, bounds) -> { }))
                .column(new Table.Column<>("action", Component.literal("Action"), 80, 120, 1, width -> 20,
                        new Table.Cell<>() {
                    @Override public void draw(ModularGuiContext context, Item item, Rect bounds) { }
                    @Override public List<Rect> focusTargets(Item item, Rect bounds) {
                        int width = (bounds.width() - 6) / 2;
                        return List.of(new Rect(bounds.x() + 2, bounds.y() + 2, width, 18),
                                new Rect(bounds.x() + width + 4, bounds.y() + 2, width, 18));
                    }
                    @Override public boolean click(Item item, Rect bounds, int x, int y) {
                        List<Rect> targets = focusTargets(item, bounds);
                        for (int index = 0; index < targets.size(); index++) if (targets.get(index).contains(x, y)) {
                            actions.add(item.id() + ":" + index);
                            return true;
                        }
                        return false;
                    }
                }));
        List<Item> rows = List.of(new Item(0, 1), new Item(1, 1), new Item(2, 1), new Item(3, 1), new Item(4, 1));
        table.rows(rows);
        Overlay overlay = new Overlay();
        Window window = new Window(true).content(table).overlay(overlay);
        window.layoutViewport(200, 100, null);

        check(window.pointerPressed(45, 25, null) && actions.isEmpty());
        check(window.pointerReleased(125, 25) && actions.isEmpty()); // Other button in the same cell.
        window.pointerPressed(45, 25, null);
        window.pointerReleased(45, 48);
        check(actions.isEmpty()); // Same action on another row.
        window.pointerPressed(45, 25, null);
        window.pointerReleased(5, 5);
        check(actions.isEmpty());
        window.pointerPressed(45, 25, null);
        check(window.pointerReleased(45, 25) && actions.equals(List.of("0:0")));
        check(!window.pointerReleased(45, 25));
        window.pointerPressed(125, 25, null);
        table.rows(List.copyOf(rows));
        window.layoutViewport(200, 100, null);
        window.pointerReleased(125, 25);
        check(actions.equals(List.of("0:0", "0:1")));

        int before = actions.size();
        window.pointerPressed(45, 25, null);
        table.setScroll(23);
        window.pointerReleased(45, 25);
        check(actions.size() == before);
        table.setScroll(0);
        window.pointerPressed(45, 25, null);
        table.rows(List.of(new Item(0, 2), rows.get(1), rows.get(2), rows.get(3), rows.get(4)));
        window.layoutViewport(200, 100, null);
        window.pointerReleased(45, 25);
        check(actions.size() == before); // Updated data under the same stable key is stale, too.
        window.pointerPressed(45, 25, null);
        table.rows(List.of(rows.get(1), rows.get(2), rows.get(3)));
        window.layoutViewport(200, 100, null);
        window.pointerReleased(45, 25);
        check(actions.size() == before);
        window.pointerPressed(45, 25, null);
        table.visible(false);
        table.visible(true);
        window.pointerReleased(45, 25);
        check(actions.size() == before);

        window.focusManager().requestFocus(table, null);
        table.onFocusMove(FocusManager.Direction.LEFT);
        table.onFocusMove(FocusManager.Direction.RIGHT);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions.size() == before);
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions.size() == ++before);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        window.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, null);
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions.size() == before);

        window.pointerPressed(45, 25, null);
        Control popup = new Control() { @Override public boolean focusScope() { return true; } };
        overlay.add(popup);
        window.layoutViewport(200, 100, null);
        window.pointerReleased(45, 25);
        check(actions.size() == before);
        popup.visible(false);

        for (int width : new int[]{127, 128, 129}) {
            window.layoutViewport(width - 32, 150, null);
            check(table.compact());
            window.focusManager().requestFocus(table, null);
            table.onFocusMove(FocusManager.Direction.LEFT);
            table.onFocusMove(FocusManager.Direction.RIGHT);
            Rect target = table.focusBounds();
            check(window.pointerPressed(target.x() + 1, target.y() + 1, null));
            check(actions.size() == before);
            check(window.pointerReleased(target.x() + 1, target.y() + 1));
            check(actions.size() == ++before);
        }
        nestedDropdownTrigger();
    }

    private static void nestedDropdownTrigger() {
        Dropdown<String> dropdown = new Dropdown<>("a", 2);
        dropdown.setOptions(List.of(new Dropdown.Option<>("a", Component.literal("A"))));
        Table<Integer> table = new Table<Integer>().rowKey(row -> row)
                .column(new Table.Column<>("menu", Component.literal("Menu"), 40, 80, 1, width -> 20,
                        new Table.Cell<>() {
                    @Override public void draw(ModularGuiContext context, Integer row, Rect area) { }
                    @Override public List<Rect> focusTargets(Integer row, Rect area) { return List.of(area); }
                    @Override public boolean click(Integer row, Rect area, int x, int y) {
                        dropdown.click(area.x(), area.y(), area.width(), area.height(), x, y);
                        return dropdown.releaseAt(x, y);
                    }
                }));
        table.rows(List.of(1));
        Window window = new Window(true).content(table);
        window.layoutViewport(100, 80, null);
        window.pointerPressed(10, 25, null);
        check(!dropdown.isOpen());
        window.pointerReleased(10, 25);
        check(dropdown.isOpen());
        dropdown.close();
        window.focusManager().requestFocus(table, null);
        window.keyPressed(GLFW.GLFW_KEY_SPACE, 0, null);
        check(!dropdown.isOpen());
        window.keyReleased(GLFW.GLFW_KEY_SPACE, 0, null);
        check(dropdown.isOpen());
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Table action release/cancellation regression");
    }
}
