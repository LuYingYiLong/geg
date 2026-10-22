package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Rect;

import java.util.List;
import java.util.Map;
import io.github.luyingyilong.ore.signal.SignalDiagnostics;

/**
 * Immutable view data, deliberately containing no live Control references.
 */
public record InspectorSnapshot(long sequence, boolean waiting, String selected, boolean picking, String error,
                                boolean canUndo, boolean canRedo, List<Target> targets, List<Node> nodes, SignalPanel signals, io.github.luyingyilong.ore.control.base.LifecycleInfo lifecycle, List<io.github.luyingyilong.ore.control.base.LifecycleInfo> lifecycleHistory, io.github.luyingyilong.ore.debug.DebugThemes.Snapshot themes) {
    public static InspectorSnapshot empty() {
        return new InspectorSnapshot(0, true, "", false, "", false, false, List.of(), List.of(), SignalPanel.empty(), null, List.of(), io.github.luyingyilong.ore.debug.DebugThemes.Snapshot.empty());
    }

    public record SignalPanel(String node, String name, String state, SignalDiagnostics.Snapshot data, Map<Long, String> owners) {
        public static SignalPanel empty() {
            return new SignalPanel("", "", "empty", SignalDiagnostics.Snapshot.empty(), Map.of());
        }
    }

    public record Target(String id, String name, boolean nativeWindow, boolean active) {
    }

    public record Node(String id, String parent, String name, String className, boolean visible, boolean internal,
                       String hiddenReason, Rect bounds, Integer total, Integer instances, List<Property> properties) {
    }

    public record Property(int index, String name, String group, String kind, List<String> options,
                           Object base, Object effective, boolean overridden, boolean readonly, String source, String origin) {
    }
}
