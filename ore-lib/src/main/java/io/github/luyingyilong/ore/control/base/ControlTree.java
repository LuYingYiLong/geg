package io.github.luyingyilong.ore.control.base;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.signal.DispatchGuard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The only writer of ordinary parent/child links. All validation precedes commit. */
final class ControlTree {
    private ControlTree() { }

    private static final ThreadLocal<Transaction> TRANSACTION = new ThreadLocal<>();
    private static final class Transaction {
        final java.util.Map<Control, List<Control>> before = new java.util.LinkedHashMap<>();
        final List<Control> created = new ArrayList<>();
        final java.util.Map<Control, Object> states = new IdentityHashMap<>();
        final java.util.Map<Control, String> frees = new java.util.LinkedHashMap<>();
    }

    static void created(Control control) {
        Transaction transaction = TRANSACTION.get();
        if (transaction != null) transaction.created.add(control);
    }

    private static void snapshotState(Control node, Transaction transaction) {
        if (transaction.states.containsKey(node)) return;
        transaction.states.put(node, node.captureTreeState());
        for (Control child : node.ownedSnapshot()) snapshotState(child, transaction);
    }

    static boolean deferFree(Control control, String reason) {
        Transaction transaction = TRANSACTION.get();
        if (transaction == null) return false;
        transaction.frees.putIfAbsent(control, reason);
        return true;
    }

    static void transact(Control anchor, Runnable action) {
        if (TRANSACTION.get() != null) { action.run(); return; }
        Transaction transaction = new Transaction();
        snapshotState(anchor, transaction);
        TRANSACTION.set(transaction);
        boolean success = false;
        try (var guard = DispatchGuard.enter()) {
            try {
                action.run();
                success = true;
            } finally {
                TRANSACTION.remove();
                if (!success) {
                    for (Control parent : transaction.before.keySet())
                        for (Control child : parent.children) if (child.parent == parent) child.parent = null;
                    transaction.before.forEach((parent, original) -> {
                        parent.children.clear();
                        parent.children.addAll(original);
                        for (Control child : original) child.parent = parent;
                    });
                    transaction.states.forEach(Control::restoreTreeState);
                    for (Control parent : transaction.before.keySet()) {
                        ControlIds.refresh(parent);
                        parent.syncChildrenChanged();
                        parent.requestLayout();
                    }
                    for (Control created : transaction.created)
                        if (created.parent == null && created.lifecycleOwner == null) created.queueFree("Rolled back composition candidate");
                } else {
                    java.util.Map<Control, Control> previousParents = new IdentityHashMap<>();
                    transaction.before.forEach((parent, original) -> original.forEach(child -> previousParents.put(child, parent)));
                    Set<Control> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
                    candidates.addAll(previousParents.keySet());
                    transaction.before.keySet().forEach(parent -> candidates.addAll(parent.children));
                    for (Control child : candidates) {
                        Control previous = previousParents.get(child);
                        if (previous != child.parent && previous != null) {
                            previous.childDetached(child);
                            if (child.parent == null || previous.getWindowContext() != child.parent.getWindowContext())
                                DebugBridge.backend().removed(child);
                        }
                    }
                    for (Control child : candidates)
                        if (previousParents.get(child) != child.parent && child.parent != null && child.isAlive())
                            child.parent.childAttached(child);
                }
            }
        }
        if (success) transaction.frees.forEach(Control::queueFree);
    }


    static void add(Control parent, Control child) {
        Objects.requireNonNull(child, "child");
        if (child.parent != null) throw new IllegalStateException("Control already has a parent");
        List<Control> next = new ArrayList<>(parent.children);
        next.add(child);
        commit(parent, next, false, false);
    }

    static boolean remove(Control parent, Control child) {
        parent.getSignalScope().checkThread();
        if (child == null || child.parent != parent) return false;
        List<Control> next = new ArrayList<>(parent.children);
        next.removeIf(node -> node == child);
        commit(parent, next, false, false);
        return true;
    }

    static void move(Control parent, Control child, int index) {
        parent.getSignalScope().checkThread();
        if (child == null || child.parent != parent) throw new IllegalArgumentException("Not a direct child");
        if (index < 0 || index >= parent.children.size()) throw new IndexOutOfBoundsException(index);
        List<Control> next = new ArrayList<>(parent.children);
        next.removeIf(node -> node == child);
        next.add(index, child);
        if (!parent.isAlive()) throw new IllegalStateException("Cannot reorder a pending/freed control");
        commit(parent, next, false, false);
    }

