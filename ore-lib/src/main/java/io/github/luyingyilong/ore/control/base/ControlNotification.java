package io.github.luyingyilong.ore.control.base;

import io.github.luyingyilong.ore.window.WindowContext;

/**
 * Immutable state at the transition, even if a callback reparents the control.
 */
public record ControlNotification(Type type, WindowContext window, boolean insideTree,
                                  boolean ready, boolean effectiveVisible, long treeEpoch) {
    public enum Type {ENTER_TREE, READY, EXIT_TREE, PRE_FREE, RESIZED, VISIBILITY_CHANGED, SUSPENDED, RESUMED, THEME_CHANGED}
}
