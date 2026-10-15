package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Standalone layout checks; run with java -ea and the client dependency classpath. */
public final class LayoutRegressionTest {
    private LayoutRegressionTest() {
    }

    public static void main(String[] args) {
        Control unset = new Control();
        check(unset.minimumSize().equals(new Size(0, 0)));
        check(unset.size().equals(new Size(0, 0)));
        for (int width : new int[]{127, 128, 129, 320, 427}) {
            Control title = new Control().preferred(60, 20).minimum(0, 20)
                    .policy(Control.SizePolicy.EXPAND, Control.SizePolicy.CONTENT);
            Control balance = new Control().preferred(90, 20).minimum(64, 20);
            Control search = new Control().preferred(138, 18).minimum(72, 18);
            Control filter = new Control().preferred(82, 20).minimum(74, 20);
            Box header = new Box().orientation(Box.Orientation.HORIZONTAL).gap(6)
                    .add(title).add(balance).add(search).add(filter);
            header.layout(new Rect(0, 0, width - 10, 25), null);
            check(title.size().width() == title.bounds().width());
            check(balance.minimumSize().equals(new Size(64, 20)));
            check(title.bounds().right() + 6 <= balance.bounds().x());
            check(balance.bounds().right() + 6 <= search.bounds().x());
            check(search.bounds().height() == 18 && search.bounds().y() == 3);
            check(filter.bounds().height() == 20 && filter.bounds().y() == 2);
            check(filter.bounds().right() <= width - 10);
            check(search.bounds().right() + 6 <= filter.bounds().x());
            if (width == 427) {
                check(title.size().width() > 60);
                check(balance.size().width() >= balance.minimumWidth());
                check(search.size().width() >= search.minimumWidth());
                check(filter.size().width() >= filter.minimumWidth());
            }
            if (width == 320) {
                check(title.size().width() == 60);
                check(balance.size().width() == balance.minimumWidth());
                check(search.size().width() >= search.minimumWidth());
                check(filter.size().width() >= filter.minimumWidth());
            }
        }

        Control inner = new Control().preferred(10, 10);
        Padding padding = new Padding(new Padding.Insets(1, 2, 3, 4)).child(inner);
        check(padding.measureWidth(null, 100) == 16);
        check(padding.measureHeight(null, 100) == 14);
        padding.layout(new Rect(0, 0, 20, 20), null);
        check(inner.bounds().equals(new Rect(4, 1, 14, 16)));

        List<Integer> items = new ArrayList<>();
        for (int index = 0; index < 400; index++) items.add(index);
        Grid<Integer> grid = new Grid<Integer>().columns(96, 4, 4)
                .mode(Grid.LayoutMode.MASONRY).adapter(new Grid.Adapter<>() {
                    @Override public int size() { return items.size(); }
                    @Override public Integer item(int index) { return items.get(index); }
                    @Override public Object key(Integer item) { return item; }
                    @Override public int measureHeight(Integer item, int width, net.minecraft.client.gui.Font font) {
                        return 30 + item % 3 * 10;
                    }
                    @Override public Grid.View<Integer> createView() {
                        return new Grid.View<>() {
                            @Override public void bind(Integer item) { }
                            @Override public void draw(ModularGuiContext context, Rect area, boolean hovered) { }
                        };
                    }
                });
        grid.layout(new Rect(0, 0, 400, 100), null);
        check(grid.columns() == 4 && grid.entries().size() == 400);
        check(grid.activeViews() > 0 && grid.activeViews() < 100);
        check(grid.hitItem(5, 5) != null);
        grid.setScroll(150);
        Object anchor = grid.entries().stream().filter(entry -> entry.bounds().y() <= grid.scroll()
                && entry.bounds().bottom() > grid.scroll()).findFirst().orElseThrow().key();
        Collections.reverse(items);
        grid.invalidate();
        grid.layout(new Rect(0, 0, 400, 100), null);
        Grid.Entry<Integer> restored = grid.entries().stream()
                .filter(entry -> entry.key().equals(anchor)).findFirst().orElseThrow();
        check(restored.bounds().y() <= grid.scroll() && restored.bounds().bottom() > grid.scroll());
        check(grid.activeViews() < 100);

        Grid<Object> fixed = new Grid<Object>().mode(Grid.LayoutMode.ROWS).columns(30, 2, 2);
        Control first = new Control().preferred(0, 10);
        Control second = new Control().preferred(0, 20);
        Control third = new Control().preferred(0, 10);
        fixed.add(first).add(second).add(third);
        fixed.layout(new Rect(0, 0, 62, 60), null);
        check(first.bounds().equals(new Rect(0, 0, 30, 10)));
        check(second.bounds().equals(new Rect(32, 0, 30, 20)));
        check(third.bounds().equals(new Rect(0, 22, 30, 10)));

        Overlay overlay = new Overlay();
        Control lower = new Control();
        Control upper = new Control();
        overlay.add(lower).add(upper);
        overlay.layout(new Rect(0, 0, 20, 20), null);
        check(overlay.hitTop(5, 5) == upper);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected Ore layout result");
    }
}
