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
        check(unset.minimumSize().equals(new Size(0, 0)));
        check(unset.size().equals(new Size(0, 0)));
        Control freeParent = new Control();
        Control freeChild = new Control().position(3, 4).requestedSize(11, 12);
        freeParent.addChild(freeChild);
        freeParent.layout(new Rect(0, 0, 20, 20), null);
        check(freeChild.parent() == freeParent && freeChild.bounds().equals(new Rect(3, 4, 11, 12)));
        check(freeParent.hitTop(4, 5) == freeChild);
        checkThrows(() -> freeChild.addChild(freeParent));
        check(freeParent.removeChild(freeChild) && freeChild.parent() == null);

        Box limited = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4);
        Control left = new Control().minimum(30, 12);
        Control right = new Control().minimum(30, 12);
        limited.addChild(left).addChild(right);
        limited.layout(new Rect(0, 0, 50, 12), null);
        check(left.bounds().width() == 30 && right.bounds().width() == 30);
        check(right.bounds().right() == 64);
        checkThrows(() -> left.position(5, 5));
        checkThrows(() -> right.requestedSize(5, 5));
        check(limited.children().equals(List.of(left, right)));
        limited.layout(new Rect(0, 0, 80, 12), null);
        check(!limited.layoutDirty());
        right.minimum(40, 12);
        check(limited.layoutDirty());

        Control weightedA = new Control().minimum(10, 10)
                .sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER).sizeFlagsStretchRatio(1.0F);
        Control weightedB = new Control().minimum(10, 10)
                .sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER).sizeFlagsStretchRatio(2.0F);
        Box weighted = new Box().orientation(Box.Orientation.HORIZONTAL)
                .add(weightedA).add(weightedB);
        weighted.layout(new Rect(0, 0, 100, 10), null);
        check(weightedA.bounds().width() + weightedB.bounds().width() == 100);
        check(weightedB.bounds().width() > weightedA.bounds().width());

        Font font = new Font(id -> null, false) {
            @Override public int width(String value) { return value.length() * 6; }
            @Override public int width(net.minecraft.network.chat.FormattedText value) {
                return width(value.getString());
            }
        };
        Segmented footer = new Segmented(List.of(Component.literal("Market"), Component.literal("Mine"),
                Component.literal("Publish"), Component.literal("Favorites"),
                Component.literal("History"), Component.literal("Claim")));
        footer.selected(5);
        footer.layout(new Rect(0, 0, 127, 20), font);
        check(footer.scrollX() > 0 && footer.bounds().height() == 20);
        check(footer.clickAt(0, 0, 120, 10, font) == 5);
        check(footer.releasePointer(120, 10, true));
        footer.layout(new Rect(0, 0, 427, 20), font);
        check(footer.scrollX() == 0);

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
        Scroll gridScroll = new Scroll().content(grid).embedded(true);
        gridScroll.layout(new Rect(0, 0, 400, 100), null);
        check(grid.columns() == 4 && grid.entries().size() == 400);
        check(grid.activeViews() > 0 && grid.activeViews() < 100);
        check(grid.hitItem(5, 5) != null);
        gridScroll.setScroll(0, 150);
        Object anchor = grid.entries().stream().filter(entry -> entry.bounds().y() <= gridScroll.scrollY()
                && entry.bounds().bottom() > gridScroll.scrollY()).findFirst().orElseThrow().key();
        Collections.reverse(items);
        grid.invalidate();
        gridScroll.layout(new Rect(0, 0, 400, 100), null);
        Grid.Entry<Integer> restored = grid.entries().stream()
                .filter(entry -> entry.key().equals(anchor)).findFirst().orElseThrow();
        check(restored.bounds().y() <= gridScroll.scrollY()
                && restored.bounds().bottom() > gridScroll.scrollY());
        check(grid.activeViews() < 100);
        gridScroll.scrollToTop();
        Collections.reverse(items);
        grid.invalidate();
        gridScroll.layout(new Rect(0, 0, 400, 100), null);
        check(gridScroll.scrollY() == 0);
        gridScroll.layout(new Rect(0, 0, 80, 100), null);
        check(grid.columns() == 1 && grid.contentWidth() == 96);
        check(gridScroll.horizontalVisible() && gridScroll.verticalVisible());
        check(gridScroll.scrollBy(16, 0) && gridScroll.scrollX() == 16);
        gridScroll.scrollToTop();
        check(grid.hitItem(5 + gridScroll.scrollX(), 5 + gridScroll.scrollY()) != null);
        check(gridScroll.dispatchScroll(5, 5, -1.0D, false) && gridScroll.scrollY() == 28);
        gridScroll.scrollToTop();
        List<Integer> chosen = new ArrayList<>();
        grid.onItemClick(chosen::add);
        check(gridScroll.dispatchPointer(5, 5) && chosen.equals(List.of(items.get(0))));

        Box tall = new Box().add(new Control().minimum(30, 80));
        Scroll scroll = new Scroll().content(tall).horizontal(Scroll.BarMode.DISABLED);
        scroll.layout(new Rect(0, 0, 50, 50), null);
        check(scroll.verticalVisible() && scroll.viewport().width() == 44);
        check(scroll.maxScrollY() == 30 && scroll.scrollBy(0, 10));
        scroll.scrollToTop();
        check(scroll.dispatchScroll(5, 5, -1.0D, false) && scroll.scrollY() == 28);
        scroll.embedded(true);
        scroll.layout(new Rect(0, 0, 50, 50), null);
        check(scroll.viewport().width() == 50 && scroll.verticalVisible());
        scroll.scrollToTop();
        check(scroll.dispatchPointer(47, 6));
        check(scroll.dragTo(47, 45) && scroll.scrollY() > 0);
        check(scroll.endDrag() && !scroll.endDrag());
        scroll.vertical(Scroll.BarMode.NEVER);
        scroll.layout(new Rect(0, 0, 50, 50), null);
        scroll.scrollToTop();
        check(!scroll.verticalVisible() && scroll.scrollBy(0, 5));
        scroll.vertical(Scroll.BarMode.DISABLED);
        scroll.layout(new Rect(0, 0, 50, 50), null);
        check(scroll.scrollY() == 0 && !scroll.scrollBy(0, 5));
        Box wide = new Box().orientation(Box.Orientation.HORIZONTAL)
                .add(new Control().minimum(40, 20)).add(new Control().minimum(40, 20));
        Scroll sideways = new Scroll().content(wide).vertical(Scroll.BarMode.DISABLED);
        sideways.layout(new Rect(0, 0, 50, 50), null);
        check(sideways.horizontalVisible() && sideways.viewport().height() == 44
                && sideways.maxScrollX() == 30);
        sideways.horizontal(Scroll.BarMode.NEVER);
        sideways.layout(new Rect(0, 0, 50, 50), null);
        check(!sideways.horizontalVisible() && sideways.scrollBy(5, 0));
        sideways.horizontal(Scroll.BarMode.DISABLED);
        sideways.layout(new Rect(0, 0, 50, 50), null);
        check(sideways.scrollX() == 0 && !sideways.scrollBy(5, 0));
        List<String> scrolledClicks = new ArrayList<>();
        Control target = new Control() {
            @Override protected boolean onPointerPressed(int x, int y) {
                if (!hit(x, y)) return false;
                scrolledClicks.add("target");
                return true;
            }
        };
        target.minimum(30, 20);
        Scroll withChild = new Scroll().content(new Box()
                .add(new Control().minimum(30, 80)).add(target));
        withChild.layout(new Rect(0, 0, 50, 50), null);
        withChild.setScroll(0, 60);
        check(withChild.scrollY() == 50 && withChild.dispatchPointer(10, 35)
                && scrolledClicks.equals(List.of("target")));
        Scroll always = new Scroll().content(new Control().minimum(10, 10))
                .vertical(Scroll.BarMode.ALWAYS).horizontal(Scroll.BarMode.ALWAYS);
        always.layout(new Rect(0, 0, 50, 50), null);
        check(always.verticalVisible() && always.horizontalVisible()
                && always.viewport().equals(new Rect(0, 0, 44, 44)));
        Scroll emptyGrid = new Scroll().content(new Grid<>());
        emptyGrid.layout(new Rect(0, 0, 50, 50), null);
        check(!emptyGrid.verticalVisible() && !emptyGrid.horizontalVisible());

        Grid<Object> fixed = new Grid<Object>().mode(Grid.LayoutMode.ROWS).columns(30, 2, 2);
        Control first = new Control().preferred(0, 10);
        Control second = new Control().preferred(0, 20);
        Control third = new Control().preferred(0, 10);
        fixed.add(first).add(second).add(third);
        fixed.layout(new Rect(0, 0, 62, 60), null);
        check(first.bounds().equals(new Rect(0, 0, 30, 10)));
        check(second.bounds().equals(new Rect(32, 0, 30, 20)));
        check(third.bounds().equals(new Rect(0, 22, 30, 10)));
        Window fontWindow = new Window(true).content(new Scroll().content(fixed));
        fontWindow.layoutViewport(62, 60, null);
        second.preferred(0, 30);
        OreFonts.resourcesReloaded();
        fontWindow.layoutViewport(62, 60, null);
        check(second.bounds().height() == 30);
        check(third.bounds().y() == 32);

        Overlay overlay = new Overlay();
        Control lower = new Control();
        Control upper = new Control();
        overlay.add(lower).add(upper);
        overlay.layout(new Rect(0, 0, 20, 20), null);
        check(overlay.hitTop(5, 5) == upper);
        List<String> clicks = new ArrayList<>();
        Control clickLower = new Control() {
            @Override protected boolean onPointerPressed(int x, int y) {
                if (!hit(x, y)) return false;
                clicks.add("lower");
                return true;
            }
        };
        Control clickUpper = new Control() {
            @Override protected boolean onPointerPressed(int x, int y) {
                if (!hit(x, y)) return false;
                clicks.add("upper");
                return true;
            }
        };
        Overlay clickable = new Overlay().add(clickLower).add(clickUpper);
        clickable.layout(new Rect(0, 0, 20, 20), null);
        check(clickable.dispatchPointer(5, 5) && clicks.equals(List.of("upper")));
        clickUpper.visible(false);
        check(clickable.dispatchPointer(5, 5) && clicks.equals(List.of("upper", "lower")));
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
