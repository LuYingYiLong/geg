package io.github.luyingyilong.ore.layout;

/**
 * Read-only diagnostic data; does not retain a control or a window.
 */
public record LifecycleInfo(long id, String name, LifecycleState state, boolean insideTree,
                            boolean ready, boolean suspended, String reason, String requestSource) {
}
