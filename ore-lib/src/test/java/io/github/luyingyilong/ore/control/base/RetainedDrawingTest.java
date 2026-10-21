package io.github.luyingyilong.ore.control.base;

import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.layout.Divider;
import io.github.luyingyilong.ore.control.layout.Panel;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.Transform2D;
import io.github.luyingyilong.ore.signal.DispatchGuard;
import io.github.luyingyilong.ore.support.InventoryTestSupport;
import io.github.luyingyilong.ore.theme.*;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Exercises real Control dispatch, geometry and primitives without a GL driver. */
public final class RetainedDrawingTest {
    private static final StyleBoxFlat FACE = new StyleBoxFlat(0xFFFFFFFF, ThemeInsets.ZERO, 0, ThemeInsets.ZERO, ThemeInsets.ZERO);

    private static final class Graphics extends GuiGraphics {
        final List<Rect> fills = new ArrayList<>();
        Graphics() { super(null, null); }
        @Override public void flush() { }
        @Override public void fill(int left, int top, int right, int bottom, int color) {
            check(DispatchGuard.active() && !LifecycleScheduler.flush(), "Primitive paint must retain the draw guard");
            fills.add(DrawContext.toScreenRect(pose().last().pose(), new Rect(left, top, right - left, bottom - top)));
        }
    }

    public static void main(String[] args) {
        InventoryTestSupport.initializeRegistries();
        Graphics graphics = new Graphics();
        Window window = Window.embedded();
        Label label = new Label(Component.literal("Label"));
        Panel panel = new Panel().setChild(label);
        window.setContent(panel);
        panel.setThemeOverride(OreTheme.PANEL, FACE);
        panel.setOffset(new Offset(2, 3));
        panel.layout(new Rect(10, 15, 60, 30), InventoryTestSupport.FONT);
        panel.setRenderTransform(new Transform2D(3, 4, 45, 1.5, .5, .5));
        label.setRenderTransform(new Transform2D(4, 1, 0, 1, .5, .5));
        List<Control> paints = new ArrayList<>();
        List<Rect> labels = new ArrayList<>();
        var previous = DebugBridge.backend();
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public void paint(Control node, DrawContext context, int x, int y) {
                check(DispatchGuard.active() && !LifecycleScheduler.flush(), "Every retained part enters the protected draw phase");
                paints.add(node);
                if (node == label) labels.add(context.toScreenRect(label.getScreenBounds(x, y)));
            }
        });
        try (var context = new DrawContext(graphics, InventoryTestSupport.FONT, window.getContext(), new Rect(0, 0, 140, 100), 0, 0, 0)) {
            Matrix4f pose = new Matrix4f(graphics.pose().last().pose());
            panel.draw(context, 0, 0);
            Rect expectedPanel = panel.getRenderTransform().toAffine(panel.getAbsoluteBounds()).bounds(panel.getAbsoluteBounds());
            Rect expectedLabel = panel.getRenderTransform().toAffine(panel.getAbsoluteBounds())
                    .multiply(label.getRenderTransform().toAffine(label.getAbsoluteBounds())).bounds(label.getAbsoluteBounds());
            check(paints.equals(List.of(panel, label)), "Panel and Label draw once in real child order");
            check(graphics.fills.equals(List.of(expectedPanel)) && labels.equals(List.of(expectedLabel)), "Offset and composed transforms reach background and Label once");
            check(pose.equals(graphics.pose().last().pose()) && !DispatchGuard.active(), "Drawing restores geometry and lifecycle state");

            paints.clear(); graphics.fills.clear(); label.setVisible(false);
            panel.draw(context, 0, 0);
            check(paints.equals(List.of(panel)) && graphics.fills.size() == 1, "Hidden Label skips its own draw while the Panel remains visible");
            panel.setVisible(false); paints.clear(); graphics.fills.clear();
            panel.draw(context, 0, 0);
            check(paints.isEmpty() && graphics.fills.isEmpty(), "Hidden Panel suppresses its entire branch");
            panel.setVisible(true); label.setVisible(true);

            RuntimeException sentinel = new RuntimeException("expected retained drawing failure");
            DebugBridge.install(new DebugBridge.Backend() {
                @Override public void paint(Control node, DrawContext frame, int x, int y) {
                    if (node == label) throw sentinel;
                }
            });
            try { panel.draw(context, 0, 0); throw new AssertionError("Expected child failure"); }
            catch (RuntimeException failure) { check(failure == sentinel, "Original failure propagates"); }
            check(pose.equals(graphics.pose().last().pose()) && !DispatchGuard.active(), "Failed child painting restores all transform scopes");

            DebugBridge.install(new DebugBridge.Backend() {
                @Override public void paint(Control node, DrawContext frame, int x, int y) {
                    check(node == panel, "Queued descendants do not paint later in this traversal");
                    panel.queueFree();
                    check(!LifecycleScheduler.flush(), "Permanent cleanup waits for drawing to end");
                }
            });
            graphics.fills.clear(); panel.draw(context, 0, 0);
            check(graphics.fills.isEmpty(), "Drawing-time queueFree prevents the Panel face from painting");
            panel.draw(null, 0, 0); label.draw(null, 0, 0);
            check(LifecycleScheduler.flush() && panel.isDisposed() && label.isDisposed(), "Pending controls stop painting immediately and release together at the safe point");
            panel.draw(null, 0, 0); label.draw(null, 0, 0);
            DebugBridge.install(null);
            divider(context, graphics);
        } finally {
            DebugBridge.install(previous);
            window.queueFree();
            LifecycleScheduler.flush();
        }
        System.out.println("ORE_RETAINED_DRAWING_PASS");
    }

    private static void divider(DrawContext context, Graphics graphics) {
        Divider divider = new Divider();
        divider.setThemeOverride(OreTheme.H_DIVIDER, FACE);
        divider.setThemeOverride(OreTheme.V_DIVIDER, FACE);
        divider.layout(new Rect(0, 0, 20, 10), InventoryTestSupport.FONT);
        try {
            graphics.fills.clear(); divider.draw(context, 0, 0);
            check(graphics.fills.equals(List.of(new Rect(0, 4, 20, 2))), "Horizontal background retains its centered two-pixel height");
            divider.setLayout(Divider.Layout.VERTICAL);
            graphics.fills.clear(); divider.draw(context, 0, 0);
            check(graphics.fills.equals(List.of(new Rect(9, 0, 1, 10))), "Vertical background retains its centered one-pixel width");
            check(divider.getOwnedParts().isEmpty(), "Primitive backgrounds allocate no painter Controls");
        } finally { divider.queueFree(); LifecycleScheduler.flush(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
