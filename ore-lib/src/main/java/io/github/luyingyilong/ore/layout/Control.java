package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.Transform2D;
import io.github.luyingyilong.ore.theme.*;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.event.CancelEvent;
import io.github.luyingyilong.ore.event.InputEvent;
import io.github.luyingyilong.ore.event.PointerEvent;
import io.github.luyingyilong.ore.event.ScrollEvent;
import io.github.luyingyilong.ore.signal.*;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A local-coordinate layout node. Window owns keyboard focus; ModularUI edits text.
 */
public class Control implements SignalOwner {
    private final SignalScope signalScope = new SignalScope();
    private Theme theme;
    private String themeTypeVariation = "";
    private String resolvedThemeVariation = "";
    private java.lang.ref.WeakReference<Control> themeParent;
    private final java.util.List<java.lang.ref.WeakReference<Control>> themeDependents = new java.util.ArrayList<>();
    private final java.util.Map<ThemeKey<?>,Object> themeOverrides = new java.util.LinkedHashMap<>();
    private final java.util.Map<ThemeKey<?>,Object> themeProperties = new java.util.LinkedHashMap<>();
    private final java.util.Map<ThemeKey<?>,ThemeDB.Resolution<?>> themeCache = new java.util.HashMap<>();
    private boolean observingThemes;

    public static void registerTheme(ThemeDB db) { db.registerType("Control",null); }
    public Control setTheme(Theme theme) {
        signalScope.checkThread(); requireThemeAlive();
        if(theme!=null && theme.getThemeDB()!=ThemeDB.getDefault()) throw new IllegalArgumentException("Control themes must use the client ThemeDB");
        if (this.theme != theme) { this.theme=theme; invalidateTheme(true); }
        return this;
    }
    /** Only the explicitly assigned Theme, never the inherited result. */
    public Theme getTheme() { return theme; }
    public Control setThemeTypeVariation(String variation) {
        signalScope.checkThread(); requireThemeAlive(); Objects.requireNonNull(variation);
        if (!variation.isEmpty() && !ThemeDB.getDefault().hasType(variation)) throw new IllegalArgumentException("Unregistered theme variation: " + variation);
        if (DebugBridge.previewWrite(this,"themeTypeVariation",variation)) return this;
        if (!themeTypeVariation.equals(variation)) { themeTypeVariation=variation; invalidateTheme(true); }
        return this;
    }
    @Inspectable(group="Appearance")
    public String getThemeTypeVariation() {
        String effective=DebugBridge.value(this,"themeTypeVariation",themeTypeVariation);
        if(!resolvedThemeVariation.equals(effective)) {
            resolvedThemeVariation=effective;
            invalidateTheme(true);
        }
        return effective;
    }
    public String getThemeType() {
        for(Class<?> type=getClass();type!=null;type=type.getSuperclass())
            if(ThemeDB.getDefault().hasType(type.getSimpleName())) return type.getSimpleName();
        return "Control";
    }
    /** Style inheritance can differ from the drawing tree (popup menus and owned parts). */
    public Control setThemeParent(Control source) {
        signalScope.checkThread(); requireThemeAlive();
        if(source!=null && !source.isAlive()) throw new IllegalArgumentException("Theme source is not alive");
        for(Control c=source;c!=null;c=c.getThemeParent()) if(c==this) throw new IllegalArgumentException("Theme inheritance cycle");
        Control previous=themeParent==null?null:themeParent.get();
        if(previous==source) return this;
        if(previous!=null) previous.themeDependents.removeIf(ref -> ref.get()==null || ref.get()==this);
        themeParent=source==null?null:new java.lang.ref.WeakReference<>(source);
        if(source!=null) source.themeDependents.add(new java.lang.ref.WeakReference<>(this));
        invalidateTheme(true); return this;
    }
    public Control getThemeParent() {
        Control explicit=themeParent==null?null:themeParent.get();
        return explicit!=null && explicit.isAlive()?explicit:parent!=null?parent:lifecycleOwner;
    }
    public <T> Control setThemeOverride(ThemeKey<T> key,T value) {
        signalScope.checkThread(); requireThemeAlive(); ThemeDB.getDefault().validateValue(key,value);
        Object previous=themeOverrides.entrySet().stream().filter(e -> e.getKey().getId().equals(key.getId())).map(java.util.Map.Entry::getValue).findFirst().orElse(null);
        themeOverrides.keySet().removeIf(k -> k.getId().equals(key.getId())); themeOverrides.put(key,value);
        if(!Objects.equals(previous,value)) invalidateTheme(key.affectsLayout());
        return this;
    }
    public Control removeThemeOverride(ThemeKey<?> key) {
        signalScope.checkThread(); if(themeOverrides.keySet().removeIf(k -> k.getId().equals(key.getId()))) invalidateTheme(key.affectsLayout()); return this;
    }
    /** Property setters use the same resolver, but remain distinguishable in diagnostics. */
    protected final <T> void setThemeProperty(ThemeKey<T> key,T value) {
        signalScope.checkThread(); requireThemeAlive(); ThemeDB.getDefault().validateValue(key,value);
        if(!Objects.equals(themeProperties.put(key,value),value)) invalidateTheme(key.affectsLayout());
    }
    protected final void clearThemeProperty(ThemeKey<?> key) {
        signalScope.checkThread(); if(themeProperties.remove(key)!=null) invalidateTheme(key.affectsLayout());
    }
    final void validateThemeAttachment(Control proposedParent) {
        if(themeParent!=null && themeParent.get()!=null) return;
        for(Control c=proposedParent;c!=null;c=c.getThemeParent()) if(c==this) throw new IllegalArgumentException("Mixed tree/theme cycle");
    }
    private void requireThemeAlive() { if(!isAlive()) throw new IllegalStateException("Cannot style a pending/freed control"); }
    @SuppressWarnings("unchecked")
    public final <T> ThemeDB.Resolution<T> getThemeResolution(ThemeKey<T> key) {
        signalScope.checkThread();
        String variation=getThemeTypeVariation();
        if(!observingThemes && isAlive()) {
            observingThemes=true;
            ThemeDB.getDefault().changed().connect(this, layout -> { if(isAlive()) invalidateThemeSelf(layout); });
        }
        ThemeDB.Resolution<?> cached=themeCache.get(key);
        if(cached!=null) return (ThemeDB.Resolution<T>)cached;
        ThemeDB database=ThemeDB.getDefault();
        java.util.LinkedHashSet<String> types=new java.util.LinkedHashSet<>();
        var keyTypes=database.getTypeChain(key.type());
        // Resource namespaces such as Icons do not inherit unrelated Control items or variations.
        if(!keyTypes.contains("Control")) types.addAll(keyTypes);
        else {
            if(!variation.isEmpty()) types.addAll(database.getTypeChain(variation));
            var actualTypes=database.getTypeChain(getThemeType());
            if(!actualTypes.contains(key.type())) {
                // Internal images keep their own presentation type, but use their semantic owner's variation.
                java.util.Set<Control> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                for(Control source=key.kind()==ThemeKey.Kind.TEXTURE?getThemeParent():null;
                        source!=null && seen.add(source);source=source.getThemeParent()) {
                    var sourceTypes=database.getTypeChain(source.getThemeType());
                    if(sourceTypes.contains(key.type())) {
                        if(!source.getThemeTypeVariation().isEmpty()) types.addAll(database.getTypeChain(source.getThemeTypeVariation()));
                        types.addAll(sourceTypes); break;
                    }
                }
                types.addAll(keyTypes);
            }
            types.addAll(actualTypes); types.addAll(keyTypes);
            types.remove("Control"); types.add("Control");
        }
        var candidates=List.copyOf(types);
        var property=database.getLocalThemeValue(themeProperties,key,candidates);
        var override=database.getLocalThemeValue(themeOverrides,key,candidates);
        ThemeDB.Resolution<T> result;
        if(property!=null || override!=null) {
            var local=property!=null?property:override;
            result=new ThemeDB.Resolution<>(key.validate(local.value()),List.of(new ThemeDB.Trace(
                    property!=null?"explicit property":"local override","","",themeSourceName(),local.item(),"resolved")));
        } else {
            List<ThemeDB.Layer> layers=new ArrayList<>();
            java.util.Set<Control> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            Control previous=null;
            for(Control c=this;c!=null && seen.add(c);c=c.getThemeParent()) {
                boolean inheritOverrides=previous!=null && (previous.themeParent!=null && previous.themeParent.get()==c
                        || previous.lifecycleOwner==c && c.parts.containsValue(previous));
                if(c.theme!=null || inheritOverrides) layers.add(new ThemeDB.Layer(c.theme,c==this?"own theme":"ancestor theme",c.themeSourceName(),
                        inheritOverrides?c.themeOverrides:java.util.Map.of()));
                previous=c;
            }
            layers.add(new ThemeDB.Layer(ThemeDB.getDefault().getDefaultTheme(),"host default","",java.util.Map.of()));
            result=ThemeDB.getDefault().resolve(key,List.copyOf(types),layers);
        }
        themeCache.put(key,result); return result;
    }
    private String themeSourceName() { return (debugName.isEmpty()?getThemeType():debugName)+"#"+SignalDiagnostics.ownerId(this); }
    public String getThemeState() { return getVisible()?"visible":"hidden"; }
    public final java.util.List<ThemeKey<?>> getThemeKeys() {
        var types=new java.util.LinkedHashSet<>(ThemeDB.getDefault().getTypeChain(getThemeType()));
        if(!getThemeTypeVariation().isEmpty()) types.addAll(ThemeDB.getDefault().getTypeChain(getThemeTypeVariation()));
        return ThemeDB.getDefault().getKeys().stream().filter(key -> types.contains(key.type()) || themeCache.containsKey(key)).toList();
    }
    public final <T> T getThemeItem(ThemeKey<T> key) { return getThemeResolution(key).value(); }
    final void invalidateTheme(boolean layout) {
        invalidateThemeSelf(layout);
        for(Control c:ownedSnapshot()) if(c.isAlive()) c.invalidateTheme(layout);
        themeDependents.removeIf(ref -> ref.get()==null || !ref.get().isAlive());
        for(var ref:List.copyOf(themeDependents)) { Control c=ref.get(); if(c!=null && c.isAlive()) c.invalidateTheme(layout); }
    }
    private void invalidateThemeSelf(boolean layout) {
        themeCache.clear();
        if(layout) invalidateTextLayout();
        LifecycleScheduler.themeChanged(this);
    }

