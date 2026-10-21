package io.github.luyingyilong.ore.render;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.input.Dropdown;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.event.WindowInputs;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.window.CanvasRegion;
import io.github.luyingyilong.ore.window.RecipeCanvasFrames;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.List;

/** Exercises the real Window entrance across the external region transform boundary. */
public final class CanvasGeometryTest {
    public static void main(String[] args) throws Exception {
        transforms(); inputAndClipping(); popupEscapesRecipe(); ownershipAndCleanup(); retiredLayouts(); threadRejection();
        System.out.println("ORE_CANVAS_GEOMETRY_PASS");
    }

    private static void transforms() {
        var t = new CanvasTransform(30.5, 12.5, 2);
        check(t.toLocalX(t.toScreenX(17.25)) == 17.25, "Coordinate round trip");
        check(t.toScreen(new Rect(1, 2, 3, 4)).equals(new Rect(32, 16, 7, 9)), "Fractional coverage rounds outwards");
        for (double scale : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            fails(() -> new CanvasTransform(0, 0, scale));
        fails(() -> CanvasTransform.fromPose(new Matrix4f().rotateZ(0.3F)));
        fails(() -> CanvasTransform.fromPose(new Matrix4f().scale(1, 2, 1)));
    }

    private static void inputAndClipping() {
        int[] activations = {0};
        Button button = io.github.luyingyilong.ore.control.input.ButtonTestAccess.create(cue -> { });
        button.pressed().connect(button, () -> activations[0]++);
        button.setPosition(70, 40).setRequestedSize(40, 22);
        Control items = new Control().setPreferred(300, 200); items.addChild(button);
        Scroll scroll = new Scroll().setHorizontal(Scroll.BarMode.NEVER).setVertical(Scroll.BarMode.NEVER).setContent(items);
        CanvasRegion region = new CanvasRegion(); region.setContent(scroll);
        region.setViewport(new Rect(0, 0, 100, 60)); region.setTransform(new CanvasTransform(50, 30, 2));
        Control page = new Control(); page.addChild(region);
        Window root = Window.embedded().setContent(page); root.layoutViewport(400, 240, null);
        scroll.setScroll(50, 20);
        check(EventGeometry.displayed(button).equals(new Rect(90, 70, 80, 44)), "Scroll then region scale, each once");
        Rect point = EventGeometry.point(button, 96, 78);
        check(point.x() == 73 && point.y() == 44, "Pointer inverse matches local tree coordinates");
        check(WindowInputs.press(root, 96, 78, null), "Scaled control captures mouse");
        root.layoutViewport(400, 240, null);
        WindowInputs.release(root, 96, 78);
        check(activations[0] == 1, "Scaled release activates once across a layout pass");
        WindowInputs.press(root, 96, 78, null); WindowInputs.release(root, 300, 200);
        check(activations[0] == 1 && !root.getPointerCaptured(0), "Drag outside cancels captured action");
        region.setHostClip(new Rect(50, 30, 30, 120));
        check(!EventGeometry.contains(button, 96, 78), "Inherited viewer clip participates in release hit checks");
        check(region.hitTop(96, 78) == null, "Clipped widget cannot be picked");
    }

    private static void popupEscapesRecipe() {
        Dropdown<String> dropdown = new Dropdown<>("a", 4);
        dropdown.setOptions(List.of(new Dropdown.Option<>("a", Component.literal("A")), new Dropdown.Option<>("b", Component.literal("B"))));
        CanvasRegion region = new CanvasRegion(); region.setContent(dropdown);
        region.setViewport(new Rect(0, 0, 80, 20)); region.setTransform(new CanvasTransform(50, 30, 1));
        Control page = new Control(); page.addChild(region);
        Window root = Window.embedded().setContent(page); root.layoutViewport(300, 180, null);
        WindowInputs.press(root, 55, 35, null); WindowInputs.release(root, 55, 35);
        check(dropdown.isOpen(), "Recipe Dropdown opens on release");
        var popup = (io.github.luyingyilong.ore.window.WindowPopups.Popup) root.getPopupOverlay().getChild(0);
        root.layoutViewport(300, 180, null);
        check(popup.getPanelBounds().bottom() > region.getHostBounds().bottom(), "Popup is not clipped to recipe region");
        WindowInputs.press(root, 55, 80, null); WindowInputs.release(root, 55, 80);
        check(dropdown.getSelected().equals("b"), "Screen-space popup rows activate outside recipe bounds");
    }

    private static void ownershipAndCleanup() {
        Control content = new Control();
        Canvas first = new Canvas(content);
        try { new Canvas(content); throw new AssertionError("Shared content accepted"); }
        catch (IllegalStateException expected) { }
        check(first.getContent() == content && content.getParent() != null, "Rejected mount preserves original owner");
        fails(() -> first.setContent(Window.embedded()));
        check(first.getContent() == content, "A second Window cannot replace content or create a second host context");
        first.close(); first.close();
        check(content.isQueuedForDeletion(), "Canvas closes owned subtree immediately for input");
        LifecycleScheduler.flush();
        check(content.isDisposed(), "Permanent cleanup completes at safe boundary");
        try { first.setContent(new Control()); throw new AssertionError("Closed Canvas revived"); }
        catch (IllegalStateException expected) { }
    }

    private static void retiredLayouts() {
        Screen screen = new Screen(Component.empty()) { };
        class Layout implements RecipeCanvasFrames.Layout {
            int closes;
            @Override public Screen getScreen() { return screen; }
            @Override public void close() { closes++; RecipeCanvasFrames.forget(this); }
        }
        Layout one = new Layout(), two = new Layout();
        RecipeCanvasFrames.begin(screen); RecipeCanvasFrames.rendered(one); RecipeCanvasFrames.rendered(two); RecipeCanvasFrames.end(screen);
        check(one.closes == 0 && two.closes == 0, "Repeated frames retain both recipe instances");
        RecipeCanvasFrames.begin(screen); RecipeCanvasFrames.rendered(two); RecipeCanvasFrames.end(screen);
        check(one.closes == 1 && two.closes == 0, "Only replaced layout retires");
        RecipeCanvasFrames.clear(); check(two.closes == 1, "Session end closes retained layouts once");
    }

    private static void threadRejection() throws Exception {
        Canvas canvas = new Canvas(new Control());
        Throwable[] failure = {null};
        Thread worker = new Thread(() -> { try { canvas.close(); } catch (Throwable error) { failure[0] = error; } });
        worker.start(); worker.join();
        check(failure[0] instanceof IllegalStateException && !canvas.isClosed(), "Rejected close cannot partially close Canvas");
        failure[0] = null;
        worker = new Thread(() -> { try { canvas.setViewport(new Rect(0, 0, 80, 40)); } catch (Throwable error) { failure[0] = error; } });
        worker.start(); worker.join();
        check(failure[0] instanceof IllegalStateException && canvas.getViewport().width() == 1, "Rejected mutation preserves viewport");
        canvas.close(); LifecycleScheduler.flush();
    }

    private static void fails(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid transform accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
