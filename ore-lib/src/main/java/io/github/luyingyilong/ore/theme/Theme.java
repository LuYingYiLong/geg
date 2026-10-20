package io.github.luyingyilong.ore.theme;
import io.github.luyingyilong.ore.signal.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Shared resource object; controls own subscriptions, never this object's lifetime. */
public final class Theme implements SignalOwner {
    private final SignalScope scope=new SignalScope();
    private final SignalSource<Boolean> changed=new SignalSource<>(this,"changed","Boolean");
    private final ThemeDB db;
    private final ResourceLocation id;
    private Map<String,Object> values=Map.of();
    private Theme parent;
    private Connection parentConnection;
    private Map<String,String> valueResources=Map.of();
    private String resource="programmatic";
    Theme(ThemeDB db, ResourceLocation id) { this.db=db; this.id=Objects.requireNonNull(id); }
    public SignalScope getSignalScope() { return scope; }
    public Signal<Boolean> changed() { return changed.signal(); }
    public ThemeDB getThemeDB() { return db; }
    public ResourceLocation getId() { return id; }
    public Theme getParent() { return parent; }
    public String getResource() { return resource; }
    public <T> Theme set(ThemeKey<T> key,T value) {
        scope.checkThread(); db.validateValue(key,value);
        if(Objects.equals(values.get(key.getId()),value)) return this;
        Map<String,Object> next=new LinkedHashMap<>(values); next.put(key.getId(),value); values=Map.copyOf(next);
        Map<String,String> sources=new LinkedHashMap<>(valueResources); sources.put(key.getId(),"programmatic"); valueResources=Map.copyOf(sources);
        publish(key.affectsLayout()); return this;
    }
    public Theme remove(ThemeKey<?> key) {
        scope.checkThread(); if(!values.containsKey(key.getId())) return this;
        Map<String,Object> next=new LinkedHashMap<>(values); next.remove(key.getId()); values=Map.copyOf(next);
        publish(key.affectsLayout()); return this;
    }
    public Theme setParent(Theme parent) {
        scope.checkThread(); if(parent==this.parent) return this;
        for(Theme p=parent;p!=null;p=p.parent) if(p==this) throw new IllegalArgumentException("Theme inheritance cycle");
        if(parent!=null && parent.db!=db) throw new IllegalArgumentException("Theme belongs to another database");
        linkParent(parent); publish(true); return this;
    }
    /** Resolves this resource's parent chain; Control additionally applies type and tree inheritance. */
    public <T> T get(ThemeKey<T> key) {
        scope.checkThread();
        for(Theme current=this;current!=null;current=current.parent) {
            Object value=current.values.get(key.getId());
            if(value!=null) return key.validate(value);
        }
        return null;
    }
    String getValueResource(String id) { return valueResources.getOrDefault(id,resource); }
    Object getValue(String id) { return values.get(id); }
    void replace(Map<String,Object> values, Theme parent, String resource) { this.values=Map.copyOf(values); linkParent(parent); this.resource=resource; Map<String,String> sources=new LinkedHashMap<>(); values.keySet().forEach(key->sources.put(key,resource)); valueResources=Map.copyOf(sources); }
    private void linkParent(Theme next) {
        if(parent==next) return;
        if(parentConnection!=null) parentConnection.disconnect();
        parent=next;
        parentConnection=next==null?null:next.changed().connect(this,layout -> {
            if(!db.isReloadNotification(this)) changed.emit(layout);
        });
    }
    void emitChanged(boolean layout) { changed.emit(layout); }
    void publish(boolean layout) { db.publish(layout); changed.emit(layout); }
    public String toString() { return id.toString(); }
}