    private final SignalSource0 focusEntered = new SignalSource0(this, "focusEntered");
    private final SignalSource0 focusExited = new SignalSource0(this, "focusExited");
    private final SignalSource<Boolean> visibilityChanged = new SignalSource<>(this, "visibilityChanged", "Boolean");
    private final java.util.LinkedHashMap<String, Control> parts = new java.util.LinkedHashMap<>();
    private final java.util.Set<Control> pooled = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    final List<Control> children = new ArrayList<>();
    boolean validatingChildren;
    boolean updatingChildren;
    private LifecycleState lifecycleState = LifecycleState.ALIVE;
    Control lifecycleOwner;
    private List<Control> freeOwned = List.of();
    private io.github.luyingyilong.ore.window.WindowContext treeContext;
    private boolean insideTree, ready, suspended, effectiveVisible, enteredDelivered;
    private long treeEpoch;
    private String freeReason = "", freeSource = "";
    private boolean signalFocused;
    private SignalScope bindingScope;
    private String debugName = "";
    Control parent;
    private Rect bounds = new Rect(0, 0, 0, 0);
    private Rect requestedBounds = new Rect(0, 0, 0, 0);
    private Offset offset = Offset.ZERO;
    private int minimumWidth;
    private int minimumHeight;
    private int preferredWidth;
    private int preferredHeight;
    private int sizeFlagsHorizontal = SizeFlags.SIZE_FILL;
    private int sizeFlagsVertical = SizeFlags.SIZE_FILL;
    private boolean visible = true;
    private boolean layoutDirty = true;
    private boolean focusable;
    private Font layoutFont;
    private Transform2D renderTransform = Transform2D.IDENTITY;
    private float sizeFlagsStretchRatio = 1.0F;

