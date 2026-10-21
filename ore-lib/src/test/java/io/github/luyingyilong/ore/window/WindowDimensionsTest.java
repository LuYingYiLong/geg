package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.support.InventoryTestSupport;
import net.minecraft.client.gui.GuiGraphics;

/** Transient host geometry is skipped atomically; explicit invalid configuration still fails. */
public final class WindowDimensionsTest {
    public static void main(String[] args) {
        WindowContext context = new WindowContext(true);
        context.setDimensions(1000, 600, 2000, 1200, 4);
        for (double[] invalid : new double[][]{
                {0, 600, 2000, 1200, 4}, {1000, 0, 2000, 1200, 4},
                {1000, 600, 0, 1200, 4}, {1000, 600, 2000, 0, 4},
                {-1, 600, 2000, 1200, 4}, {1000, 600, -1, 1200, 4},
                {1000, 600, 2000, 1200, 0}, {1000, 600, 2000, 1200, -1},
                {1000, 600, 2000, 1200, Double.NaN}, {1000, 600, 2000, 1200, Double.POSITIVE_INFINITY}}) {
            check(!context.trySetDimensions((int) invalid[0], (int) invalid[1], (int) invalid[2], (int) invalid[3], invalid[4]),
                    "An undrawable host snapshot is rejected without throwing");
            try {
                context.setDimensions((int) invalid[0], (int) invalid[1], (int) invalid[2], (int) invalid[3], invalid[4]);
                throw new AssertionError("Explicit invalid dimensions must still fail");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("client=") && expected.getMessage().contains("scale="),
                        "Dimension errors identify the rejected values");
            }
            context.setMouse(200, 100);
            check(context.getGuiWidth() == 500 && context.getGuiHeight() == 300 && context.getGuiScale() == 4
                            && context.getFramebufferWidth() == 2000 && context.getFramebufferHeight() == 1200,
                    "A skipped frame preserves the last valid dimensions");
            check(context.getMouseX() == 100 && context.getMouseY() == 50 && context.clientX(100) == 200 && context.clientY(50) == 100,
                    "A skipped frame cannot corrupt pointer or IME coordinate conversion");
        }
        check(context.trySetDimensions(1001, 603, 1001, 603, 3), "A restored host accepts new dimensions");
        check(context.getGuiWidth() == 334 && context.getGuiHeight() == 201, "Restored odd dimensions retain pixel coverage");
        context.setMouse(303, 153);
        check(context.getMouseX() == 101 && context.getMouseY() == 51 && context.clientX(101) == 303,
                "Pointer conversion uses the restored dimensions");
        emptyEmbeddedViewport();
        System.out.println("ORE_WINDOW_DIMENSIONS_PASS");
    }

    private static void emptyEmbeddedViewport() {
        InventoryTestSupport.initializeRegistries();
        Window window = Window.embedded().setContent(new Control());
        GuiGraphics graphics = new GuiGraphics(null, null) {
            @Override public void flush() { throw new AssertionError("An empty viewport must not touch drawing state"); }
        };
        DebugBridge.Backend previous = DebugBridge.backend();
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public void paint(Control node, DrawContext frame, int x, int y) {
                throw new AssertionError("An empty viewport must not dispatch a paint");
            }
        });
        try {
            for (Rect viewport : new Rect[]{new Rect(0, 0, 0, 100), new Rect(0, 0, 100, 0), new Rect(0, 0, 0, 0)})
                try (var frame = new DrawContext(graphics, InventoryTestSupport.FONT, window.getContext(), viewport, 0, 0, 0)) {
                    window.draw(frame, 0, 0);
                    check(window.isAlive(), "Skipping a frame does not destroy its Window");
                }
        } finally {
            DebugBridge.install(previous);
            window.queueFree(); LifecycleScheduler.flush();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
