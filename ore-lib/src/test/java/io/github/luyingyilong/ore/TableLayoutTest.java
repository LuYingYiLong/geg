package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.Signals;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.layout.Rect;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Headless table geometry, scrolling, stable-key, and action hit checks. */
public final class TableLayoutTest {
    private TableLayoutTest() {
    }

    public static void main(String[] args) {
        List<Integer> selected = new ArrayList<>();
        List<Integer> acted = new ArrayList<>();
        Table.Cell<Integer> text = (context, row, bounds) -> { };
        Table.Cell<Integer> action = new Table.Cell<>() {
            @Override public void draw(ModularGuiContext context, Integer row, Rect bounds) { }

            @Override public boolean click(Integer row, Rect bounds, int mouseX, int mouseY) {
                if (bounds.contains(mouseX, mouseY)) {
                    acted.add(row);
                    return true;
                }
                return false;
            }
        };
        Table<Integer> table = Signals.configure(new Table<Integer>()
                .setRowKey(row -> row), connected -> connected.rowActivated().connect(connected, selected::add))
                .column(new Table.Column<>("status", Component.literal("Status"),
                        110, 110, 1, width -> 20, text))
                .column(new Table.Column<>("price", Component.literal("Price"),
                        96, 96, 0, width -> 10, text))
                .column(new Table.Column<>("action", Component.literal("Action"),
                        104, 104, 0, width -> width >= 104 ? 19 : 39, action));
        List<Integer> rows = new ArrayList<>();
        for (int index = 0; index < 20; index++) rows.add(index);
        table.setRows(rows);
        table.layout(new Rect(0, 0, 320, 144), null);
        check(!table.getCompact() && table.getHeaderHeight() == 19);
        check(java.util.Arrays.equals(table.getColumnWidths(), new int[]{120, 96, 104}));
        check(table.getLayoutRows().get(0).bounds().equals(new Rect(0, 0, 320, 23)));
        check(table.getLayoutRows().get(1).bounds().y() == 23);
        check(table.getVisibleRows().size() == 6);
        check(table.getActiveViews() >= table.getVisibleRows().size()
                && table.getActiveViews() < rows.size());
        check(table.hitRow(5, 18) == null);
        check(table.hitRow(5, 19).item() == 0);
        check(table.click(5, 20) && selected.equals(List.of(0)));
        check(table.click(218, 20) && acted.equals(List.of(0)));
        table.setHeaderVisible(false);
        table.layout(new Rect(0, 0, 320, 144), null);
        check(table.getHeaderHeight() == 0 && table.hitRow(5, 0).item() == 0);
        table.setHeaderVisible(true);
        table.layout(new Rect(0, 0, 320, 144), null);

        table.setScroll(46);
        check(table.hitRow(5, 19).item() == 2);
        List<Integer> shifted = new ArrayList<>(rows);
        shifted.add(2, 20);
        table.setRows(shifted);
        table.layout(new Rect(0, 0, 320, 144), null);
        check(table.getScroll() == 69 && table.hitRow(5, 19).item() == 2);
        table.setRows(List.of(99));
        table.layout(new Rect(0, 0, 320, 144), null);
        check(table.getScroll() == 0 && table.getVisibleRows().size() == 1);
        table.setRows(List.of());
        table.layout(new Rect(0, 0, 320, 144), null);
        check(table.getVisibleRows().isEmpty() && table.getActiveViews() == 0
                && table.hitRow(5, 19) == null);

        for (int guiWidth : new int[]{127, 128, 129}) {
            int tableWidth = guiWidth - 32;
            table.setRows(rows);
            table.scrollToTop();
            table.layout(new Rect(0, 0, tableWidth, 144), null);
            check(table.getCompact() && table.getHeaderHeight() == 0);
            check(table.getLayoutRows().get(0).bounds().height() == 107);
            check(table.getLayoutRows().get(1).bounds().y() == 107);
            check(table.getVisibleRows().size() == 2);
            check(table.click(5, 70) && acted.get(acted.size() - 1) == 0);
            table.setScroll(Integer.MAX_VALUE);
            check(table.getScroll() == table.getContentHeight() - 144);
        }

        Table.Cell<Integer> variableHeight = new Table.Cell<>() {
            @Override public void draw(ModularGuiContext context, Integer row, Rect bounds) { }

            @Override public int measureHeight(net.minecraft.client.gui.Font font,
                                               Integer row, int width) {
                return row == 1 ? 36 : 0;
            }
        };
        Table<Integer> wrapped = new Table<Integer>().setRowKey(row -> row)
                .column(new Table.Column<>("name", Component.literal("Name"),
                        48, 80, 1, width -> 20, variableHeight));
        wrapped.setRows(List.of(0, 1, 2));
        wrapped.layout(new Rect(0, 0, 100, 100), null);
        check(wrapped.getLayoutRows().get(0).bounds().height() == 23);
        check(wrapped.getLayoutRows().get(1).bounds().height() == 36);
        check(wrapped.getLayoutRows().get(2).bounds().y() == 59);
        check(wrapped.hitRow(5, 43).item() == 1);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected Table layout result");
    }
}
