package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Mouse focus must not relocate a table before its cell receives the same pointer event. */
public final class TableScrolledDropdownTest {
    public static void main(String[] args) {
        detachedMemberMenu();
        retainedDropdown();
        System.out.println("ORE_TABLE_SCROLLED_DROPDOWN_PASS");
    }

    private static Dropdown<String> dropdown() {
        Dropdown<String> dropdown = new Dropdown<>("a", 4);
        dropdown.setOptions(List.of(new Dropdown.Option<>("a", Component.literal("A")),
                new Dropdown.Option<>("b", Component.literal("B"))));
        return dropdown;
    }

    private static void detachedMemberMenu() {
        int[] actions = {0};
        Dropdown<String> menu = dropdown();
        Table<Integer> table = new Table<Integer>().rowKey(value -> value)
                .column(new Table.Column<>("name", Component.empty(), 230, 230, 0,
                        width -> 23, (context, row, bounds) -> { }))
                .column(new Table.Column<>("actions", Component.empty(), 70, 70, 0,
                        width -> 23, new Table.Cell<>() {
                    @Override public void draw(brachy.modularui.screen.viewport.ModularGuiContext context,
                                               Integer row, Rect bounds) { }
                    @Override public List<Rect> focusTargets(Integer row, Rect bounds) { return List.of(bounds); }
                    @Override public boolean click(Integer row, Rect bounds, int x, int y) {
                        if (!menu.click(bounds.x(), bounds.y(), bounds.width(), bounds.height(), x, y)) return false;
                        actions[0]++;
                        return menu.releaseAt(x, y);
                    }
                }));
        table.minimum(300, 80);
        table.rows(List.of(0));
        Scroll scroll = new Scroll().vertical(Scroll.BarMode.DISABLED).content(table);
        Window window = new Window(true).content(scroll);
        window.layoutViewport(120, 80, null);
        scroll.setScroll(150, 0);
        check(scroll.scrollX() == 150, "Test needs horizontal overflow");
        check(window.pointerPressed(90, 25, null), "Visible action press");
        check(scroll.scrollX() == 150, "Mouse focus unexpectedly changed horizontal scroll");
        window.pointerReleased(90, 25);
        check(actions[0] == 1, "Visible table action must receive release");
        check(menu.isOpen(), "Detached organization-style Dropdown must open");
        menu.close();
        window.focusManager().requestFocus(null, null);
        window.pointerPressed(90, 25, null);
        window.pointerReleased(50, 25);
        check(!menu.isOpen() && actions[0] == 1, "Dragging out still cancels");

        // Changing focus scope on the first pointer press must preserve the same coordinates too.
        Control scope = new Control() { @Override public boolean focusScope() { return true; } };
        scroll.removeChild(table);
        Scroll scopedScroll = new Scroll().vertical(Scroll.BarMode.DISABLED).content(table);
        scopedScroll.requestedSize(120, 80);
        scope.addChild(scopedScroll);
        Overlay overlay = new Overlay().add(scope);
        window.overlay(overlay);
        window.layoutViewport(120, 80, null);
        scopedScroll.setScroll(150, 0);
        window.pointerPressed(90, 25, null);
        check(scopedScroll.scrollX() == 150, "First mouse focus in an overlay must not reveal the whole table");
        window.pointerReleased(90, 25);
        check(menu.isOpen(), "Menu opens on first press in a new scope");
    }

    private static void retainedDropdown() {
        Dropdown<String> dropdown = dropdown();
        Table<Integer> table = new Table<Integer>().rowKey(value -> value)
                .column(new Table.Column<>("name", Component.empty(), 230, 230, 0,
                        width -> 23, (context, row, bounds) -> { }))
                .column(new Table.Column<>("menu", Component.empty(), 70, 70, 0,
                        width -> 23, new Table.ControlCell<>(() -> dropdown, (control, row) -> { })));
        table.minimum(300, 80);
        table.rows(List.of(0));
        Scroll scroll = new Scroll().vertical(Scroll.BarMode.DISABLED).content(table);
        Window window = Window.embedded().content(scroll);
        window.layoutViewport(120, 80, null);
        scroll.setScroll(150, 0);
        window.pointerPressed(90, 25, null);
        check(scroll.scrollX() == 150 && window.focusManager().focused() == dropdown, "Retained dropdown preserves scroll");
        window.pointerReleased(90, 25);
        check(dropdown.isOpen(), "Retained Dropdown opens");
        window.pointerPressed(90, 67, null);
        window.pointerReleased(90, 67);
        check(dropdown.selected().equals("b") && !dropdown.isOpen(), "Popup options use the scrolled anchor");

        window.focusManager().requestFocus(null, null);
        scroll.setScroll(0, 0);
        window.focusManager().requestFocus(dropdown, null);
        check(scroll.scrollX() > 0, "Keyboard/programmatic focus must still reveal an offscreen dropdown");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
