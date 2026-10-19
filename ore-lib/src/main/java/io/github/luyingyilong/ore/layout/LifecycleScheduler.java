package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.signal.DispatchGuard;
import java.util.*;

/** Client-thread queue. Hosts flush only after ALL rendering (including MUI siblings) ends. */
public final class LifecycleScheduler {
    private static final ThreadLocal<Queues> LOCAL = ThreadLocal.withInitial(Queues::new);
    private static final System.Logger LOG = System.getLogger(LifecycleScheduler.class.getName());
    private static final class Queues {
        final ArrayDeque<Control> frees = new ArrayDeque<>();
        final ArrayDeque<Runnable> notices = new ArrayDeque<>();
        final Set<Control> resized = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean delivering, freeing;
    }
    private LifecycleScheduler() { }
    static void enqueue(Control root) {
        Queues q = LOCAL.get();
        q.frees.removeIf(root::queuedOwns);
        q.frees.add(root);
    }
    static void notify(Control node, ControlNotification.Type type) {
        ControlNotification snapshot = node.notificationSnapshot(type);
        LOCAL.get().notices.add(() -> node.deliverNotification(snapshot));
    }
    static void resized(Control node) {
        Queues q = LOCAL.get();
        if (q.resized.add(node)) q.notices.add(() -> {
            q.resized.remove(node);
            if (node.isAlive()) node.deliverNotification(node.notificationSnapshot(ControlNotification.Type.RESIZED));
        });
    }
    static void prepare(Control node) {
        LOCAL.get().notices.add(() -> node.readySubtree());
    }
    public static void notifications() {
        Queues q = LOCAL.get();
        if (q.delivering) return;
        q.delivering = true;
        try (var guard = DispatchGuard.enter()) {
            while (!q.notices.isEmpty()) q.notices.remove().run();
        } finally { q.delivering = false; }
    }
    /** Returns false when called during an event, signal, layout, drawing or notification. */
    public static boolean flush() {
        Queues q = LOCAL.get();
        if (DispatchGuard.active() || q.freeing || q.delivering) return false;
        q.freeing = true;
        try {
            notifications();
            while (!q.frees.isEmpty()) {
                Control node = q.frees.removeFirst();
                if (!node.isQueuedForDeletion()) continue;
                try (var guard = DispatchGuard.enter()) { node.freeNow(); }
                catch (RuntimeException error) { LOG.log(System.Logger.Level.ERROR, "Ore permanent cleanup failed", error); }
                notifications();
            }
            return true;
        } finally { q.freeing = false; }
    }
}
