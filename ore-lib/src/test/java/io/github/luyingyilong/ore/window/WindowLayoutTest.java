package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.layout.Splitter;

import io.github.luyingyilong.ore.control.data.Tree;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class WindowLayoutTest {
    public static void main(String[] args) {
        WindowContext a = new WindowContext(true), b = new WindowContext(true);
        a.setDimensions(1000, 600, 2000, 1200, 4); a.setMouse(200, 100);
        check(a.getGuiWidth() == 500 && a.getGuiHeight() == 300 && a.getMouseX() == 100 && a.getMouseY() == 50, "DPI coordinates");
        check(a.clientX(100) == 200 && a.clientY(50) == 100, "IME coordinates invert pointer conversion");
        for (double dpi : new double[]{1, 1.25, 1.5, 1.75, 2}) {
            double scale = WindowContext.nativePixelScale(2, dpi);
            check(scale >= 1 && scale == Math.rint(scale), "Integer framebuffer scale at fractional DPI");
        }
        check(WindowContext.nativePixelScale(2, 1.25) == 3, "125% DPI rounds to nearest physical pixel scale");
        b.setDimensions(1001, 603, 1001, 603, 3);
        check(b.getGuiWidth() == 334 && b.getGuiHeight() == 201, "Partial edge pixel stays in layout viewport");
        check(Math.abs(b.getProjectionWidth() * 3 - 1001) < 0.00001
                && Math.abs(b.getProjectionHeight() * 3 - 603) < 0.00001, "Projection never stretches ceil-rounded GUI dimensions");
        b.setMouse(303, 153);
        check(b.getMouseX() == 101 && b.getMouseY() == 51 && b.clientX(101) == 303, "Odd-size pointer and IME match pixel grid");
        rejects(() -> WindowContext.nativePixelScale(Double.NaN, 1));
        a.button(0, true); a.key(65, 1, 2); check(a.mouseDown(0) && !b.mouseDown(0) && !b.keyDown(65), "Input isolation");
        a.setFocused(false); check(!a.mouseDown(0) && !a.keyDown(65), "Focus loss drops held buttons");
        try (var outer = a.enter()) { check(WindowContext.getCurrent() == a, "Active context"); try (var inner = b.enter()) { check(WindowContext.getCurrent() == b, "Nested context"); } check(WindowContext.getCurrent() == a, "Restore context"); }
        check(WindowContext.getCurrent() == WindowContext.getPrimary(), "No context leak");
        check(Window.embedded().getHost() instanceof EmbeddedWindowHost, "Embedded factory");
        check(Window.nativeWindow(WindowOptions.defaults("Test")).getHost().getNativeWindow(), "Native factory is lazy");
        rejects(() -> new WindowOptions("Bad", 0, 10, 1, 1, 2));

        Control left = new Control().setMinimum(20, 10), right = new Control().setMinimum(30, 10);
        Splitter split = new Splitter(left, right).setRatio(0.5); Window root = new Window(true).setContent(split);
        root.layoutViewport(104, 50, null); check(left.getBounds().width() == 50 && right.getBounds().x() == 54, "Split geometry");
        io.github.luyingyilong.ore.event.WindowInputs.press(root,51, 12, null); io.github.luyingyilong.ore.event.WindowInputs.move(root,72, 12); io.github.luyingyilong.ore.event.WindowInputs.release(root,72, 12);
        root.layoutViewport(104, 50, null); check(left.getBounds().width() == 70, "Divider uses Window capture");
        root.layoutViewport(25, 50, null); check(left.getBounds().width() >= 20 && right.getBounds().width() >= 30, "Split preserves minimums");

        Tree<String> tree = new Tree<>(); List<Tree.Item<String>> items = new ArrayList<>();
        items.add(new Tree.Item<>("root", "", Component.literal("Root"), "root"));
        for (int i=0;i<500;i++) items.add(new Tree.Item<>("item"+i,"root",Component.literal("Node "+i),"item"+i));
        tree.setItems(items); tree.layout(new Rect(0,0,200,110),null); tree.setVisibleRegion(new Rect(0,0,200,110));
        check(tree.getVisibleRows()==501 && tree.getInstantiatedRows()<=16,"Tree virtualization");
        tree.setVisibleRegion(new Rect(0,4400,200,110)); check(tree.getInstantiatedRows()<=16,"Virtual rows stay bounded while scrolling");
        tree.setExpanded("root",false); check(tree.getVisibleRows()==1,"Collapse");
        tree.setSearch("Node 499"); check(tree.getVisibleRows()==2,"Search includes ancestry");
        rejects(() -> tree.setItems(List.of(items.get(0),items.get(0))));
        Tree<String> wideTree = new Tree<>();
        wideTree.setItems(List.of(new Tree.Item<>("wide", "", Component.literal("Long tree node ".repeat(12)), "wide")));
        Scroll treeScroll = new Scroll().setContent(wideTree);
        Window treeWindow = new Window(true).setContent(treeScroll);
        treeWindow.layoutViewport(160,100,null);
        check(treeScroll.getHorizontalVisible()&&treeScroll.getMaxScrollX()>0,"Tree measures full text width for horizontal scroll");
        treeScroll.setScroll(180,0);
        check(treeScroll.getScrollX()==180&&wideTree.getChildren().get(0).getBounds().width()==wideTree.getContentExtent().width(),"Virtual row retains complete content width");
        wideTree.setItems(List.of(new Tree.Item<>("wide", "", Component.literal("Short"), "wide")));
        treeWindow.layoutViewport(160,100,null);
        check(!treeScroll.getHorizontalVisible()&&treeScroll.getScrollX()==0,"Shorter tree content removes horizontal overflow");
        System.out.println("ORE_WINDOW_LAYOUT_PASS");
    }
    private static void check(boolean value, String text) { if(!value)throw new AssertionError(text); }
    private static void rejects(Runnable action) { try { action.run(); throw new AssertionError("Expected rejection"); } catch(IllegalArgumentException expected) { } }
}