    @Override
    public final SignalScope getSignalScope() {
        return signalScope;
    }

    public final Signal0 focusEntered() {
        return focusEntered.signal();
    }

    public final Signal0 focusExited() {
        return focusExited.signal();
    }

    public final Signal<Boolean> visibilityChanged() {
        return visibilityChanged.signal();
    }

    public final boolean isDisposed() {
        return lifecycleState == LifecycleState.FREED;
    }

    /**
     * Connections capturing the current virtual row/item belong here, not to the reusable view.
     */
    public final SignalScope getBindingScope() {
        signalScope.checkThread();
        if (!isAlive()) throw new IllegalStateException("Control has been disposed");
        if (bindingScope == null) bindingScope = signalScope.child("binding");
        return bindingScope;
    }

    public final void resetBindingScope() {
        if (bindingScope != null) {
            bindingScope.close(SignalDiagnostics.Reason.BINDING_RESET);
            bindingScope = null;
        }
        for (Control child : ownedSnapshot()) if (child.isAlive()) child.resetBindingScope();
    }

    public final LifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public final boolean isAlive() {
        return lifecycleState == LifecycleState.ALIVE;
    }

    public final boolean isQueuedForDeletion() {
        return lifecycleState == LifecycleState.QUEUED_FOR_FREE || lifecycleState == LifecycleState.FREEING;
    }

    public final boolean isInsideTree() {
        return insideTree;
    }

    public final boolean isReady() {
        return ready;
    }

    public final boolean isSuspended() {
        return suspended;
    }

    public final LifecycleInfo getLifecycleInfo() {
        return new LifecycleInfo(SignalDiagnostics.ownerId(this), debugName.isEmpty() ? getClass().getSimpleName() : debugName,
                lifecycleState, insideTree, ready, suspended, freeReason, freeSource);
    }

    public final void queueFree() {
        queueFree("direct request");
    }

