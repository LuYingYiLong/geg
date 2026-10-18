package io.github.luyingyilong.geg.inspector;

import io.github.luyingyilong.geg.client.ui.layout.Control;
import java.util.*;

/** Session-only overrides. Application setters keep their latest base values untouched. */
public final class DebugOverrides {
    private Map<Control,Map<String,Object>> values=new IdentityHashMap<>();
    private final Deque<Map<Control,Map<String,Object>>> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public Object value(Control c,String key,Object base) { return values.getOrDefault(c,Map.of()).getOrDefault(key,base); }
    public boolean contains(Control c,String key) { return values.getOrDefault(c,Map.of()).containsKey(key); }
    private Map<Control,Map<String,Object>> copy() {
        Map<Control,Map<String,Object>> next=new IdentityHashMap<>();values.forEach((c,v)->next.put(c,new HashMap<>(v)));return next;
    }
    private void checkpoint() { undo.push(copy());if(undo.size()>100)undo.removeLast();redo.clear(); }
    public void set(Control c,String key,Object value) {
        if (value == null ? !contains(c,key) : contains(c,key) && Objects.equals(values.get(c).get(key),value)) return;
        checkpoint();if(value==null){Map<String,Object> map=values.get(c);if(map!=null)map.remove(key);}
        else values.computeIfAbsent(c,k->new HashMap<>()).put(key,value);
    }
    public void reset(Set<Control> controls) { checkpoint();controls.forEach(values::remove); }
    public void resetAll() { checkpoint();values.clear(); }
    public boolean canUndo() { return !undo.isEmpty(); } public boolean canRedo() { return !redo.isEmpty(); }
    public void undo() { if(!undo.isEmpty()){redo.push(copy());values=undo.pop();} }
    public void redo() { if(!redo.isEmpty()){undo.push(copy());values=redo.pop();} }
    public void retain(Set<Control> live) {
        values.keySet().retainAll(live);
        undo.forEach(m->m.keySet().retainAll(live));redo.forEach(m->m.keySet().retainAll(live));
    }
    public void forget(Set<Control> removed) {
        removed.forEach(values::remove);undo.forEach(m->removed.forEach(m::remove));redo.forEach(m->removed.forEach(m::remove));
    }
    public void removeTree(Control root) {
        Set<Control> removed=Collections.newSetFromMap(new IdentityHashMap<>());collect(root,removed);
        removed.forEach(values::remove);undo.forEach(m->removed.forEach(m::remove));redo.forEach(m->removed.forEach(m::remove));
    }
    private void collect(Control c,Set<Control> nodes) {
        nodes.add(c);InspectorProperties.properties(c).forEach(p->nodes.add(p.target()));
        c.children().forEach(child->collect(child,nodes));
    }
    public void clear() { values.clear();undo.clear();redo.clear(); }
}
