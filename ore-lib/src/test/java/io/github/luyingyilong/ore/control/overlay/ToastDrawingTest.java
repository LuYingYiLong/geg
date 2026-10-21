package io.github.luyingyilong.ore.control.overlay;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.overlay.toast.ToastQueue;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.signal.DispatchGuard;
import io.github.luyingyilong.ore.support.InventoryTestSupport;
import net.minecraft.network.chat.Component;

/** Lifecycle checks use the actual public draw entry, without requiring a GL context. */
public final class ToastDrawingTest {
    public static void main(String[] args) {
        publicBoundary();
        invalidatedFrame();
        freeDuringDrawing();
        hiddenAndFinished();
        System.out.println("ORE_TOAST_DRAWING_PASS");
    }

    private static void publicBoundary() {
        for (Class<?> type : new Class<?>[]{Label.class, Toast.class})
            for (var method : type.getMethods())
                check(!method.getName().equals("drawGui"), "Raw GUI drawing must not escape the Control pipeline");
    }

    private static void invalidatedFrame() {
        var queue = new ToastQueue();
        var toast = toast(() -> 1000L);
        queue.show("message", toast);
        var frames = queue.layout(InventoryTestSupport.FONT, 128, 100);
        Control label = toast.getChild(0);
        queue.clear();
        check(toast.isQueuedForDeletion() && label.isQueuedForDeletion(), "Queue removal marks the entire owned subtree");
        // A captured frame may outlive the queue entry until the end of this render round.
        // No graphics/context access is permitted after queueFree, even before flush.
        frames.get(0).toast().draw(null, 0, 0);
        label.draw(null, 0, 0);
        check(LifecycleScheduler.flush() && toast.isDisposed() && label.isDisposed(), "Cleanup releases toast and its retained Label");
        toast.draw(null, 0, 0);
        label.draw(null, 0, 0);
    }

    private static void freeDuringDrawing() {
        Toast toast = toast(() -> 1000L).open();
        toast.layout(new Rect(3, 4, 100, 24), InventoryTestSupport.FONT);
        Control label = toast.getChild(0);
        var previous = DebugBridge.backend();
        int[] paints = {0};
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public void paint(Control node, DrawContext context, int x, int y) {
                check(node == toast, "Queued children must not receive a later paint in this round");
                paints[0]++;
                check(DispatchGuard.active() && !LifecycleScheduler.flush(), "Drawing cannot flush permanent cleanup");
                toast.queueFree();
                check(!LifecycleScheduler.flush() && !label.isDisposed(), "Resources survive until the safe frame boundary");
            }
        });
        try {
            toast.draw(null, 0, 0);
            toast.draw(null, 0, 0);
            check(paints[0] == 1, "Pending toast skips subsequent drawing immediately");
            check(!DispatchGuard.active(), "Drawing releases its lifecycle guard");
        } finally {
            DebugBridge.install(previous);
            toast.queueFree();
            LifecycleScheduler.flush();
        }
        check(toast.isDisposed() && label.isDisposed(), "Drawing-time request frees all owned parts once");
    }

    private static void hiddenAndFinished() {
        long[] now = {0};
        Toast toast = toast(() -> now[0]).setDuration(1).open();
        toast.layout(new Rect(0, 0, 60, 20), InventoryTestSupport.FONT);
        toast.setVisible(false);
        toast.draw(null, 0, 0);
        toast.setVisible(true);
        now[0] = Toast.ENTER_DURATION + 1 + Toast.EXIT_DURATION;
        check(toast.getFinished(), "Toast reached the end of its exit animation");
        toast.draw(null, 0, 0);
        toast.queueFree();
        LifecycleScheduler.flush();
    }

    private static Toast toast(java.util.function.LongSupplier clock) {
        return new Toast(clock, cue -> { }).setText(Component.literal("Message")).setDuration(0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
