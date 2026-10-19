package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.signal.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public final class VirtualSignalLifetimeTest {
    public static void main(String[] args) {
        try (SignalScope lifetime = new SignalScope()) {
            SignalSource0 refresh = new SignalSource0(lifetime, "refresh");
            List<Integer> observed = new ArrayList<>();
            List<Control> created = new ArrayList<>();
            List<Integer> data = IntStream.range(0, 300).boxed().toList();
            final class View extends Control implements Grid.View<Integer> {
                View() { created.add(this); }
                @Override public void bind(Integer item) {
                    refresh.signal().connect(getBindingScope(), () -> observed.add(item));
                }
                @Override public void draw(DrawContext context, Rect bounds, boolean hovered) { }
            }
            Grid<Integer> grid = new Grid<Integer>().setAdapter(new Grid.Adapter<>() {
                @Override public int getSize() { return data.size(); }
                @Override public Integer getItem(int index) { return data.get(index); }
                @Override public Object getKey(Integer item) { return item; }
                @Override public int measureHeight(Integer item, int width, Font font) { return 24; }
                @Override public Grid.View<Integer> createView() { return new View(); }
            });
            Scroll scroll = new Scroll().setContent(grid);
            scroll.layout(new Rect(0, 0, 310, 100), null);
            Control removedView = grid.getChildren().get(0);
            removedView.queueFree(); LifecycleScheduler.flush();
            grid.invalidate(); scroll.layout(new Rect(0, 0, 310, 100), null);
            check(grid.getChildren().stream().allMatch(Control::isAlive) && removedView.isDisposed(), "A freed grid view is replaced, never rebound");
            int active = grid.getActiveViews();
            for (int i = 0; i < 4; i++) { grid.invalidate(); scroll.layout(new Rect(0, 0, 310, 100), null); }
            refresh.emit();
            check(observed.size() == active && refresh.signal().connectionCount() == active, "Equal-key refresh does not accumulate subscriptions");
            observed.clear();
            scroll.setScroll(0, 10000);
            refresh.emit();
            check(observed.size() == grid.getActiveViews() && observed.stream().allMatch(id -> id > 200), "Pooled nodes do not retain old item callbacks");
            scroll.queueFree();
            io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
            check(created.stream().allMatch(Control::isDisposed) && refresh.signal().connectionCount() == 0, "Destroy active and pooled grid views");

            List<Control> cells = new ArrayList<>();
            Table<Integer> table = new Table<Integer>().setRowKey(value -> value).addColumn(new Table.Column<>("id", Component.empty(),
                    40, 100, 1, width -> 23, new Table.ControlCell<>(() -> {
                        Control control = new Control(); cells.add(control); return control;
                    }, (control, value) -> refresh.signal().connect(control.getBindingScope(), () -> observed.add(value)))));
            table.setRows(data); table.layout(new Rect(0, 0, 120, 100), null);
            Control removedRow = table.getChildren().get(0); removedRow.queueFree(); LifecycleScheduler.flush();
            table.setRows(data); table.layout(new Rect(0, 0, 120, 100), null);
            check(table.getChildren().stream().allMatch(Control::isAlive), "A freed table row is replaced, never rebound");
            for (int i = 0; i < 4; i++) { table.setRows(data); table.layout(new Rect(0, 0, 120, 100), null); }
            observed.clear(); refresh.emit();
            check(observed.size() == refresh.signal().connectionCount() && observed.stream().distinct().count() == observed.size(), "Table binders replace data-scoped connections");
            table.queueFree();
            io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
            check(cells.stream().allMatch(Control::isDisposed) && refresh.signal().connectionCount() == 0, "Table disposal drains cells and pool");
        }
        System.out.println("ORE_VIRTUAL_SIGNAL_LIFETIME_PASS");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
