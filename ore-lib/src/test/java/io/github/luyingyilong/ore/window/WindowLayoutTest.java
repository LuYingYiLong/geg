package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.Tree;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class WindowLayoutTest {
    public static void main(String[] args) {
        WindowContext a = new WindowContext(true), b = new WindowContext(true);
        a.dimensions(1000, 600, 2000, 1200, 4); a.mouse(200, 100);
        check(a.guiWidth() == 500 && a.guiHeight() == 300 && a.mouseX() == 100 && a.mouseY() == 50, "DPI coordinates");
        check(a.clientX(100) == 200 && a.clientY(50) == 100, "IME coordinates invert pointer conversion");
        for (double dpi : new double[]{1, 1.25, 1.5, 1.75, 2}) {
            double scale = WindowContext.nativePixelScale(2, dpi);
            check(scale >= 1 && scale == Math.rint(scale), "Integer framebuffer scale at fractional DPI");
        }
        check(WindowContext.nativePixelScale(2, 1.25) == 3, "125% DPI rounds to nearest physical pixel scale");
        b.dimensions(1001, 603, 1001, 603, 3);
        check(b.guiWidth() == 334 && b.guiHeight() == 201, "Partial edge pixel stays in layout viewport");
        check(Math.abs(b.projectionWidth() * 3 - 1001) < 0.00001
                && Math.abs(b.projectionHeight() * 3 - 603) < 0.00001, "Projection never stretches ceil-rounded GUI dimensions");
        b.mouse(303, 153);
        check(b.mouseX() == 101 && b.mouseY() == 51 && b.clientX(101) == 303, "Odd-size pointer and IME match pixel grid");
        rejects(() -> WindowContext.nativePixelScale(Double.NaN, 1));
        a.button(0, true); a.key(65, 1, 2); check(a.mouseDown(0) && !b.mouseDown(0) && !b.keyDown(65), "Input isolation");
        a.focused(false); check(!a.mouseDown(0) && !a.keyDown(65), "Focus loss drops held buttons");
        try (var outer = a.enter()) { check(WindowContext.current() == a, "Active context"); try (var inner = b.enter()) { check(WindowContext.current() == b, "Nested context"); } check(WindowContext.current() == a, "Restore context"); }
        check(WindowContext.current() == WindowContext.primary(), "No context leak");
        check(Window.embedded().host() instanceof EmbeddedWindowHost, "Embedded factory");
        check(Window.nativeWindow(WindowOptions.defaults("Test")).host().nativeWindow(), "Native factory is lazy");
        rejects(() -> new WindowOptions("Bad", 0, 10, 1, 1, 2));

        Control left = new Control().minimum(20, 10), right = new Control().minimum(30, 10);
        Splitter split = new Splitter(left, right).ratio(0.5); Window root = new Window(true).content(split);
        root.layoutViewport(104, 50, null); check(left.bounds().width() == 50 && right.bounds().x() == 54, "Split geometry");
        root.pointerPressed(51, 12, null); root.pointerDragged(72, 12); root.pointerReleased(72, 12);
        root.layoutViewport(104, 50, null); check(left.bounds().width() == 70, "Divider uses Window capture");
        root.layoutViewport(25, 50, null); check(left.bounds().width() >= 20 && right.bounds().width() >= 30, "Split preserves minimums");

        Tree<String> tree = new Tree<>(); List<Tree.Item<String>> items = new ArrayList<>();
        items.add(new Tree.Item<>("root", "", Component.literal("Root"), "root"));
        for (int i=0;i<500;i++) items.add(new Tree.Item<>("item"+i,"root",Component.literal("Node "+i),"item"+i));
        tree.items(items); tree.layout(new Rect(0,0,200,110),null); tree.visibleRegion(new Rect(0,0,200,110));
        check(tree.visibleRows()==501 && tree.instantiatedRows()<=16,"Tree virtualization");
        tree.visibleRegion(new Rect(0,4400,200,110)); check(tree.instantiatedRows()<=16,"Virtual rows stay bounded while scrolling");
        tree.expanded("root",false); check(tree.visibleRows()==1,"Collapse");
        tree.search("Node 499"); check(tree.visibleRows()==2,"Search includes ancestry");
        rejects(() -> tree.items(List.of(items.get(0),items.get(0))));
        Tree<String> wideTree = new Tree<>();
        wideTree.items(List.of(new Tree.Item<>("wide", "", Component.literal("Long tree node ".repeat(12)), "wide")));
        Scroll treeScroll = new Scroll().content(wideTree);
        Window treeWindow = new Window(true).content(treeScroll);
        treeWindow.layoutViewport(160,100,null);
        check(treeScroll.horizontalVisible()&&treeScroll.maxScrollX()>0,"Tree measures full text width for horizontal scroll");
        treeScroll.setScroll(180,0);
        check(treeScroll.scrollX()==180&&wideTree.children().get(0).bounds().width()==wideTree.contentExtent().width(),"Virtual row retains complete content width");
        wideTree.items(List.of(new Tree.Item<>("wide", "", Component.literal("Short"), "wide")));
        treeWindow.layoutViewport(160,100,null);
        check(!treeScroll.horizontalVisible()&&treeScroll.scrollX()==0,"Shorter tree content removes horizontal overflow");
        System.out.println("ORE_WINDOW_LAYOUT_PASS");
    }
    private static void check(boolean value, String text) { if(!value)throw new AssertionError(text); }
    private static void rejects(Runnable action) { try { action.run(); throw new AssertionError("Expected rejection"); } catch(IllegalArgumentException expected) { } }
}
