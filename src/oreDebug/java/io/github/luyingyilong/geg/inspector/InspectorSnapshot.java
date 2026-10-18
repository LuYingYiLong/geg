package io.github.luyingyilong.geg.inspector;

import io.github.luyingyilong.geg.client.ui.layout.Rect;

import java.util.List;

/**
 * Immutable view data, deliberately containing no live Control references.
 */
public record InspectorSnapshot(long sequence, boolean waiting, String selected, boolean picking, String error,
                                boolean canUndo, boolean canRedo, List<Target> targets, List<Node> nodes) {
    public static InspectorSnapshot empty() {
        return new InspectorSnapshot(0, true, "", false, "", false, false, List.of(), List.of());
    }

    public record Target(String id, String name, boolean nativeWindow, boolean active) {
    }

    public record Node(String id, String parent, String name, String className, boolean visible, boolean internal,
                       String hiddenReason, Rect bounds, Integer total, Integer instances, List<Property> properties) {
    }

    public record Property(int index, String name, String group, String kind, List<String> options,
                           Object base, Object effective, boolean overridden, boolean readonly, String source) {
    }
}
