package io.github.luyingyilong.ore.signal;

/** Pure Java guard shared by signal delivery and UI phases. Closing never runs cleanup. */
public final class DispatchGuard implements AutoCloseable {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private boolean closed;
    private DispatchGuard() { DEPTH.set(DEPTH.get() + 1); }
    public static DispatchGuard enter() { return new DispatchGuard(); }
    public static boolean active() { return DEPTH.get() != 0; }
    @Override public void close() { if (!closed) { closed = true; DEPTH.set(DEPTH.get() - 1); } }
}
