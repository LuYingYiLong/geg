package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleInfo;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.LifecycleState;

import io.github.luyingyilong.ore.debug.DebugBridge;

public final class InspectorLifecycleTest {
    public static void main(String[] args) {
        InspectorLifecycles history = new InspectorLifecycles();
        DebugBridge.install(new DebugBridge.Backend() {
            @Override public void lifecycle(Control control, LifecycleInfo info) { history.record(control, info); }
        });
        try {
            Control root = new Control().setDebugName("root"), child = new Control().setDebugName("child"), part = new Control().setDebugName("part");
            root.addChild(child); root.ownPart("label", part); root.queueFree("host ended"); LifecycleScheduler.flush();
            var list = history.snapshot();
            check(list.size() == 3 && list.stream().allMatch(i -> i.state() == LifecycleState.FREED), "Final cleanup records for entire ownership tree");
            check(list.stream().allMatch(i -> i.requestSource().contains("InspectorLifecycleTest.java")), "First request source preserved through descendants");
            check(list.stream().anyMatch(i -> i.reason().startsWith("parent cleanup")) && list.stream().anyMatch(i -> i.reason().startsWith("owned part cleanup")), "Parent and internal ownership have distinct reasons");
            for (int index = 0; index < 600; index++) new Control().queueFree();
            LifecycleScheduler.flush(); check(history.snapshot().size() == 512, "Tombstone cap");
            history.clear(); check(history.snapshot().isEmpty(), "Session stop clears history");
            System.out.println("ORE_INSPECTOR_LIFECYCLE_PASS");
        } finally { DebugBridge.install(null); }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
