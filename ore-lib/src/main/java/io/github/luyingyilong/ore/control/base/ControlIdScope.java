package io.github.luyingyilong.ore.control.base;

import java.util.Objects;

/** Factory boundary: the root belongs to its caller, its Java-owned descendants have a private ID scope. */
public final class ControlIdScope {
    private ControlIdScope() { }
    public static void isolateChildren(Control root) {
        Objects.requireNonNull(root).getSignalScope().checkThread();
        if (root.getParent() != null || !root.isAlive()) throw new IllegalStateException("Establish factory scope before mounting");
        root.idChildrenScopeRoot = true;
        ControlIds.refresh(root);
    }
}
