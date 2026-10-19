package io.github.luyingyilong.ore.layout;


import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.Segmented;
import io.github.luyingyilong.ore.theme.OreFonts;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Standalone layout checks; run with java -ea and the client dependency classpath. */
public final class LayoutRegressionTest {
    private LayoutRegressionTest() {
    }

    public static void main(String[] args) {
        Control unset = new Control();
        check(unset.getMinimumSize().equals(new Size(0, 0)));
        check(unset.getSize().equals(new Size(0, 0)));
        Control freeParent = new Control();
        Control freeChild = new Control().setPosition(3, 4).setRequestedSize(11, 12);
        freeParent.addChild(freeChild);
        freeParent.layout(new Rect(0, 0, 20, 20), null);
        check(freeChild.getParent() == freeParent && freeChild.getBounds().equals(new Rect(3, 4, 11, 12)));
        check(freeParent.hitTop(4, 5) == freeChild);
        checkThrows(() -> freeChild.addChild(freeParent));
        check(freeParent.removeChild(freeChild) && freeChild.getParent() == null);

        Box limited = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
        Control left = new Control().setMinimum(30, 12);
        Control right = new Control().setMinimum(30, 12);
        limited.addChild(left).addChild(right);
        limited.layout(new Rect(0, 0, 50, 12), null);
        check(left.getBounds().width() == 30 && right.getBounds().width() == 30);
        check(right.getBounds().right() == 64);
        checkThrows(() -> left.setPosition(5, 5));
        checkThrows(() -> right.setRequestedSize(5, 5));
        check(limited.getChildren().equals(List.of(left, right)));
        limited.layout(new Rect(0, 0, 80, 12), null);
        check(!limited.getLayoutDirty());
        right.setMinimum(40, 12);
        check(limited.getLayoutDirty());

        Control weightedA = new Control().setMinimum(10, 10)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsStretchRatio(1.0F);
        Control weightedB = new Control().setMinimum(10, 10)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsStretchRatio(2.0F);
        Box weighted = new Box().setOrientation(Box.Orientation.HORIZONTAL)
                .add(weightedA).add(weightedB);
        weighted.layout(new Rect(0, 0, 100, 10), null);
        check(weightedA.getBounds().width() + weightedB.getBounds().width() == 100);
        check(weightedB.getBounds().width() > weightedA.getBounds().width());

        Font font = new Font(id -> null, false) {
            @Override public int width(String value) { return value.length() * 6; }
            @Override public int width(net.minecraft.network.chat.FormattedText value) {
                return width(value.getString());
            }
        };
        Segmented footer = new Segmented(List.of(Component.literal("Market"), Component.literal("Mine"),
                Component.literal("Publish"), Component.literal("Favorites"),
                Component.literal("History"), Component.literal("Claim")));
        footer.setSelected(5);
        footer.layout(new Rect(0, 0, 127, 20), font);
        check(footer.getScrollX() > 0 && footer.getBounds().height() == 20);
        check(io.github.luyingyilong.ore.EventFixture.press(footer,120,10));
        check(io.github.luyingyilong.ore.EventFixture.release(footer,120, 10, true));
        footer.layout(new Rect(0, 0, 427, 20), font);
        check(footer.getScrollX() == 0);

        Control inner = new Control().setPreferred(10, 10);
        Padding padding = new Padding(new Padding.Insets(1, 2, 3, 4)).setChild(inner);
        check(padding.measureWidth(null, 100) == 16);
        check(padding.measureHeight(null, 100) == 14);
        padding.layout(new Rect(0, 0, 20, 20), null);
        check(inner.getBounds().equals(new Rect(4, 1, 14, 16)));

        List<Integer> items = new ArrayList<>();
        for (int index = 0; index < 400; index++) items.add(index);
        Grid<Integer> grid = new Grid<Integer>().setColumns(96, 4, 4)
                .setMode(Grid.LayoutMode.MASONRY).setAdapter(new Grid.Adapter<>() {
                    @Override public int getSize() { return items.size(); }
                    @Override public Integer getItem(int index) { return items.get(index); }
                    @Override public Object getKey(Integer item) { return item; }
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
        Scroll gridScroll = new Scroll().setContent(grid).setEmbedded(true);
        gridScroll.layout(new Rect(0, 0, 400, 100), null);
        check(grid.getColumns() == 4 && grid.getEntries().size() == 400);
        check(grid.getActiveViews() > 0 && grid.getActiveViews() < 100);
        check(grid.hitItem(5, 5) != null);
        gridScroll.setScroll(0, 150);
        Object anchor = grid.getEntries().stream().filter(entry -> entry.bounds().y() <= gridScroll.getScrollY()
                && entry.bounds().bottom() > gridScroll.getScrollY()).findFirst().orElseThrow().key();
        Collections.reverse(items);
        grid.invalidate();
        gridScroll.layout(new Rect(0, 0, 400, 100), null);
        Grid.Entry<Integer> restored = grid.getEntries().stream()
                .filter(entry -> entry.key().equals(anchor)).findFirst().orElseThrow();
        check(restored.bounds().y() <= gridScroll.getScrollY()
                && restored.bounds().bottom() > gridScroll.getScrollY());
        check(grid.getActiveViews() < 100);
        gridScroll.scrollToTop();
        Collections.reverse(items);
        grid.invalidate();
        gridScroll.layout(new Rect(0, 0, 400, 100), null);
        check(gridScroll.getScrollY() == 0);
        gridScroll.layout(new Rect(0, 0, 80, 100), null);
        check(grid.getColumns() == 1 && grid.getContentWidth() == 96);
        check(gridScroll.getHorizontalVisible() && gridScroll.getVerticalVisible());
        check(gridScroll.scrollBy(16, 0) && gridScroll.getScrollX() == 16);
        gridScroll.scrollToTop();
        check(grid.hitItem(5 + gridScroll.getScrollX(), 5 + gridScroll.getScrollY()) != null);
        check(io.github.luyingyilong.ore.EventFixture.scroll(gridScroll,5, 5, -1.0D, false) && gridScroll.getScrollY() == 28);
        gridScroll.scrollToTop();
        List<Integer> chosen = new ArrayList<>();
        grid.itemActivated().connect(grid, chosen::add);
        check(io.github.luyingyilong.ore.EventFixture.press(gridScroll,5, 5) && chosen.equals(List.of(items.get(0))));

        Box tall = new Box().add(new Control().setMinimum(30, 80));
        Scroll scroll = new Scroll().setContent(tall).setHorizontal(Scroll.BarMode.DISABLED);
        scroll.layout(new Rect(0, 0, 50, 50), null);
        check(scroll.getVerticalVisible() && scroll.getViewport().width() == 44);
        check(scroll.getMaxScrollY() == 30 && scroll.scrollBy(0, 10));
        scroll.scrollToTop();
        check(io.github.luyingyilong.ore.EventFixture.scroll(scroll,5, 5, -1.0D, false) && scroll.getScrollY() == 28);
        scroll.setEmbedded(true);
        scroll.layout(new Rect(0, 0, 50, 50), null);
        check(scroll.getViewport().width() == 50 && scroll.getVerticalVisible());
        scroll.scrollToTop();
        check(io.github.luyingyilong.ore.EventFixture.press(scroll,47, 6));
        check(scroll.dragTo(47, 45) && scroll.getScrollY() > 0);
        check(scroll.endDrag() && !scroll.endDrag());
        scroll.setVertical(Scroll.BarMode.NEVER);
        scroll.layout(new Rect(0, 0, 50, 50), null);
        scroll.scrollToTop();
        check(!scroll.getVerticalVisible() && scroll.scrollBy(0, 5));
        scroll.setVertical(Scroll.BarMode.DISABLED);
        scroll.layout(new Rect(0, 0, 50, 50), null);
        check(scroll.getScrollY() == 0 && !scroll.scrollBy(0, 5));
        Box wide = new Box().setOrientation(Box.Orientation.HORIZONTAL)
                .add(new Control().setMinimum(40, 20)).add(new Control().setMinimum(40, 20));
        Scroll sideways = new Scroll().setContent(wide).setVertical(Scroll.BarMode.DISABLED);
        sideways.layout(new Rect(0, 0, 50, 50), null);
        check(sideways.getHorizontalVisible() && sideways.getViewport().height() == 44
                && sideways.getMaxScrollX() == 30);
        sideways.setHorizontal(Scroll.BarMode.NEVER);
        sideways.layout(new Rect(0, 0, 50, 50), null);
        check(!sideways.getHorizontalVisible() && sideways.scrollBy(5, 0));
        sideways.setHorizontal(Scroll.BarMode.DISABLED);
        sideways.layout(new Rect(0, 0, 50, 50), null);
        check(sideways.getScrollX() == 0 && !sideways.scrollBy(5, 0));
        List<String> scrolledClicks = new ArrayList<>();
        Control target = new Control() {
            @Override protected boolean pointerDown(int x, int y) {
                if (!hit(x, y)) return false;
                scrolledClicks.add("target");
                return true;
            }
        };
        target.setMinimum(30, 20);
        Scroll withChild = new Scroll().setContent(new Box()
                .add(new Control().setMinimum(30, 80)).add(target));
        withChild.layout(new Rect(0, 0, 50, 50), null);
        withChild.setScroll(0, 60);
        check(withChild.getScrollY() == 50 && io.github.luyingyilong.ore.EventFixture.press(withChild,10, 35)
                && scrolledClicks.equals(List.of("target")));
        Scroll always = new Scroll().setContent(new Control().setMinimum(10, 10))
                .setVertical(Scroll.BarMode.ALWAYS).setHorizontal(Scroll.BarMode.ALWAYS);
        always.layout(new Rect(0, 0, 50, 50), null);
        check(always.getVerticalVisible() && always.getHorizontalVisible()
                && always.getViewport().equals(new Rect(0, 0, 44, 44)));
        Scroll emptyGrid = new Scroll().setContent(new Grid<>());
        emptyGrid.layout(new Rect(0, 0, 50, 50), null);
        check(!emptyGrid.getVerticalVisible() && !emptyGrid.getHorizontalVisible());

        Grid<Object> fixed = new Grid<Object>().setMode(Grid.LayoutMode.ROWS).setColumns(30, 2, 2);
        Control first = new Control().setPreferred(0, 10);
        Control second = new Control().setPreferred(0, 20);
        Control third = new Control().setPreferred(0, 10);
        fixed.add(first).add(second).add(third);
        fixed.layout(new Rect(0, 0, 62, 60), null);
        check(first.getBounds().equals(new Rect(0, 0, 30, 10)));
        check(second.getBounds().equals(new Rect(32, 0, 30, 20)));
        check(third.getBounds().equals(new Rect(0, 22, 30, 10)));
        Window fontWindow = new Window(true).setContent(new Scroll().setContent(fixed));
        fontWindow.layoutViewport(62, 60, null);
        second.setPreferred(0, 30);
        OreFonts.resourcesReloaded();
        fontWindow.layoutViewport(62, 60, null);
        check(second.getBounds().height() == 30);
        check(third.getBounds().y() == 32);

        Overlay overlay = new Overlay();
        Control lower = new Control();
        Control upper = new Control();
        overlay.add(lower).add(upper);
        overlay.layout(new Rect(0, 0, 20, 20), null);
        check(overlay.hitTop(5, 5) == upper);
        List<String> clicks = new ArrayList<>();
        Control clickLower = new Control() {
            @Override protected boolean pointerDown(int x, int y) {
                if (!hit(x, y)) return false;
                clicks.add("lower");
                return true;
            }
        };
        Control clickUpper = new Control() {
            @Override protected boolean pointerDown(int x, int y) {
                if (!hit(x, y)) return false;
                clicks.add("upper");
                return true;
            }
        };
        Overlay clickable = new Overlay().add(clickLower).add(clickUpper);
        clickable.layout(new Rect(0, 0, 20, 20), null);
        check(io.github.luyingyilong.ore.EventFixture.press(clickable,5, 5) && clicks.equals(List.of("upper")));
        clickUpper.setVisible(false);
        check(io.github.luyingyilong.ore.EventFixture.press(clickable,5, 5) && clicks.equals(List.of("upper", "lower")));
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected Ore layout result");
    }

    private static void checkThrows(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected an invalid tree or container placement to fail");
    }
}
