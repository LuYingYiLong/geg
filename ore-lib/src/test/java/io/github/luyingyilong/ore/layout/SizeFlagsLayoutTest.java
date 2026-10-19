package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.signal.Signals;

/** SizeFlags allocation, alignment and overflow without a running Minecraft client. */
public final class SizeFlagsLayoutTest {
    public static void main(String[] args) {
        Control defaults = new Control();
        check(defaults.getSizeFlagsHorizontal() == SizeFlags.SIZE_FILL
                && defaults.getSizeFlagsVertical() == SizeFlags.SIZE_FILL
                && defaults.getSizeFlagsStretchRatio() == 1.0F, "Defaults");
        for (int flags : new int[]{0, 1, 2, 3, 4, 6, 8, 10})
            defaults.setSizeFlagsHorizontal(flags).setSizeFlagsVertical(flags);
        for (int flags : new int[]{-1, 5, 7, 9, 11, 12, 15, 16}) {
            rejects(() -> defaults.setSizeFlagsHorizontal(flags));
            rejects(() -> defaults.setSizeFlagsVertical(flags));
        }
        for (float ratio : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY})
            rejects(() -> defaults.setSizeFlagsStretchRatio(ratio));

        Control child = new Control().setPreferred(20, 10);
        Panel panel = new Panel(null).setChild(child);
        int[] flags = {SizeFlags.SIZE_SHRINK_BEGIN, SizeFlags.SIZE_SHRINK_CENTER, SizeFlags.SIZE_SHRINK_END};
        for (int index = 0; index < flags.length; index++) {
            child.setSizeFlagsHorizontal(flags[index]).setSizeFlagsVertical(flags[index]);
            panel.layout(new Rect(0, 0, 100, 40), null);
            check(child.getBounds().equals(new Rect(index * 40, index * 15, 20, 10)), "Shrink alignment");
        }
        child.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        panel.layout(new Rect(0, 0, 100, 40), null);
        check(child.getSize().equals(new Size(100, 40)), "Fill without expansion");
        child.setMinimum(120, 50);
        panel.layout(new Rect(0, 0, 100, 40), null);
        check(child.getSize().equals(new Size(120, 50)), "Minimum overflows slot");

