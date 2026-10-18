package io.github.luyingyilong.ore.util;

/** Lets a native widget retain MUI input handling while Ore owns its paint order. */
public final class NativePaintPass {
    private boolean active;

    public boolean active() { return active; }

    public boolean accepts(boolean treeOwned) { return !treeOwned || active; }

    public void paint(Runnable paint) {
        if (active) throw new IllegalStateException("Native paint pass is already active");
        active = true;
        try { paint.run(); }
        finally { active = false; }
    }
}
