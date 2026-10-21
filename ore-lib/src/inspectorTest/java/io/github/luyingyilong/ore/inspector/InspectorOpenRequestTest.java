package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.event.InputEvent;
import io.github.luyingyilong.ore.event.KeyEvent;
import io.github.luyingyilong.ore.event.WindowInputs;
import io.github.luyingyilong.ore.window.Window;
import io.github.luyingyilong.ore.development.DevelopmentOpenRequest;

import io.github.luyingyilong.ore.debug.DebugBridge;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Simulates the synchronous key releases GLFW emits when showing/focusing a native Inspector. */
public final class InspectorOpenRequestTest {
    public static void main(String[] args) {
        releasesAfterDispatch(); canceledRequests(); failedRequests();
        System.out.println("ORE_INSPECTOR_OPEN_REQUEST_PASS");
    }

    private static void releasesAfterDispatch() {
        var tasks = new ArrayDeque<Runnable>();
        var calls = new ArrayList<String>();
        Window root = new Window(true);
        Control input = new Control() {
            @Override public void input(InputEvent event) {
                if(event instanceof KeyEvent key && key.action() == KeyEvent.Action.RELEASE) {
                    calls.add("release"); event.accept();
                }
            }
        }.setFocusable(true);
        root.setContent(input); root.layoutViewport(80, 30, null);
        root.getFocusManager().requestFocus(input, null);
        DevelopmentOpenRequest request = new DevelopmentOpenRequest(tasks::add, () -> {
            calls.add("open");
            WindowInputs.keyUp(root, GLFW.GLFW_KEY_F8, 0, null);
        });
        DebugBridge.setHostShortcut((key, scan) -> {
            if(key != GLFW.GLFW_KEY_F8) return false;
            calls.add("shortcut"); request.request(); return true;
        });
        try {
            check(WindowInputs.keyDown(root, GLFW.GLFW_KEY_F8, 0, null), "Shortcut is consumed immediately");
            WindowInputs.keyDown(root, GLFW.GLFW_KEY_F8, 0, null);
            check(tasks.size() == 1 && calls.equals(List.of("shortcut", "shortcut")), "Repeated callbacks enqueue one open without native work");
            tasks.remove().run();
            check(calls.equals(List.of("shortcut", "shortcut", "open", "release")), "Synthetic release runs after the original dispatch returns");
            request.request(); tasks.remove().run();
            check(calls.stream().filter("open"::equals).count() == 2, "An existing Inspector can be focused by a later request");
        } finally {
            DebugBridge.setHostShortcut(null); root.queueFree(); LifecycleScheduler.flush();
        }
        Window guarded = new Window(true); boolean[] rejected = {false};
        DebugBridge.setHostShortcut((key, scan) -> {
            try { WindowInputs.keyUp(guarded, key, 0, null); }
            catch(IllegalStateException expected) { rejected[0] = expected.getMessage().equals("Reentrant input dispatch"); }
            return true;
        });
        try {
            WindowInputs.keyDown(guarded, GLFW.GLFW_KEY_F8, 0, null);
            check(rejected[0], "Ordinary recursive dispatch remains rejected; only the native operation is deferred");
        } finally {
            DebugBridge.setHostShortcut(null); guarded.queueFree(); LifecycleScheduler.flush();
        }
    }

    private static void canceledRequests() {
        var tasks = new ArrayDeque<Runnable>(); int[] opens = {0};
        var request = new DevelopmentOpenRequest(tasks::add, () -> opens[0]++);
        request.request(); request.cancel(); request.request();
        tasks.remove().run(); check(opens[0] == 0, "A canceled task cannot consume or replace a newer request");
        tasks.remove().run(); check(opens[0] == 1, "The newer request still opens");
        request.request(); request.cancel(); tasks.remove().run();
        check(opens[0] == 1, "Stop/logout cancels a pending open even before an Inspector exists");
    }

    private static void failedRequests() {
        var tasks = new ArrayDeque<Runnable>(); int[] attempts = {0};
        var request = new DevelopmentOpenRequest(task -> {
            if(attempts[0]++ == 0) throw new IllegalStateException("Scheduler unavailable");
            tasks.add(task);
        }, () -> { });
        try { request.request(); throw new AssertionError("Expected scheduling failure"); }
        catch(IllegalStateException expected) { }
        request.request(); check(tasks.size() == 1, "A failed enqueue does not latch the pending flag"); tasks.remove().run();
        int[] opens = {0};
        var failing = new DevelopmentOpenRequest(tasks::add, () -> { opens[0]++; throw new IllegalStateException("Open failed"); });
        for(int attempt = 0; attempt < 2; attempt++) {
            failing.request();
            try { tasks.remove().run(); throw new AssertionError("Expected opening failure"); }
            catch(IllegalStateException expected) { }
        }
        check(opens[0] == 2, "An opening failure can be retried");
    }

    private static void check(boolean condition, String message) { if(!condition) throw new AssertionError(message); }
}
