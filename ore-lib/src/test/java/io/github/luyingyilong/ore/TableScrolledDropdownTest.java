package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Mouse focus must not relocate a table before its cell receives the same pointer event. */
public final class TableScrolledDropdownTest {
    public static void main(String[] args) {
        retainedDropdown();
        drawerDropdownAcrossLayout();
        System.out.println("ORE_TABLE_SCROLLED_DROPDOWN_PASS");
    }

    private static Dropdown<String> dropdown() {
        Dropdown<String> dropdown = new Dropdown<>("a", 4);
        dropdown.setOptions(List.of(new Dropdown.Option<>("a", Component.literal("A")),
                new Dropdown.Option<>("b", Component.literal("B"))));
        return dropdown;
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
        io.github.luyingyilong.ore.event.WindowInputs.press(window,90, 25, null);
        check(scroll.scrollX() == 150 && window.focusManager().focused() == dropdown, "Retained dropdown preserves scroll");
        io.github.luyingyilong.ore.event.WindowInputs.release(window,90, 25);
        check(dropdown.isOpen(), "Retained Dropdown opens");
        io.github.luyingyilong.ore.event.WindowInputs.press(window,90, 67, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,90, 67);
        check(dropdown.selected().equals("b") && !dropdown.isOpen(), "Popup options use the scrolled anchor");

        window.focusManager().requestFocus(null, null);
        scroll.setScroll(0, 0);
        window.focusManager().requestFocus(dropdown, null);
        check(scroll.scrollX() > 0, "Keyboard/programmatic focus must still reveal an offscreen dropdown");
    }

    private static void drawerDropdownAcrossLayout() {
        Dropdown<String> dropdown = dropdown();
        Container wrapper = new Container() {
            { addChild(dropdown); }
            @Override public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
                super.layout(bounds, font);
                fitChild(dropdown, new Rect(0, 0, bounds.width(), bounds.height()), font);
            }
        };
        Table<Integer> table = new Table<Integer>().rowKey(value -> value)
                .column(new Table.Column<>("menu", Component.empty(), 70, 70, 1,
                        width -> 23, new Table.ControlCell<>(() -> wrapper, (control, row) -> { })));
        table.minimum(300, 80);
        table.rows(List.of(0));
        var font = new net.minecraft.client.gui.Font(id -> null, false) {
            @Override public int width(net.minecraft.network.chat.FormattedText text) { return text.getString().length() * 6; }
        };
        long[] time = {0};
        Drawer drawer = new Drawer(Component.literal("Members"), () -> time[0], cue -> { }).content(table);
        Window window = Window.embedded().content(new Control()).overlay(new Overlay().add(drawer));
        drawer.open();
        time[0] = 1000;
        window.layoutViewport(400, 200, font);
        drawer.bodyScroll().setScroll(100, 0);
        var area = io.github.luyingyilong.ore.event.EventGeometry.displayed(dropdown);
        int x = Math.max(area.x(), drawer.panelBounds().x()) + 10, y = area.y() + 8;
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window, x, y, null), "Drawer dropdown press accepted");
        for (int i = 0; i < 3; i++) window.layoutViewport(400, 200, font);
        io.github.luyingyilong.ore.event.WindowInputs.release(window, x, y);
        check(dropdown.isOpen(), "Drawer Dropdown opens across intervening layout frames");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
