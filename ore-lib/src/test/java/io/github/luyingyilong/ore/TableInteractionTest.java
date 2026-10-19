package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.DrawContext;
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
        Table<Item> table = new Table<Item>().setRowKey(Item::id)
                .addColumn(new Table.Column<>("name", Component.literal("Name"), 40, 40, 0, width -> 20,
                        (context, row, bounds) -> { }))
                .addColumn(new Table.Column<>("action", Component.literal("Action"), 80, 120, 1, width -> 20,
                        new Table.Cell<>() {
                    @Override public void draw(DrawContext context, Item item, Rect bounds) { }
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
        table.setRows(rows);
        Overlay overlay = new Overlay();
        Window window = new Window(true).setContent(table).setOverlay(overlay);
        window.layoutViewport(200, 100, null);

        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 25, null) && actions.isEmpty());
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,125, 25) && actions.isEmpty()); // Other button in the same cell.
        io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 25, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,45, 48);
        check(actions.isEmpty()); // Same action on another row.
        io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 25, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,5, 5);
        check(actions.isEmpty());
        io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 25, null);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,45, 25) && actions.equals(List.of("0:0")));
        check(!io.github.luyingyilong.ore.event.WindowInputs.release(window,45, 25));
        io.github.luyingyilong.ore.event.WindowInputs.press(window,125, 25, null);
        table.setRows(List.copyOf(rows));
        window.layoutViewport(200, 100, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,125, 25);
        check(actions.equals(List.of("0:0", "0:1")));

        int before = actions.size();
        io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 25, null);
        table.setScroll(23);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,45, 25);
        check(actions.size() == before);
        table.setScroll(0);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 25, null);
        table.setRows(List.of(new Item(0, 2), rows.get(1), rows.get(2), rows.get(3), rows.get(4)));
        window.layoutViewport(200, 100, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,45, 25);
        check(actions.size() == before); // Updated data under the same stable key is stale, too.
        io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 25, null);
        table.setRows(List.of(rows.get(1), rows.get(2), rows.get(3)));
        window.layoutViewport(200, 100, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,45, 25);
        check(actions.size() == before);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 25, null);
        table.setVisible(false);
        table.setVisible(true);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,45, 25);
        check(actions.size() == before);

        window.getFocusManager().requestFocus(table, null);
        table.onFocusMove(FocusManager.Direction.LEFT);
        table.onFocusMove(FocusManager.Direction.RIGHT);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions.size() == before);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions.size() == ++before);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions.size() == before);

        io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 25, null);
        Control popup = new Control() { @Override public boolean getFocusScope() { return true; } };
        overlay.addChild(popup);
        window.layoutViewport(200, 100, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,45, 25);
        check(actions.size() == before);
        popup.setVisible(false);

        for (int width : new int[]{127, 128, 129}) {
            window.layoutViewport(width - 32, 150, null);
            check(table.getCompact());
            window.getFocusManager().requestFocus(table, null);
            table.onFocusMove(FocusManager.Direction.LEFT);
            table.onFocusMove(FocusManager.Direction.RIGHT);
            Rect target = table.getFocusBounds();
            check(io.github.luyingyilong.ore.event.WindowInputs.press(window,target.x() + 1, target.y() + 1, null));
            check(actions.size() == before);
            check(io.github.luyingyilong.ore.event.WindowInputs.release(window,target.x() + 1, target.y() + 1));
            check(actions.size() == ++before);
        }
        nestedDropdownTrigger();
    }

    private static void nestedDropdownTrigger() {
        Dropdown<String> dropdown = new Dropdown<>("a", 2);
        dropdown.setOptions(List.of(new Dropdown.Option<>("a", Component.literal("A"))));
        Table<Integer> table = new Table<Integer>().setRowKey(row -> row)
                .addColumn(new Table.Column<>("menu", Component.literal("Menu"), 40, 80, 1, width -> 20,
                        new Table.ControlCell<>(() -> dropdown,(control,row)->{})));
        table.setRows(List.of(1));
        Window window = new Window(true).setContent(table);
        window.layoutViewport(100, 80, null);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,10, 25, null);
        check(!dropdown.isOpen());
        io.github.luyingyilong.ore.event.WindowInputs.release(window,10, 25);
        check(dropdown.isOpen());
        dropdown.close();
        window.getFocusManager().requestFocus(dropdown, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null);
        check(!dropdown.isOpen());
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_SPACE, 0, null);
        check(dropdown.isOpen());
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Table action release/cancellation regression");
    }
}
