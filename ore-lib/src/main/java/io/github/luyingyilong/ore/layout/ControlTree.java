package io.github.luyingyilong.ore.layout;

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
            if (child.parent != null) throw new IllegalStateException("Control already has a parent");
            if (!child.isAlive()) throw new IllegalStateException("Cannot attach a pending/freed control");
            requireAcyclic(parent, child);
            child.validateThemeAttachment(parent);
            if (child.lifecycleOwner != null) {
                boolean allowed = false;
                for (Control node = parent; node != null; node = node.parent)
                    if (node == child.lifecycleOwner) allowed = true;
                if (!allowed) throw new IllegalStateException("Control belongs to another lifecycle owner");
            }
        }
        if (!freeing) {
            parent.validatingChildren = true;
            parent.updatingChildren = internal;
            try { parent.validateChildren(next); }
            finally { parent.updatingChildren = false; parent.validatingChildren = false; }
        }
        List<Control> previous = List.copyOf(parent.children);
        if (previous.equals(next)) return;
        List<Control> removed = previous.stream().filter(node -> !seen.contains(node)).toList();
        List<Control> added = next.stream().filter(node -> node.parent != parent).toList();
        java.util.Map<Control,java.util.List<io.github.luyingyilong.ore.theme.Theme>> oldThemes=new java.util.IdentityHashMap<>();
        for(Control child:removed) oldThemes.put(child,child.inheritedThemes());
        for(Control child:added) oldThemes.put(child,child.inheritedThemes());
        try (var phase = DispatchGuard.enter()) {
            // Publish the whole structure and its indexes before callbacks can observe it.
            parent.children.clear();
            parent.children.addAll(next);
            for (Control child : removed) child.parent = null;
            for (Control child : added) child.parent = parent;
            oldThemes.forEach(Control::refreshThemeInheritance);
            for (Control node = parent; node != null; node = node.parent) node.syncChildrenChanged();
            parent.requestLayout();
            for (Control child : removed) {
                parent.childDetached(child);
                DebugBridge.backend().removed(child);
            }
            for (Control child : added) if (child.parent == parent && child.isAlive()) parent.childAttached(child);
        }
    }
}