        for (Box.Orientation orientation : Box.Orientation.values()) {
            Control a = new Control().setMinimum(10, 10).setPreferred(20, 20)
                    .setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL)
                    .setSizeFlagsStretchRatio(2);
            Control b = new Control().setMinimum(10, 10)
                    .setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
            Box box = new Box().setOrientation(orientation).add(a).add(b);
            boolean horizontal = orientation == Box.Orientation.HORIZONTAL;
            box.layout(new Rect(0, 0, horizontal ? 120 : 30, horizontal ? 30 : 120), null);
            check((horizontal ? a.getSize().width() : a.getSize().height()) == 80, "Two-thirds slot");
            check((horizontal ? b.getSize().width() : b.getSize().height()) == 40, "One-third slot");
            if (horizontal) a.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND | SizeFlags.SIZE_SHRINK_CENTER);
            else a.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND | SizeFlags.SIZE_SHRINK_CENTER);
            box.layout(box.getBounds(), null);
            check((horizontal ? a.getBounds().x() : a.getBounds().y()) == 30, "Expansion does not imply fill");
            check((horizontal ? a.getSize().width() : a.getSize().height()) == 20, "Natural size in expanded slot");
            a.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
            a.setMinimum(horizontal ? 90 : 10, horizontal ? 10 : 90);
            box.layout(box.getBounds(), null);
            check((horizontal ? a.getSize().width() : a.getSize().height()) == 90, "Freeze at minimum");
            check((horizontal ? b.getSize().width() : b.getSize().height()) == 30, "Redistribute after minimum");
            b.setMinimum(horizontal ? 50 : 10, horizontal ? 10 : 50);
            box.layout(box.getBounds(), null);
            check((horizontal ? b.getBounds().right() : b.getBounds().bottom()) == 140, "Overflow preserves minima");
        }

        Box rounding = new Box().setOrientation(Box.Orientation.HORIZONTAL);
        for (int index = 0; index < 3; index++) rounding.add(new Control().setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL));
        rounding.layout(new Rect(0, 0, 10, 10), null);
        check(rounding.getChildren().get(0).getSize().width() == 4
                && rounding.getChildren().get(1).getSize().width() == 3
                && rounding.getChildren().get(2).getBounds().right() == 10, "Stable integer remainder");
        rounding.getChildren().get(1).setVisible(false);
        rounding.setGap(2).layout(new Rect(0, 0, 10, 10), null);
        check(rounding.getChildren().get(0).getSize().width() == 4
                && rounding.getChildren().get(2).getBounds().x() == 6, "Hidden children do not reserve slots");
        check(!rounding.getLayoutDirty(), "Layout completed");
        rounding.getChildren().get(0).setSizeFlagsStretchRatio(3);
        check(rounding.getLayoutDirty(), "Ratio invalidates ancestors");
        rounding.layout(rounding.getBounds(), null);
        rounding.getChildren().get(0).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_END);
        check(rounding.getLayoutDirty(), "Flags invalidate ancestors");

        Control insetChild = new Control().setPreferred(10, 8).setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END)
                .setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Padding inset = new Padding(new Padding.Insets(2, 3, 4, 5)).setChild(insetChild);
        inset.layout(new Rect(0, 0, 40, 30), null);
        check(insetChild.getBounds().equals(new Rect(27, 10, 10, 8)), "Padding uses inner slot alignment");
        Grid<Object> grid = new Grid<>().setMode(Grid.LayoutMode.ROWS).setColumns(30, 2, 2);
        Control card = new Control().setPreferred(20, 10).setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END);
        grid.add(card).add(new Control().setPreferred(0, 20));
        grid.layout(new Rect(0, 0, 62, 40), null);
        check(card.getBounds().equals(new Rect(10, 0, 20, 10)), "Grid aligns within fixed-width cells");
        Control fill = new Control().setPreferred(20, 10);
        Control expand = new Control().setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        Box mainFill = new Box().setOrientation(Box.Orientation.HORIZONTAL).add(fill).add(expand);
        mainFill.layout(new Rect(0, 0, 100, 20), null);
        check(fill.getSize().width() == 20 && expand.getSize().width() == 80, "Fill does not compete for main-axis space");
        fill.setPreferred(120, 10).setMinimum(30, 10);
        expand.setMinimum(40, 10);
        mainFill.layout(new Rect(0, 0, 100, 20), null);
        check(fill.getSize().width() == 60 && expand.getSize().width() == 40, "Preferred size can shrink to reserve expanded minimum");
        virtualGrid();
    }

    private static void virtualGrid() {
        class Card extends Control implements Grid.View<Integer> {
            @Override public void bind(Integer item) {
                setPreferred(40, 30);
                setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END);
            }
            @Override public void draw(brachy.modularui.screen.viewport.ModularGuiContext context, Rect area, boolean hovered) { }
        }
        int[] clicked = {-1};
        Grid<Integer> virtual = Signals.configure(new Grid<Integer>().setAdapter(new Grid.Adapter<>() {
            @Override public int getSize() { return 400; }
            @Override public Integer getItem(int index) { return index; }
            @Override public Object getKey(Integer item) { return item; }
            @Override public int measureHeight(Integer item, int width, net.minecraft.client.gui.Font font) { return 30; }
            @Override public Grid.View<Integer> createView() { return new Card(); }
        }), connected -> connected.itemActivated().connect(connected, item -> clicked[0] = item));
        Scroll scroll = new Scroll().setEmbedded(true).setContent(virtual).setHorizontal(Scroll.BarMode.DISABLED);
        scroll.layout(new Rect(0, 0, 100, 50), null);
        Control first = virtual.getChildren().get(0);
        check(first.getBounds().equals(new Rect(60, 0, 40, 30)), "Virtual cards are laid out before drawing");
        check(!io.github.luyingyilong.ore.EventFixture.press(scroll,5, 5) && clicked[0] == -1, "Unused cell space is not a card hit");
        check(io.github.luyingyilong.ore.EventFixture.press(scroll,65, 5) && clicked[0] == 0, "Virtual hit uses actual card rectangle");
        first.setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER);
        virtual.layout(virtual.getBounds(), null);
        check(first.getBounds().x() == 30, "Virtual child flag changes trigger layout");
        scroll.setScroll(0, 340);
        check(virtual.getActiveViews() < 12, "Virtual binding remains bounded");
        check(io.github.luyingyilong.ore.EventFixture.press(scroll,65, 5) && clicked[0] == 10, "Recycled cards receive layout before interaction");
        Control fixed = new Control().setPreferred(10, 10);
        Grid<Object> fixedGrid = new Grid<>().setColumns(30, 1, 0).add(fixed);
        fixedGrid.layout(new Rect(0, 0, 30, 40), null);
        fixed.setPreferred(10, 20);
        fixedGrid.layout(fixedGrid.getBounds(), null);
        check(fixed.getSize().height() == 20 && fixedGrid.getContentHeight() == 20, "Fixed child measurements invalidate the grid");
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid flags/ratio accepted");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
