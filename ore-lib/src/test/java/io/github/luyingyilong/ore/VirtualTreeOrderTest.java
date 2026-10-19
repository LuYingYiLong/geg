package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/** Recycling must maintain the real tree order, not just the renderer's private index. */
public final class VirtualTreeOrderTest {
    public static void main(String[] args) {
        grid(); table(); tree();
        System.out.println("ORE_VIRTUAL_TREE_ORDER_PASS");
    }

    private static void grid() {
        List<Integer> data = new ArrayList<>(IntStream.range(0, 400).boxed().toList());
        List<Control> created = new ArrayList<>();
        final class Card extends Control implements Grid.View<Integer> {
            Card() { created.add(this); }
            @Override public void bind(Integer item) { setDebugName(item.toString()); }
            @Override public void draw(DrawContext context, Rect area, boolean hovered) { }
        }
        Grid<Integer> grid = new Grid<Integer>().setAdapter(new Grid.Adapter<>() {
            @Override public int getSize() { return data.size(); }
            @Override public Integer getItem(int index) { return data.get(index); }
            @Override public Object getKey(Integer item) { return item; }
            @Override public int measureHeight(Integer item, int width, Font font) { return 20 + item % 4 * 3; }
            @Override public Grid.View<Integer> createView() { return new Card(); }
        });
        Scroll scroll = new Scroll().setContent(grid);
        scroll.layout(new Rect(0, 0, 310, 100), null);
        for (int y : new int[]{500, 250, 0, 2000, 1000, 0}) {
            scroll.setScroll(0, y);
            assertOrder(grid, data, false);
        }
        Collections.reverse(data); grid.invalidate(); scroll.layout(new Rect(0, 0, 310, 100), null);
        assertOrder(grid, data, false);
        check(created.size() < 100, "Grid recycles a bounded set of nodes");
        rejects(() -> grid.addChild(new Control()));
        scroll.queueFree(); LifecycleScheduler.flush();
        check(created.stream().allMatch(Control::isDisposed), "Active and detached pooled cards are freed");
    }

    private static void table() {
        List<Integer> data = new ArrayList<>(IntStream.range(0, 400).boxed().toList());
        Table<Integer> table = new Table<Integer>().setRowKey(value -> value)
                .addColumn(new Table.Column<>("value", Component.empty(), 20, 100, 1, width -> 23,
                        new Table.ControlCell<>(Control::new, (control, value) -> control.setDebugName(value.toString()))))
                .addColumn(new Table.Column<>("action", Component.empty(), 20, 20, 1, width -> 23,
                        new Table.ControlCell<>(Control::new, (control, value) -> control.setDebugName(value + ":action"))));
        table.setRows(data); table.layout(new Rect(0, 0, 120, 100), null);
        for (int y : new int[]{300, 100, 0, 600, 200, 0}) {
            table.setScroll(y); assertOrder(table, data, true);
        }
        Control retained = table.getChild(0);
        table.setRows(List.copyOf(data)); table.layout(new Rect(0, 0, 120, 100), null);
        check(table.getChild(0) == retained, "An equal-key refresh retains actual row identity");
        Control firstCell = retained.getChild(0); firstCell.queueFree(); LifecycleScheduler.flush();
        table.setRows(List.copyOf(data)); table.layout(new Rect(0, 0, 120, 100), null);
        check(table.getChild(0) == retained && !retained.getChild(0).getDebugName().contains(":"),
                "Recreated cells follow column order in the real tree");
        Collections.reverse(data); table.setRows(data); table.layout(new Rect(0, 0, 120, 100), null);
        assertOrder(table, data, true);
        check(table.getChildCount() < 20, "Table stays virtual");
        table.queueFree(); LifecycleScheduler.flush();
    }

    private static void tree() {
        Tree<Integer> tree = new Tree<>();
        tree.setItems(IntStream.range(0, 400).mapToObj(value ->
                new Tree.Item<>(Integer.toString(value), "", Component.literal("Row " + value), value)).toList());
        Scroll scroll = new Scroll().setContent(tree);
        scroll.layout(new Rect(0, 0, 160, 100), null);
        for (int y : new int[]{500, 200, 0, 1500, 100, 0}) {
            scroll.setScroll(0, y);
            int previous = -1;
            for (Control row : tree.getChildren()) {
                check(row.getBounds().y() > previous, "Tree ordinary children follow visible row order");
                previous = row.getBounds().y();
            }
        }
        check(tree.getChildCount() < 30, "Tree keeps only the viewport and buffer attached");
        scroll.queueFree(); LifecycleScheduler.flush();
    }

    private static void assertOrder(Control parent, List<Integer> data, boolean cell) {
        int previous = -1;
        for (int index = 0; index < parent.getChildCount(); index++) {
            Control child = parent.getChild(index);
            check(parent.getChildren().get(index) == child && child.getParent() == parent, "Queries share real structure");
            int position = data.indexOf(Integer.valueOf((cell ? child.getChild(0) : child).getDebugName()));
            check(position > previous, "Actual child order must follow data order after recycling");
            previous = position;
        }
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected a rejected external view");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
