package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.signal.Signals;

/** SizeFlags allocation, alignment and overflow without a running Minecraft client. */
public final class SizeFlagsLayoutTest {
    public static void main(String[] args) {
        Control defaults = new Control();
        check(defaults.sizeFlagsHorizontal() == SizeFlags.SIZE_FILL
                && defaults.sizeFlagsVertical() == SizeFlags.SIZE_FILL
                && defaults.sizeFlagsStretchRatio() == 1.0F, "Defaults");
        for (int flags : new int[]{0, 1, 2, 3, 4, 6, 8, 10})
            defaults.sizeFlagsHorizontal(flags).sizeFlagsVertical(flags);
        for (int flags : new int[]{-1, 5, 7, 9, 11, 12, 15, 16}) {
            rejects(() -> defaults.sizeFlagsHorizontal(flags));
            rejects(() -> defaults.sizeFlagsVertical(flags));
        }
        for (float ratio : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY})
            rejects(() -> defaults.sizeFlagsStretchRatio(ratio));

        Control child = new Control().preferred(20, 10);
        Panel panel = new Panel(null).child(child);
        int[] flags = {SizeFlags.SIZE_SHRINK_BEGIN, SizeFlags.SIZE_SHRINK_CENTER, SizeFlags.SIZE_SHRINK_END};
        for (int index = 0; index < flags.length; index++) {
            child.sizeFlagsHorizontal(flags[index]).sizeFlagsVertical(flags[index]);
            panel.layout(new Rect(0, 0, 100, 40), null);
            check(child.bounds().equals(new Rect(index * 40, index * 15, 20, 10)), "Shrink alignment");
        }
        child.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_FILL);
        panel.layout(new Rect(0, 0, 100, 40), null);
        check(child.size().equals(new Size(100, 40)), "Fill without expansion");
        child.minimum(120, 50);
        panel.layout(new Rect(0, 0, 100, 40), null);
        check(child.size().equals(new Size(120, 50)), "Minimum overflows slot");

        for (Box.Orientation orientation : Box.Orientation.values()) {
            Control a = new Control().minimum(10, 10).preferred(20, 20)
                    .sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL)
                    .sizeFlagsStretchRatio(2);
            Control b = new Control().minimum(10, 10)
                    .sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
            Box box = new Box().orientation(orientation).add(a).add(b);
            boolean horizontal = orientation == Box.Orientation.HORIZONTAL;
            box.layout(new Rect(0, 0, horizontal ? 120 : 30, horizontal ? 30 : 120), null);
            check((horizontal ? a.size().width() : a.size().height()) == 80, "Two-thirds slot");
            check((horizontal ? b.size().width() : b.size().height()) == 40, "One-third slot");
            if (horizontal) a.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND | SizeFlags.SIZE_SHRINK_CENTER);
            else a.sizeFlagsVertical(SizeFlags.SIZE_EXPAND | SizeFlags.SIZE_SHRINK_CENTER);
            box.layout(box.bounds(), null);
            check((horizontal ? a.bounds().x() : a.bounds().y()) == 30, "Expansion does not imply fill");
            check((horizontal ? a.size().width() : a.size().height()) == 20, "Natural size in expanded slot");
            a.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
            a.minimum(horizontal ? 90 : 10, horizontal ? 10 : 90);
            box.layout(box.bounds(), null);
            check((horizontal ? a.size().width() : a.size().height()) == 90, "Freeze at minimum");
            check((horizontal ? b.size().width() : b.size().height()) == 30, "Redistribute after minimum");
            b.minimum(horizontal ? 50 : 10, horizontal ? 10 : 50);
            box.layout(box.bounds(), null);
            check((horizontal ? b.bounds().right() : b.bounds().bottom()) == 140, "Overflow preserves minima");
        }

        Box rounding = new Box().orientation(Box.Orientation.HORIZONTAL);
        for (int index = 0; index < 3; index++) rounding.add(new Control().sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL));
        rounding.layout(new Rect(0, 0, 10, 10), null);
        check(rounding.children().get(0).size().width() == 4
                && rounding.children().get(1).size().width() == 3
                && rounding.children().get(2).bounds().right() == 10, "Stable integer remainder");
        rounding.children().get(1).visible(false);
        rounding.gap(2).layout(new Rect(0, 0, 10, 10), null);
        check(rounding.children().get(0).size().width() == 4
                && rounding.children().get(2).bounds().x() == 6, "Hidden children do not reserve slots");
        check(!rounding.layoutDirty(), "Layout completed");
        rounding.children().get(0).sizeFlagsStretchRatio(3);
        check(rounding.layoutDirty(), "Ratio invalidates ancestors");
        rounding.layout(rounding.bounds(), null);
        rounding.children().get(0).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_END);
        check(rounding.layoutDirty(), "Flags invalidate ancestors");

        Control insetChild = new Control().preferred(10, 8).sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END)
                .sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Padding inset = new Padding(new Padding.Insets(2, 3, 4, 5)).child(insetChild);
        inset.layout(new Rect(0, 0, 40, 30), null);
        check(insetChild.bounds().equals(new Rect(27, 10, 10, 8)), "Padding uses inner slot alignment");
        Grid<Object> grid = new Grid<>().mode(Grid.LayoutMode.ROWS).columns(30, 2, 2);
        Control card = new Control().preferred(20, 10).sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END);
        grid.add(card).add(new Control().preferred(0, 20));
        grid.layout(new Rect(0, 0, 62, 40), null);
        check(card.bounds().equals(new Rect(10, 0, 20, 10)), "Grid aligns within fixed-width cells");
        Control fill = new Control().preferred(20, 10);
        Control expand = new Control().sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        Box mainFill = new Box().orientation(Box.Orientation.HORIZONTAL).add(fill).add(expand);
        mainFill.layout(new Rect(0, 0, 100, 20), null);
        check(fill.size().width() == 20 && expand.size().width() == 80, "Fill does not compete for main-axis space");
        fill.preferred(120, 10).minimum(30, 10);
        expand.minimum(40, 10);
        mainFill.layout(new Rect(0, 0, 100, 20), null);
        check(fill.size().width() == 60 && expand.size().width() == 40, "Preferred size can shrink to reserve expanded minimum");
        virtualGrid();
    }

    private static void virtualGrid() {
        class Card extends Control implements Grid.View<Integer> {
            @Override public void bind(Integer item) {
                preferred(40, 30);
                sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END);
            }
            @Override public void draw(brachy.modularui.screen.viewport.ModularGuiContext context, Rect area, boolean hovered) { }
        }
        int[] clicked = {-1};
        Grid<Integer> virtual = Signals.configure(new Grid<Integer>().adapter(new Grid.Adapter<>() {
            @Override public int size() { return 400; }
            @Override public Integer item(int index) { return index; }
            @Override public Object key(Integer item) { return item; }
            @Override public int measureHeight(Integer item, int width, net.minecraft.client.gui.Font font) { return 30; }
            @Override public Grid.View<Integer> createView() { return new Card(); }
        }), connected -> connected.itemActivated().connect(connected, item -> clicked[0] = item));
        Scroll scroll = new Scroll().embedded(true).content(virtual).horizontal(Scroll.BarMode.DISABLED);
        scroll.layout(new Rect(0, 0, 100, 50), null);
        Control first = virtual.children().get(0);
        check(first.bounds().equals(new Rect(60, 0, 40, 30)), "Virtual cards are laid out before drawing");
        check(!io.github.luyingyilong.ore.EventFixture.press(scroll,5, 5) && clicked[0] == -1, "Unused cell space is not a card hit");
        check(io.github.luyingyilong.ore.EventFixture.press(scroll,65, 5) && clicked[0] == 0, "Virtual hit uses actual card rectangle");
        first.sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER);
        virtual.layout(virtual.bounds(), null);
        check(first.bounds().x() == 30, "Virtual child flag changes trigger layout");
        scroll.setScroll(0, 340);
        check(virtual.activeViews() < 12, "Virtual binding remains bounded");
        check(io.github.luyingyilong.ore.EventFixture.press(scroll,65, 5) && clicked[0] == 10, "Recycled cards receive layout before interaction");
        Control fixed = new Control().preferred(10, 10);
        Grid<Object> fixedGrid = new Grid<>().columns(30, 1, 0).add(fixed);
        fixedGrid.layout(new Rect(0, 0, 30, 40), null);
        fixed.preferred(10, 20);
        fixedGrid.layout(fixedGrid.bounds(), null);
        check(fixed.size().height() == 20 && fixedGrid.contentHeight() == 20, "Fixed child measurements invalidate the grid");
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid flags/ratio accepted");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
