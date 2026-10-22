package io.github.luyingyilong.ore.control.base;

import java.util.*;

/** Identity indexes mirror ordinary children; owned parts and pools never enter them. */
final class ControlIds {
    private record Scope(Control root, boolean childrenOnly) { }
    private ControlIds() { }
    private static Scope scope(Control node, boolean children) {
        if (children && node.idChildrenScopeRoot) return new Scope(node, true);
        while (!node.idScopeRoot && node.parent != null) {
            if (node.parent.idChildrenScopeRoot) return new Scope(node.parent, true);
            node = node.parent;
        }
        return new Scope(node, false);
    }
    static Control find(Control caller, String id) {
        if (caller.idChildrenScopeRoot && caller.id.equals(id)) return caller;
        Scope scope = scope(caller, true);
        return (scope.childrenOnly ? scope.root.nestedIdIndex : scope.root.idIndex).get(id);
    }
    static void validateRename(Control node, String id) {
        validate(scope(node, false), null, null, node, id);
    }
    static void validateStructure(Control parent, List<Control> next) {
        validate(scope(parent, true), parent, next, null, null);
    }
    private static void validate(Scope scope, Control changed, List<Control> next, Control renamed, String newId) {
        Map<String, Control> ids = new LinkedHashMap<>();
        if (!scope.childrenOnly) collect(scope.root, changed, next, renamed, newId, ids);
        else for (Control child : scope.root == changed ? next : List.copyOf(scope.root.children))
            collect(child, changed, next, renamed, newId, child.idScopeRoot ? new LinkedHashMap<>() : ids, !internal(scope.root,child));
    }
    private static boolean internal(Control parent, Control child) { return parent.getOwnedParts().containsValue(child); }
    private static void collect(Control node, Control changed, List<Control> next,
                                Control renamed, String newId, Map<String, Control> ids) {
        collect(node,changed,next,renamed,newId,ids,true);
    }
    private static void collect(Control node, Control changed, List<Control> next,
                                Control renamed, String newId, Map<String, Control> ids, boolean includeSelf) {
        if (!node.isAlive()) return;
        String key = node == renamed ? newId : node.id;
        if (includeSelf && !key.isEmpty() && ids.putIfAbsent(key, node) != null) throw new IllegalArgumentException("Duplicate control ID: " + key);
        Map<String, Control> childIds = node.idChildrenScopeRoot ? new LinkedHashMap<>() : ids;
        for (Control child : node == changed ? next : List.copyOf(node.children))
            collect(child, changed, next, renamed, newId, child.idScopeRoot ? new LinkedHashMap<>() : childIds, !internal(node,child));
    }
    static void refresh(Control node) {
        while (!node.idScopeRoot && node.parent != null) node = node.parent;
        Map<String, Control> index = new LinkedHashMap<>();
        rebuild(node, index);
        node.idIndex = Map.copyOf(index);
    }
    private static void rebuild(Control node, Map<String, Control> index) {
        rebuild(node,index,true);
    }
    private static void rebuild(Control node, Map<String, Control> index, boolean includeSelf) {
        if (!node.isAlive()) return;
        if (includeSelf && !node.id.isEmpty()) index.put(node.id, node);
        Map<String, Control> childIndex = node.idChildrenScopeRoot ? new LinkedHashMap<>() : index;
        for (Control child : List.copyOf(node.children)) {
            if (child.idScopeRoot) {
                Map<String, Control> inner = new LinkedHashMap<>(); rebuild(child, inner, !internal(node,child)); child.idIndex = Map.copyOf(inner);
            } else rebuild(child, childIndex, !internal(node,child));
        }
        if (node.idChildrenScopeRoot) node.nestedIdIndex = Map.copyOf(childIndex);
    }
}