    static void replace(Control parent, Control old, Control next) {
        Objects.requireNonNull(next, "content");
        if (old == next) return;
        List<Control> proposed = new ArrayList<>(parent.children);
        int index = old == null ? -1 : proposed.indexOf(old);
        if (index < 0) proposed.add(next); else proposed.set(index, next);
        commit(parent, proposed, false, false);
    }

    static void update(Control parent, List<Control> proposed) {
        commit(parent, proposed, true, false);
    }

    static void detachForFree(Control child) {
        if (child.parent == null) return;
        Control parent = child.parent;
        List<Control> next = new ArrayList<>(parent.children);
        next.removeIf(node -> node == child);
        commit(parent, next, true, true);
    }

    static void clearForFree(Control parent) {
        if (!parent.children.isEmpty()) commit(parent, List.of(), true, true);
    }

    static void requireAcyclic(Control parent, Control child) {
        Set<Control> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Control> pending = new ArrayDeque<>();
        pending.add(child);
        while (!pending.isEmpty()) {
            Control node = pending.removeFirst();
            if (node == parent) throw new IllegalArgumentException("Control ownership cycle");
            if (visited.add(node)) pending.addAll(node.ownedSnapshot());
        }
    }

    static void validateAttachment(Control parent, Control child) {
        parent.getSignalScope().checkThread();
        child.getSignalScope().checkThread();
        if (!parent.isAlive() || !child.isAlive()) throw new IllegalStateException("Cannot attach a pending/freed control");
        if (child.parent != null) throw new IllegalStateException("Control already has a parent");
        requireAcyclic(parent, child);
        child.validateThemeAttachment(parent);
        child.validateTransformAttachment(parent);
        if (child.lifecycleOwner != null) {
            boolean allowed = false;
            for (Control node = parent; node != null; node = node.parent)
                if (node == child.lifecycleOwner) allowed = true;
            if (!allowed) throw new IllegalStateException("Control belongs to another lifecycle owner");
        }
    }

    private static void commit(Control parent, List<Control> proposed, boolean internal, boolean freeing) {
        parent.getSignalScope().checkThread();
        if (parent.validatingChildren) throw new IllegalStateException("Cannot modify the tree during validation");
        List<Control> next = List.copyOf(proposed);
        if (!freeing && !parent.isAlive()
                && (parent.isDisposed() || next.stream().anyMatch(child -> child.parent != parent)))
            throw new IllegalStateException("Cannot attach to a pending/freed control");
        Set<Control> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Control child : next) {
            child.getSignalScope().checkThread();
            if (!seen.add(child)) throw new IllegalArgumentException("Duplicate child");
            if (child.parent == parent) continue;
            validateAttachment(parent, child);
        }
        if (!freeing) {
            parent.validatingChildren = true;
            parent.updatingChildren = internal;
            try { parent.validateChildren(next); }
            finally { parent.updatingChildren = false; parent.validatingChildren = false; }
        }
        ControlIds.validateStructure(parent, next);
        List<Control> previous = List.copyOf(parent.children);
        if (previous.equals(next)) return;
        List<Control> removed = previous.stream().filter(node -> !seen.contains(node)).toList();
        List<Control> added = next.stream().filter(node -> node.parent != parent).toList();
        java.util.Map<Control,java.util.List<io.github.luyingyilong.ore.theme.Theme>> oldThemes=new java.util.IdentityHashMap<>();
        for(Control child:removed) oldThemes.put(child,child.inheritedThemes());
        for(Control child:added) oldThemes.put(child,child.inheritedThemes());
        try (var phase = DispatchGuard.enter()) {
            // Publish the whole structure and its indexes before callbacks can observe it.
            Transaction transaction = TRANSACTION.get();
            if (transaction != null) transaction.before.putIfAbsent(parent, previous);
            parent.children.clear();
            parent.children.addAll(next);
            for (Control child : removed) child.parent = null;
            for (Control child : added) child.parent = parent;
            ControlIds.refresh(parent);
            for (Control child : removed) ControlIds.refresh(child);
            oldThemes.forEach(Control::refreshThemeInheritance);
            for (Control node = parent; node != null; node = node.parent) node.syncChildrenChanged();
            parent.requestLayout();
            if (transaction != null) return;
            for (Control child : removed) {
                parent.childDetached(child);
                DebugBridge.backend().removed(child);
            }
            for (Control child : added) if (child.parent == parent && child.isAlive()) parent.childAttached(child);
        }
    }
}