    /**
     * Hosts may supply a diagnostic reason; subsequent requests never overwrite the first.
     */
    public final void queueFree(String reason) {
        signalScope.checkThread();
        if (!isAlive()) return;
        Objects.requireNonNull(reason);
        String source = DebugBridge.active() ? StackWalker.getInstance().walk(frames -> frames
                .filter(f -> !f.getClassName().equals(Control.class.getName()))
                .limit(8).map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1)
                        + "." + f.getMethodName() + "(" + f.getFileName() + ":" + f.getLineNumber() + ")").collect(java.util.stream.Collectors.joining("\n"))) : "";
        markForFree(reason, source);
        for (Control node = parent; node != null; node = node.parent) node.syncChildrenChanged();
        LifecycleScheduler.enqueue(this);
        Window window = getWindowContext().getOwner();
        if (window != null) {
            window.cancelCaptures(this, CancelEvent.Reason.DETACHED);
            window.getFocusManager().forgetSubtree(this);
        }
        cancelInteractions();
        requestLayout();
    }

    final boolean queuedOwns(Control candidate) {
        if (this == candidate) return true;
        for (Control c : freeOwned) if (c.queuedOwns(candidate)) return true;
        return false;
    }

    private void markForFree(String reason, String source) {
        if (!isAlive()) return;
        lifecycleState = LifecycleState.QUEUED_FOR_FREE;
        freeReason = reason;
        freeSource = source;
        freeOwned = ownedSnapshot();
        for(var ref:List.copyOf(themeDependents)) { Control dependent=ref.get(); if(dependent!=null && dependent.isAlive()) dependent.setThemeParent(null); }
        publishLifecycle();
        for (Control child : freeOwned)
            child.markForFree(child.parent == this ? "parent cleanup: " + getDebugName()
                    : "owned part cleanup: " + getDebugName(), source);
    }

    /**
     * Register a non-layout component. Its active-tree lifetime follows this owner.
     */
    public final <T extends Control> T ownPart(String name, T part) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(part);
        requireOwnership(part);
        Control previous = parts.get(name);
        if(previous==part) return part;
        if (previous != null && previous != part) throw new IllegalStateException("Part name already in use: " + name);
        parts.put(name, part);
        ((Control) part).lifecycleOwner = this;
        ((Control) part).invalidateTheme(true);
        if (insideTree) {
            part.enterTree(treeContext, suspended, effectiveVisible);
            LifecycleScheduler.prepare(part);
        }
        return part;
    }

    /**
     * Pool ownership survives temporary detachment; it does not activate a detached view.
     */
    public final <T extends Control> T ownPooled(T part) {
        requireOwnership(part);
        if(!pooled.add(part)) return part;
        ((Control) part).lifecycleOwner = this;
        ((Control) part).invalidateTheme(true);
        return part;
    }

    private void requireOwnership(Control part) {
        signalScope.checkThread();
        part.signalScope.checkThread();
        if (!isAlive() || !part.isAlive()) throw new IllegalStateException("Cannot own a pending/freed control");
        if (part.lifecycleOwner != null && part.lifecycleOwner != this || part.parent != null && part.parent != this)
            throw new IllegalStateException("Control already has a lifecycle owner");
        requireAcyclicChild(part);
        part.validateThemeAttachment(this);
        part.validateTransformAttachment(this);
    }

    /**
     * Layout children, internal parts and pooled controls form one ownership graph.
     */
    private void requireAcyclicChild(Control child) {
        ControlTree.requireAcyclic(this, child);
    }

    public final java.util.Map<String, Control> getOwnedParts() {
        return java.util.Map.copyOf(parts);
    }

    final List<Control> ownedSnapshot() {
        java.util.Set<Control> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Control> result = new ArrayList<>();
        for (Control c : getChildren()) if (c.parent == this && seen.add(c)) result.add(c);
        for (Control c : List.copyOf(parts.values())) if (seen.add(c)) result.add(c);
        for (Control c : List.copyOf(pooled)) if (seen.add(c)) result.add(c);
        return result;
    }

    private List<Control> activeOwnedSnapshot() {
        java.util.Set<Control> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Control> result = new ArrayList<>();
        for (Control c : getChildren()) if (c.parent == this && seen.add(c)) result.add(c);
        for (Control c : List.copyOf(parts.values())) if (seen.add(c)) result.add(c);
        return result;
    }

    protected void notification(ControlNotification notification) {
    }

    final ControlNotification notificationSnapshot(ControlNotification.Type type) {
        return new ControlNotification(type, treeContext, insideTree, ready, effectiveVisible, treeEpoch);
    }

    final void deliverNotification(ControlNotification notice) {
        if (isDisposed()) return;
        if (!isAlive() && notice.type() == ControlNotification.Type.READY) return;
        if (notice.type() == ControlNotification.Type.ENTER_TREE && notice.treeEpoch() == treeEpoch)
            enteredDelivered = true;
        try {
            notification(notice);
        } catch (RuntimeException error) {
            lifecycleError("notification " + notice.type(), error);
        }
    }

    private void lifecycleError(String phase, RuntimeException error) {
        System.getLogger(Control.class.getName()).log(System.Logger.Level.ERROR, "Ore " + phase + ": " + getDebugName(), error);
    }

    final void enterTree(io.github.luyingyilong.ore.window.WindowContext context, boolean paused, boolean ancestorVisible) {
        if (!isAlive() || insideTree) return;
        treeContext = context;
        insideTree = true;
        enteredDelivered = false;
        treeEpoch++;
        suspended = paused;
        effectiveVisible = ancestorVisible && visible;
        LifecycleScheduler.notify(this, ControlNotification.Type.ENTER_TREE);
        for (Control c : activeOwnedSnapshot()) c.enterTree(context, paused, effectiveVisible);
    }

    /**
     * Input proxies wait for their host's native adapter; ordinary controls are immediately ready.
     */
    protected boolean lifecycleReady() {
        return true;
    }

    final void readySubtree() {
        if (!insideTree || !isAlive()) return;
        long preparingEpoch = treeEpoch;
        for (Control c : activeOwnedSnapshot()) {
            if (!insideTree || !isAlive() || treeEpoch != preparingEpoch) return;
            if (c.parent == this || parts.containsValue(c)) c.readySubtree();
        }
        // A child's READY callback may detach, free or reattach this subtree.
        if (!insideTree || !isAlive() || treeEpoch != preparingEpoch
                || ready || !enteredDelivered || !lifecycleReady()) return;
        if (activeOwnedSnapshot().stream().anyMatch(c -> c.isAlive() && c.insideTree && !c.ready)) return;
        ready = true;
        deliverNotification(notificationSnapshot(ControlNotification.Type.READY));
        Control owner = parent != null ? parent : lifecycleOwner;
        if (owner != null && owner.insideTree && !owner.ready) LifecycleScheduler.prepare(owner);
    }

    final void exitTree() {
        if (!insideTree) return;
        for (Control c : activeOwnedSnapshot()) c.exitTree();
        insideTree = false;
        suspended = false;
        effectiveVisible = false;
        LifecycleScheduler.notify(this, ControlNotification.Type.EXIT_TREE);
    }

    final void suspendTree(boolean value) {
        if (!isAlive() || !insideTree || suspended == value) return;
        suspended = value;
        LifecycleScheduler.notify(this, value ? ControlNotification.Type.SUSPENDED : ControlNotification.Type.RESUMED);
        for (Control c : activeOwnedSnapshot()) c.suspendTree(value);
    }

    private void updateEffectiveVisibility(boolean ancestorVisible) {
        boolean next = insideTree && visible && ancestorVisible;
        if (effectiveVisible != next) {
            effectiveVisible = next;
            LifecycleScheduler.notify(this, ControlNotification.Type.VISIBILITY_CHANGED);
        }
        for (Control c : activeOwnedSnapshot()) c.updateEffectiveVisibility(next);
    }

    final void freeNow() {
        if (lifecycleState != LifecycleState.QUEUED_FOR_FREE) return;
        try {
            preFreeSubtree();
            LifecycleScheduler.notifications();
        } finally {
            try {
                exitTree();
                LifecycleScheduler.notifications();
            } finally {
                finishFree();
            }
        }
    }

    private void preFreeSubtree() {
        if (lifecycleState != LifecycleState.QUEUED_FOR_FREE) return;
        lifecycleState = LifecycleState.FREEING;
        LifecycleScheduler.notify(this, ControlNotification.Type.PRE_FREE);
        for (Control c : freeOwned) c.preFreeSubtree();
    }

    private void finishFree() {
        if (lifecycleState != LifecycleState.FREEING) return;
        try {
            for (Control c : freeOwned) {
                try {
                    if (c.lifecycleState == LifecycleState.QUEUED_FOR_FREE) c.freeNow();
                    else c.finishFree();
                } catch (RuntimeException error) {
                    lifecycleError("owned cleanup", error);
                }
            }
        } finally {
            try {
                signalScope.close(SignalDiagnostics.Reason.OWNER_DISPOSED);
            } finally {
                if (parent != null) ControlTree.detachForFree(this);
                if (lifecycleOwner != null) {
                    lifecycleOwner.parts.values().removeIf(c -> c == this);
                    lifecycleOwner.pooled.remove(this);
                    lifecycleOwner = null;
                }
                ControlTree.clearForFree(this);
                parts.clear();
                pooled.clear();
                freeOwned = List.of();
                bindingScope = null;
                if(themeParent!=null && themeParent.get()!=null)
                    themeParent.get().themeDependents.removeIf(ref -> ref.get()==null || ref.get()==this);
                for(var ref:List.copyOf(themeDependents)) {
                    Control dependent=ref.get(); if(dependent!=null && dependent.isAlive()) dependent.setThemeParent(null);
                }
                themeParent=null; themeDependents.clear(); themeCache.clear(); themeProperties.clear(); themeOverrides.clear(); theme=null;
                lifecycleState = LifecycleState.FREED;
                publishLifecycle();
                treeContext = null;
            }
        }
    }

    private void publishLifecycle() {
        if (!DebugBridge.active()) return;
        try {
            DebugBridge.backend().lifecycle(this, getLifecycleInfo());
        } catch (RuntimeException error) {
            lifecycleError("diagnostic observer", error);
        }
    }

    public final void notifyFocusChanged(boolean focused) {
        onFocusChanged(focused);
        if (signalFocused == focused) return;
        signalFocused = focused;
        if (focused) focusEntered.emit();
        else focusExited.emit();
    }

    public Font getLayoutFont() {
        return layoutFont;
    }

    public Control setMinimum(int width, int height) {
        minimumWidth = Math.max(0, width);
        minimumHeight = Math.max(0, height);
        requestLayout();
        return this;
    }

    public Control setMinimum(Size size) {
        return setMinimum(size.width(), size.height());
    }

    public Control setPreferred(int width, int height) {
        preferredWidth = Math.max(0, width);
        preferredHeight = Math.max(0, height);
        requestLayout();
        return this;
    }

    @Inspectable(min = 0, max = 15)
    public int getSizeFlagsHorizontal() {
        int sizeFlagsHorizontal = DebugBridge.value(this, "sizeFlagsHorizontal", this.sizeFlagsHorizontal);
        return sizeFlagsHorizontal;
    }

    public Control setSizeFlagsHorizontal(int flags) {
        SizeFlags.validate(flags);
        if (DebugBridge.previewWrite(this, "sizeFlagsHorizontal", flags)) return this;
        if (sizeFlagsHorizontal != flags) {
            sizeFlagsHorizontal = flags;
            requestLayout();
        }
        return this;
    }

    @Inspectable(min = 0, max = 15)
    public int getSizeFlagsVertical() {
        int sizeFlagsVertical = DebugBridge.value(this, "sizeFlagsVertical", this.sizeFlagsVertical);
        return sizeFlagsVertical;
    }

    public Control setSizeFlagsVertical(int flags) {
        SizeFlags.validate(flags);
        if (DebugBridge.previewWrite(this, "sizeFlagsVertical", flags)) return this;
        if (sizeFlagsVertical != flags) {
            sizeFlagsVertical = flags;
            requestLayout();
        }
        return this;
    }

    @Inspectable(min = 0.0001, max = 100000)
    public float getSizeFlagsStretchRatio() {
        float sizeFlagsStretchRatio = DebugBridge.value(this, "sizeFlagsStretchRatio", this.sizeFlagsStretchRatio);
        return sizeFlagsStretchRatio;
    }

    public Control setSizeFlagsStretchRatio(float ratio) {
        if (!Float.isFinite(ratio) || ratio <= 0.0F)
            throw new IllegalArgumentException("Stretch ratio must be positive");
        if (DebugBridge.previewWrite(this, "sizeFlagsStretchRatio", ratio)) return this;
        if (sizeFlagsStretchRatio != ratio) {
            sizeFlagsStretchRatio = ratio;
            requestLayout();
        }
        return this;
    }

    public boolean getFocusable() {
        return focusable;
    }

    public Control setFocusable(boolean focusable) {
        this.focusable = focusable;
        return this;
    }

    public Transform2D getRenderTransform() {
        if (!DebugBridge.active()) return renderTransform;
        return new Transform2D(DebugBridge.value(this,"renderTranslationX",renderTransform.getTranslationX()),
                DebugBridge.value(this,"renderTranslationY",renderTransform.getTranslationY()),
                DebugBridge.value(this,"renderRotation",renderTransform.getRotationDegrees()),
                DebugBridge.value(this,"renderScale",renderTransform.getScale()),
                DebugBridge.value(this,"renderPivotX",renderTransform.getPivotX()),
                DebugBridge.value(this,"renderPivotY",renderTransform.getPivotY()));
    }

    public Control setRenderTransform(Transform2D value) {
        signalScope.checkThread();
        if (!isAlive()) throw new IllegalStateException("Cannot transform a pending/freed control");
        Objects.requireNonNull(value);
        if(renderTransform.equals(value))return this;
        validateRenderTransform(value);
        renderTransform=value;
        requestLayout();
        return this;
    }

    protected boolean requiresAxisAlignedClip() { return false; }
    public Rect getClipBounds() { return requiresAxisAlignedClip()?getAbsoluteBounds():null; }
    protected void validateOwnRenderTransform(Transform2D value) { }
    protected boolean requiresUprightInput() { return false; }

    public final Control getGeometryParent() { return parent != null ? parent : lifecycleOwner; }

    public final void validateRenderTransform(Transform2D proposed) {
        double angle=0;
        for(Control node=getGeometryParent();node!=null;node=node.getGeometryParent()) angle=(angle+node.getRenderTransform().getRotationDegrees()%360)%360;
        validateTransformTree(angle,proposed);
    }

    final void validateTransformAttachment(Control owner) {
        double angle=0;
        for(Control node=owner;node!=null;node=node.getGeometryParent()) angle=(angle+node.getRenderTransform().getRotationDegrees()%360)%360;
        validateTransformTree(angle,getRenderTransform());
    }

    private void validateTransformTree(double angle,Transform2D proposed) {
        validateOwnRenderTransform(proposed);
        angle=(angle+proposed.getRotationDegrees()%360)%360;
        proposed.toAffine(getAbsoluteBounds()).matrix();
        double remainder=Math.IEEEremainder(angle,360);
        if (requiresUprightInput() && Math.abs(remainder)>1e-8
                || requiresAxisAlignedClip() && Math.abs(Math.IEEEremainder(angle,90))>1e-8)
            throw new IllegalArgumentException("Rotation is not supported by this input/clip subtree: "+getClass().getSimpleName());
        for(Control child:ownedSnapshot()) if(child.isAlive()) child.validateTransformTree(angle,child.getRenderTransform());
    }

    /** Called before changing an existing node into a clipped/upright surface. */
    protected final void validateGeometryRequirement(boolean clipped,boolean upright) {
        double angle=0;
        for(Control node=this;node!=null;node=node.getGeometryParent()) angle=(angle+node.getRenderTransform().getRotationDegrees()%360)%360;
        if (upright && Math.abs(Math.IEEEremainder(angle,360))>1e-8
                || clipped && Math.abs(Math.IEEEremainder(angle,90))>1e-8)
            throw new IllegalArgumentException("This transform requires polygon clipping or rotated native input");
    }

    @Inspectable(group="Transform") public double getRenderTranslationX(){return getRenderTransform().getTranslationX();}
    @Inspectable(group="Transform") public double getRenderTranslationY(){return getRenderTransform().getTranslationY();}
    @Inspectable(group="Transform") public double getRenderRotation(){return getRenderTransform().getRotationDegrees();}
    @Inspectable(group="Transform",min=0.0001,max=10000) public double getRenderScale(){return getRenderTransform().getScale();}
    @Inspectable(group="Transform",min=0,max=1) public double getRenderPivotX(){return getRenderTransform().getPivotX();}
    @Inspectable(group="Transform",min=0,max=1) public double getRenderPivotY(){return getRenderTransform().getPivotY();}
    public void setRenderTranslationX(double v){setRenderComponent("renderTranslationX",v);}
    public void setRenderTranslationY(double v){setRenderComponent("renderTranslationY",v);}
    public void setRenderRotation(double v){setRenderComponent("renderRotation",v);}
    public void setRenderScale(double v){setRenderComponent("renderScale",v);}
    public void setRenderPivotX(double v){setRenderComponent("renderPivotX",v);}
    public void setRenderPivotY(double v){setRenderComponent("renderPivotY",v);}
    private void setRenderComponent(String key,double value){
        validateRenderTransform(withRenderComponent(getRenderTransform(),key,value));
        if(DebugBridge.previewWrite(this,key,value)) return;
        // An ordinary setter updates only its source property, never copying other Inspector overrides.
        setRenderTransform(withRenderComponent(renderTransform,key,value));
    }
    private static Transform2D withRenderComponent(Transform2D base,String key,double value){
        return new Transform2D(key.equals("renderTranslationX")?value:base.getTranslationX(),
                key.equals("renderTranslationY")?value:base.getTranslationY(),
                key.equals("renderRotation")?value:base.getRotationDegrees(),
                key.equals("renderScale")?value:base.getScale(),
                key.equals("renderPivotX")?value:base.getPivotX(),
                key.equals("renderPivotY")?value:base.getPivotY());
    }

    public final io.github.luyingyilong.ore.render.Affine2D getLocalToWindowTransform() {
        Rect origin=getAbsoluteBounds();
        return io.github.luyingyilong.ore.event.EventGeometry.matrix(this)
                .multiply(io.github.luyingyilong.ore.render.Affine2D.translation(origin.x(),origin.y()));
    }

    public FocusGeometry getFocusGeometry() {
        return FocusGeometry.forControl(this,getFocusBounds());
    }

    public final void drawFocus(DrawContext context,int x,int y) {
        getFocusGeometry().draw(this,context,x,y);
    }

    public boolean acceptsFocus() {
        return focusable && isInputAccessible();
    }

    /** Derived input eligibility, independent of drawing visibility during exit animations. */
    public final boolean isInputAccessible() {
        for(Control child=this,node=this;node!=null;child=node,node=node.getGeometryParent()) {
            if(!node.isAlive()||!node.getVisible()||node.isSuspended())return false;
            if(child!=node&&!node.allowsChildInput(child))return false;
        }
        return true;
    }

    protected boolean allowsChildInput(Control child) { return true; }

    /** Framework frame phase, before measurement; implementations must not edit the tree here. */
    protected boolean advanceAnimation(long milliseconds) { return false; }

    public final boolean advanceAnimations(long milliseconds) {
        if(!isAlive()||!getVisible()||isSuspended())return false;
        try(var phase=DispatchGuard.enter()) {
            boolean active=advanceAnimation(milliseconds);
            for(Control child:activeOwnedSnapshot())if(child.isAlive())active|=child.advanceAnimations(milliseconds);
            return active;
        }
    }

    public boolean getFocusScope() {
        return false;
    }

    /**
     * Z translation applied by this node when it paints its subtree.
     */
    public float getContentDepth() {
        return 0.0F;
    }

    /**
     * Bounds in Window coordinates; virtual controls may return their active item's bounds.
     */
    public Rect getFocusBounds() {
        return getAbsoluteBounds();
    }

    public boolean onFocusKey(int keyCode, int modifiers) {
        return false;
    }

    public boolean onFocusMove(FocusManager.Direction direction) {
        return false;
    }

    public boolean activate() {
        return false;
    }

    /**
     * Paired keyboard activation; simple controls may still activate on the initial press.
     */
    public boolean onActivationPressed(int keyCode) {
        return activate();
    }

    public boolean onActivationReleased(int keyCode) {
        return false;
    }

    protected boolean pointerUp(boolean over) {
        return false;
    }

    protected boolean pointerUp(int rootX, int rootY, boolean over) {
        return pointerUp(over);
    }

    /**
     * Coordinates use the same unscrolled tree space as pointer presses and releases.
     */
    protected boolean pointerMove(int rootX, int rootY) {
        return false;
    }

    public void cancelInteraction() {
    }

    public final void cancelInteractions() {
        try {
            cancelInteraction();
        } catch (RuntimeException error) {
            lifecycleError("cancel interaction", error);
        } finally {
            for (Control child : ownedSnapshot()) child.cancelInteractions();
        }
    }

    public void onFocusChanged(boolean focused) {
    }

    public io.github.luyingyilong.ore.window.WindowContext getWindowContext() {
        for (Control node = this; node != null; node = node.getParent())
            if (node instanceof Window window) return window.getContext();
        if (treeContext != null) return treeContext;
        if (lifecycleOwner != null) return lifecycleOwner.getWindowContext();
        return io.github.luyingyilong.ore.window.WindowContext.getCurrent();
    }

    public final Control getParent() { return parent; }

    /** Immutable snapshot of real direct children, including hidden and queued nodes. */
    public final List<Control> getChildren() { return List.copyOf(children); }
    public final Control getChild(int index) { return children.get(index); }
    public final int getChildCount() { return children.size(); }
    public final void addChild(Control child) { ControlTree.add(this, child); }
    public final boolean removeChild(Control child) { return ControlTree.remove(this, child); }
    public final void moveChild(Control child, int index) { ControlTree.move(this, child, index); }

    /** Validate the entire proposed list without mutating the tree. */
    protected void validateChildren(List<Control> proposedChildren) { }

    /** Synchronize derived indexes after a direct or descendant structural change. */
    protected void childrenChanged() { }

    final void syncChildrenChanged() {
        try { childrenChanged(); }
        catch (RuntimeException error) { lifecycleError("child index synchronization", error); }
    }


    /** Atomic framework operation for named slots and fixed composite skeletons. */
    protected final void updateChildren(List<Control> proposedChildren) {
        ControlTree.update(this, proposedChildren);
    }
    protected final boolean isUpdatingChildren() { return updatingChildren; }
    protected final void replaceChild(Control previous, Control next) {
        ControlTree.replace(this, previous, next);
    }

    final java.util.List<Theme> inheritedThemes() {
        java.util.List<Theme> result=new java.util.ArrayList<>();
        java.util.Set<Control> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for(Control node=this;node!=null && seen.add(node);node=node.getThemeParent()) if(node.theme!=null) result.add(node.theme);
        return java.util.List.copyOf(result);
    }
    final void refreshThemeInheritance(java.util.List<Theme> previous) {
        if(isAlive()) invalidateTheme(!previous.equals(inheritedThemes()));
    }
    final void childAttached(Control child) {
        if (insideTree) {
            child.enterTree(treeContext, suspended, effectiveVisible);
            LifecycleScheduler.prepare(child);
        }
    }

    final void childDetached(Control child) {
        Window window = getWindowContext().getOwner();
        if (window != null) {
            window.cancelCaptures(child, CancelEvent.Reason.DETACHED);
            window.getFocusManager().forgetSubtree(child);
        }
        child.exitTree();
        child.cancelInteractions();
    }

    /**
     * Free controls can place children; a Container owns its children's rectangles.
     */
    public Control setPosition(int x, int y) {
        requireFreePlacement();
        requestedBounds = new Rect(x, y, requestedBounds.width(), requestedBounds.height());
        requestLayout();
        return this;
    }

    public Control setRequestedSize(int width, int height) {
        requireFreePlacement();
        requestedBounds = new Rect(requestedBounds.x(), requestedBounds.y(), width, height);
        requestLayout();
        return this;
    }

    private void requireFreePlacement() {
        if (parent instanceof Container)
            throw new IllegalStateException("The parent container controls position and size");
    }

    public void requestLayout() {
        layoutDirty = true;
        if (parent != null) parent.requestLayout();
        else if (lifecycleOwner != null && lifecycleOwner.parts.containsValue(this)) lifecycleOwner.requestLayout();
    }

    public boolean getLayoutDirty() {
        return layoutDirty;
    }

    /**
     * Resource reloads can change glyph advances without changing the text itself.
     */
    public void invalidateTextLayout() {
        requestLayout();
        for (Control child : ownedSnapshot()) if (child.isAlive()) child.invalidateTextLayout();
    }

    public int getMinimumWidth() {
        int minimumWidth = DebugBridge.value(this, "minimumWidth", this.minimumWidth);
        return minimumWidth;
    }

    public int getMinimumHeight() {
        int minimumHeight = DebugBridge.value(this, "minimumHeight", this.minimumHeight);
        return minimumHeight;
    }

    public Size getMinimumSize() {
        return new Size(getMinimumWidth(), getMinimumHeight());
    }

    public boolean getVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            if (!visible) {
                if (getWindowContext().getOwner() != null)
                    getWindowContext().getOwner().cancelCaptures(this, CancelEvent.Reason.DETACHED);
                cancelInteractions();
            }
            updateEffectiveVisibility(parent != null ? parent.effectiveVisible : lifecycleOwner != null ? lifecycleOwner.effectiveVisible : true);
            requestLayout();
            visibilityChanged.emit(visible);
        }
    }

    public Control setOffset(int x, int y) {
        return setOffset(new Offset(x, y));
    }

    public Offset getOffset() {
        if (!DebugBridge.active()) return offset;
        return new Offset(debugValue("offsetX", offset.x()), debugValue("offsetY", offset.y()));
    }

    /**
     * Layout translation shared by rendering, descendants, hit testing and focus.
     */
    public Control setOffset(Offset offset) {
        Offset next = Objects.requireNonNull(offset);
        if (!this.offset.equals(next)) {
            this.offset = next;
            requestLayout();
        }
        return this;
    }

    protected final <T> T debugValue(String key, T base) {
        return DebugBridge.value(this, key, base);
    }

    protected final int debugValue(String key, int base) {
        return DebugBridge.value(this, key, base);
    }

    public String getDebugName() {
        return debugName;
    }

    public Control setDebugName(String name) {
        debugName = java.util.Objects.requireNonNull(name);
        return this;
    }

    public Rect getDebugRequestedBounds() {
        if (!DebugBridge.active()) return requestedBounds;
        return new Rect(debugValue("x", requestedBounds.x()), debugValue("y", requestedBounds.y()),
                debugValue("width", requestedBounds.width()), debugValue("height", requestedBounds.height()));
    }

    /**
     * Complex/forwarded properties only; the development Inspector also discovers @Inspectable getters.
     */
    public void debugProperties(java.util.List<DebugProperty> out) {
        var number = (java.util.function.BiConsumer<String, java.util.function.Supplier<?>>) (key, read) -> out.add(
                DebugProperty.number(this, key, "Layout", read,
                        key.startsWith("offset") || key.equals("x") || key.equals("y") ? -100000 : key.equals("sizeFlagsStretchRatio") ? 0.0001 : 0, 100000));
        number.accept("minimumWidth", this::getMinimumWidth);
        number.accept("minimumHeight", this::getMinimumHeight);
        number.accept("preferredWidth", () -> debugValue("preferredWidth", preferredWidth));
        number.accept("preferredHeight", () -> debugValue("preferredHeight", preferredHeight));
        number.accept("offsetX", () -> getOffset().x());
        number.accept("offsetY", () -> getOffset().y());
        if (!(parent instanceof Container) && lifecycleOwner == null && !(this instanceof Window)) {
            number.accept("x", () -> getDebugRequestedBounds().x());
            number.accept("y", () -> getDebugRequestedBounds().y());
            number.accept("width", () -> getDebugRequestedBounds().width());
            number.accept("height", () -> getDebugRequestedBounds().height());
        }
    }

    /**
     * The parent's assigned rectangle, before this control's Offset is applied.
     */
    public Rect getLayoutBounds() {
        return bounds;
    }

    public Rect getBounds() {
        return bounds.translated(getOffset().x(), getOffset().y());
    }

    /**
     * The size assigned by the most recent layout pass.
     */
    public Size getSize() {
        return new Size(bounds.width(), bounds.height());
    }

    public Rect getAbsoluteBounds() {
        Rect local = getBounds();
        if (parent == null) return local;
        Rect origin = parent.getAbsoluteBounds();
        return local.translated(origin.x(), origin.y());
    }



    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        int minimumWidth = DebugBridge.value(this, "minimumWidth", this.minimumWidth);
        int preferredWidth = DebugBridge.value(this, "preferredWidth", this.preferredWidth);
        return Math.max(minimumWidth, preferredWidth);
    }

    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        int minimumHeight = DebugBridge.value(this, "minimumHeight", this.minimumHeight);
        int preferredHeight = DebugBridge.value(this, "preferredHeight", this.preferredHeight);
        return Math.max(minimumHeight, preferredHeight);
    }

    /**
     * The desired size before layout, which may differ from the current size.
     */
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        int minimumWidth = DebugBridge.value(this, "minimumWidth", this.minimumWidth);
        int minimumHeight = DebugBridge.value(this, "minimumHeight", this.minimumHeight);
        return new Size(Math.max(minimumWidth, measureWidth(font, available.width())),
                Math.max(minimumHeight, measureHeight(font, available.height())));
    }

    /**
     * Containers use this envelope so overrides of natural measurement still respect preferences.
     */
    public final Size desiredSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        Size natural = measureSize(font, available);
        int width = Math.max(natural.width(), debugValue("preferredWidth", preferredWidth));
        int height = Math.max(natural.height(), debugValue("preferredHeight", preferredHeight));
        return width == natural.width() && height == natural.height() ? natural : new Size(width, height);
    }

    /**
     * Coordinates are relative to the parent control.
     */
    public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            if (bounds.width() != localBounds.width() || bounds.height() != localBounds.height())
                LifecycleScheduler.resized(this);
            layoutFont = font;
            bounds = localBounds;
            layoutDirty = false;
            if (!(this instanceof Container))
                for (Control child : getChildren())
                    if (child.parent == this && child.isAlive() && child.getVisible())
                        child.layout(child.getDebugRequestedBounds(), font);
        }
    }

    public final void draw(DrawContext context, int screenX, int screenY) {
        if (!isAlive() || !getVisible()) return;
        try(var phase=DispatchGuard.enter(); var transform=getRenderTransform().equals(Transform2D.IDENTITY) ? (DrawContext.Scope)() -> { } : context.pushControlTransform(this,screenX,screenY)) {
            drawContent(context,screenX,screenY);
        }
    }

    protected void drawContent(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var phase = DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            List<Control> snapshot = getChildren();
            render(context, screenX, screenY);
            for (Control child : snapshot)
                if (child.parent == this && child.isAlive() && child.getVisible())
                    child.draw(context, screenX, screenY);
        }
    }

    protected void render(DrawContext context, int screenX, int screenY) {
    }

    /**
     * Window paints only the topmost eligible tooltip after all scrolling clips have closed.
     */
    public boolean hasTooltip() {
        return false;
    }

    public void renderTooltip(DrawContext context, int mouseX, int mouseY) {
    }

    /**
     * Called only by the owning Window, in target-to-parent order.
     */
    public void input(InputEvent event) {
        boolean handled = false;
        if (event instanceof CancelEvent) {
            cancelInteraction();
            return;
        }
        if (event instanceof PointerEvent pointer && pointer.button() == 0) {
            handled = switch (pointer.action()) {
                case PRESS -> pointerDown(event.treeX(), event.treeY());
                case RELEASE -> pointerUp(event.treeX(), event.treeY(), pointer.over());
                case MOVE -> pointerMove(event.treeX(), event.treeY());
            };
        } else if (event instanceof ScrollEvent scroll)
            handled = wheel(event.treeX(), event.treeY(), scroll.delta(), scroll.horizontal());
        if (handled) event.accept();
    }

    protected boolean pointerDown(int x, int y) {
        return false;
    }

    protected boolean wheel(int x, int y, double delta, boolean horizontal) {
        return false;
    }

    public Rect getScreenBounds(int screenX, int screenY) {
        return getAbsoluteBounds().translated(screenX, screenY);
    }

    public boolean hit(int rootX, int rootY) {
        return isAlive() && visible && getAbsoluteBounds().contains(rootX, rootY);
    }

    /**
     * Last drawn child is hit first. Children may overflow their parent's bounds.
     */
    public final Control hitTop(int windowX,int windowY) {
        return io.github.luyingyilong.ore.event.EventGeometry.hitTop(this,windowX,windowY);
    }

    /** Framework entry; tree coordinates are adapted once, while child traversal retains Window coordinates. */
    public final Control hitTestGeometry(int treeX,int treeY,int windowX,int windowY) {
        return hitTestTree(treeX,treeY,windowX,windowY);
    }

    protected Control hitTestTree(int rootX,int rootY,int windowX,int windowY) {
        List<Control> snapshot=getChildren();
        for(int i=snapshot.size()-1;i>=0;i--) {
            Control child=snapshot.get(i);
            if(child.parent!=this)continue;
            Control hit=child.hitTop(windowX,windowY);
            if(hit!=null)return hit;
        }
        return hit(rootX,rootY)?this:null;
    }
}
