package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.signal.*;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.debug.DebugProperty;
import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** A local-coordinate layout node. Window owns keyboard focus; ModularUI edits text. */
public class Control implements SignalOwner {
    private final SignalScope signalScope = new SignalScope();
    private final SignalSource0 focusEntered = new SignalSource0(this, "focusEntered");
    private final SignalSource0 focusExited = new SignalSource0(this, "focusExited");
    private final SignalSource<Boolean> visibilityChanged = new SignalSource<>(this, "visibilityChanged", "Boolean");
    private LifecycleState lifecycleState = LifecycleState.ALIVE;
    private Control lifecycleOwner;
    private final java.util.LinkedHashMap<String, Control> parts = new java.util.LinkedHashMap<>();
    private final java.util.Set<Control> pooled = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private List<Control> freeOwned = List.of();
    private io.github.luyingyilong.ore.window.WindowContext treeContext;
    private boolean insideTree, ready, suspended, effectiveVisible, enteredDelivered;
    private long treeEpoch;
    private String freeReason = "", freeSource = "";
    private boolean signalFocused;
    private SignalScope bindingScope;

    @Override public final SignalScope signalScope() { return signalScope; }
    public final Signal0 focusEntered() { return focusEntered.signal(); }
    public final Signal0 focusExited() { return focusExited.signal(); }
    public final Signal<Boolean> visibilityChanged() { return visibilityChanged.signal(); }
    public final boolean isDisposed() { return lifecycleState == LifecycleState.FREED; }
    /** Connections capturing the current virtual row/item belong here, not to the reusable view. */
    public final SignalScope bindingScope() {
        signalScope.checkThread();
        if (!isAlive()) throw new IllegalStateException("Control has been disposed");
        if (bindingScope == null) bindingScope = signalScope.child("binding");
        return bindingScope;
    }
    public final void resetBindingScope() {
        if (bindingScope != null) { bindingScope.close(SignalDiagnostics.Reason.BINDING_RESET); bindingScope = null; }
        for (Control child : ownedSnapshot()) if (child.isAlive()) child.resetBindingScope();
    }

    public final LifecycleState lifecycleState() { return lifecycleState; }
    public final boolean isAlive() { return lifecycleState == LifecycleState.ALIVE; }
    public final boolean isQueuedForDeletion() { return lifecycleState == LifecycleState.QUEUED_FOR_FREE || lifecycleState == LifecycleState.FREEING; }
    public final boolean isInsideTree() { return insideTree; }
    public final boolean isReady() { return ready; }
    public final boolean isSuspended() { return suspended; }
    public final LifecycleInfo lifecycleInfo() {
        return new LifecycleInfo(SignalDiagnostics.ownerId(this), debugName.isEmpty() ? getClass().getSimpleName() : debugName,
                lifecycleState, insideTree, ready, suspended, freeReason, freeSource);
    }
    public final void queueFree() { queueFree("direct request"); }
    /** Hosts may supply a diagnostic reason; subsequent requests never overwrite the first. */
    public final void queueFree(String reason) {
        signalScope.checkThread();
        if (!isAlive()) return;
        Objects.requireNonNull(reason);
        String source = DebugBridge.active() ? StackWalker.getInstance().walk(frames -> frames
                .filter(f -> !f.getClassName().equals(Control.class.getName()))
                .limit(8).map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1)
                        + "." + f.getMethodName() + "(" + f.getFileName() + ":" + f.getLineNumber() + ")").collect(java.util.stream.Collectors.joining("\n"))) : "";
        markForFree(reason, source);
        LifecycleScheduler.enqueue(this);
        Window window = treeContext == null ? null : treeContext.owner();
        if (window != null) { window.cancelCaptures(this, CancelEvent.Reason.DETACHED); window.focusManager().forgetSubtree(this); }
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
        freeReason = reason; freeSource = source;
        freeOwned = ownedSnapshot();
        publishLifecycle();
        for (Control child : freeOwned) child.markForFree(child.parent == this ? "parent cleanup: " + debugName()
                : "owned part cleanup: " + debugName(), source);
    }
    /** Register a non-layout component. Its active-tree lifetime follows this owner. */
    public final <T extends Control> T ownPart(String name, T part) {
        Objects.requireNonNull(name); Objects.requireNonNull(part);
        requireOwnership(part);
        Control previous = parts.get(name);
        if (previous != null && previous != part) throw new IllegalStateException("Part name already in use: " + name);
        parts.put(name, part); ((Control) part).lifecycleOwner = this;
        if (insideTree) { part.enterTree(treeContext, suspended, effectiveVisible); LifecycleScheduler.prepare(part); }
        return part;
    }
    /** Pool ownership survives temporary detachment; it does not activate a detached view. */
    public final <T extends Control> T ownPooled(T part) {
        requireOwnership(part); pooled.add(part); ((Control) part).lifecycleOwner = this; return part;
    }
    private void requireOwnership(Control part) {
        signalScope.checkThread(); part.signalScope.checkThread();
        if (!isAlive() || !part.isAlive()) throw new IllegalStateException("Cannot own a pending/freed control");
        if (part.lifecycleOwner != null && part.lifecycleOwner != this || part.parent != null && part.parent != this)
            throw new IllegalStateException("Control already has a lifecycle owner");
        for (Control node = this; node != null; node = node.parent != null ? node.parent : node.lifecycleOwner)
            if (node == part) throw new IllegalArgumentException("Lifecycle ownership cycle");
    }
    public final java.util.Map<String, Control> ownedParts() { return java.util.Map.copyOf(parts); }
    private List<Control> ownedSnapshot() {
        java.util.Set<Control> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Control> result = new ArrayList<>();
        for (Control c : children()) if (c.parent == this && seen.add(c)) result.add(c);
        for (Control c : List.copyOf(parts.values())) if (seen.add(c)) result.add(c);
        for (Control c : List.copyOf(pooled)) if (seen.add(c)) result.add(c);
        return result;
    }
    private List<Control> activeOwnedSnapshot() {
        java.util.Set<Control> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Control> result = new ArrayList<>();
        for (Control c : children()) if (c.parent == this && seen.add(c)) result.add(c);
        for (Control c : List.copyOf(parts.values())) if (seen.add(c)) result.add(c);
        return result;
    }
    protected void notification(ControlNotification notification) { }
    final ControlNotification notificationSnapshot(ControlNotification.Type type) {
        return new ControlNotification(type, treeContext, insideTree, ready, effectiveVisible, treeEpoch);
    }
    final void deliverNotification(ControlNotification notice) {
        if (isDisposed()) return;
        if (!isAlive() && notice.type() == ControlNotification.Type.READY) return;
        if (notice.type() == ControlNotification.Type.ENTER_TREE && notice.treeEpoch() == treeEpoch) enteredDelivered = true;
        try { notification(notice); }
        catch (RuntimeException error) { lifecycleError("notification " + notice.type(), error); }
    }
    private void lifecycleError(String phase, RuntimeException error) {
        System.getLogger(Control.class.getName()).log(System.Logger.Level.ERROR, "Ore " + phase + ": " + debugName(), error);
    }
    final void enterTree(io.github.luyingyilong.ore.window.WindowContext context, boolean paused, boolean ancestorVisible) {
        if (!isAlive() || insideTree) return;
        treeContext = context; insideTree = true; enteredDelivered = false; treeEpoch++; suspended = paused; effectiveVisible = ancestorVisible && visible;
        LifecycleScheduler.notify(this, ControlNotification.Type.ENTER_TREE);
        for (Control c : activeOwnedSnapshot()) c.enterTree(context, paused, effectiveVisible);
    }
    /** Input proxies wait for their host's native adapter; ordinary controls are immediately ready. */
    protected boolean lifecycleReady() { return true; }
    final void readySubtree() {
        if (!insideTree || !isAlive()) return;
        for (Control c : activeOwnedSnapshot()) c.readySubtree();
        if (ready || !enteredDelivered || !lifecycleReady()) return;
        if (activeOwnedSnapshot().stream().anyMatch(c -> c.isAlive() && c.insideTree && !c.ready)) return;
        ready = true;
        deliverNotification(notificationSnapshot(ControlNotification.Type.READY));
        Control owner = parent != null ? parent : lifecycleOwner;
        if (owner != null && owner.insideTree && !owner.ready) LifecycleScheduler.prepare(owner);
    }
    final void exitTree() {
        if (!insideTree) return;
        for (Control c : activeOwnedSnapshot()) c.exitTree();
        insideTree = false; suspended = false; effectiveVisible = false;
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
        if (effectiveVisible != next) { effectiveVisible = next; LifecycleScheduler.notify(this, ControlNotification.Type.VISIBILITY_CHANGED); }
        for (Control c : activeOwnedSnapshot()) c.updateEffectiveVisibility(next);
    }
    final void freeNow() {
        if (lifecycleState != LifecycleState.QUEUED_FOR_FREE) return;
        try { preFreeSubtree(); LifecycleScheduler.notifications(); }
        finally { try { exitTree(); LifecycleScheduler.notifications(); } finally { finishFree(); } }
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
                try { if (c.lifecycleState == LifecycleState.QUEUED_FOR_FREE) c.freeNow(); else c.finishFree(); }
                catch (RuntimeException error) { lifecycleError("owned cleanup", error); }
            }
        } finally {
            try { signalScope.close(SignalDiagnostics.Reason.OWNER_DISPOSED); }
            finally {
                if (parent != null) {
                    Control old = parent;
                    try { old.removeChild(this); }
                    catch (RuntimeException error) { lifecycleError("detach during cleanup", error); }
                    finally { old.children.removeIf(node -> node == this); parent = null; old.requestLayout(); }
                }
                if (lifecycleOwner != null) { lifecycleOwner.parts.values().removeIf(c -> c == this); lifecycleOwner.pooled.remove(this); lifecycleOwner = null; }
                children.clear(); parts.clear(); pooled.clear(); freeOwned = List.of(); bindingScope = null;
                lifecycleState = LifecycleState.FREED;
                publishLifecycle(); treeContext = null;
            }
        }
    }

    private void publishLifecycle() {
        if (!DebugBridge.active()) return;
        try { DebugBridge.backend().lifecycle(this, lifecycleInfo()); }
        catch (RuntimeException error) { lifecycleError("diagnostic observer", error); }
    }

    public final void notifyFocusChanged(boolean focused) {
        onFocusChanged(focused);
        if (signalFocused == focused) return;
        signalFocused = focused;
        if (focused) focusEntered.emit(); else focusExited.emit();
    }
    private String debugName = "";
    private Control parent;
    private final List<Control> children = new ArrayList<>();
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
    public Font layoutFont(){return layoutFont;}
    private int focusExpansion;
    private float sizeFlagsStretchRatio = 1.0F;

    public Control minimum(int width, int height) {
        minimumWidth = Math.max(0, width);
        minimumHeight = Math.max(0, height);
        requestLayout();
        return this;
    }

    public Control minimum(Size size) {
        return minimum(size.width(), size.height());
    }

    public Control preferred(int width, int height) {
        preferredWidth = Math.max(0, width);
        preferredHeight = Math.max(0, height);
        requestLayout();
        return this;
    }

    public Control sizeFlagsHorizontal(int flags) {
        SizeFlags.validate(flags);
        if (DebugBridge.previewWrite(this, "sizeFlagsHorizontal", flags)) return this;
        if (sizeFlagsHorizontal != flags) {
            sizeFlagsHorizontal = flags;
            requestLayout();
        }
        return this;
    }

    public Control sizeFlagsVertical(int flags) {
        SizeFlags.validate(flags);
        if (DebugBridge.previewWrite(this, "sizeFlagsVertical", flags)) return this;
        if (sizeFlagsVertical != flags) {
            sizeFlagsVertical = flags;
            requestLayout();
        }
        return this;
    }

    public Control sizeFlagsStretchRatio(float ratio) {
        if (!Float.isFinite(ratio) || ratio <= 0.0F)
            throw new IllegalArgumentException("Stretch ratio must be positive");
        if (DebugBridge.previewWrite(this, "sizeFlagsStretchRatio", ratio)) return this;
        if (sizeFlagsStretchRatio != ratio) {
            sizeFlagsStretchRatio = ratio;
            requestLayout();
        }
        return this;
    }

    @Inspectable(min = 0, max = 15)
    public int sizeFlagsHorizontal() {
        int sizeFlagsHorizontal = DebugBridge.value(this, "sizeFlagsHorizontal", this.sizeFlagsHorizontal);
        return sizeFlagsHorizontal;
    }
    @Inspectable(min = 0, max = 15)
    public int sizeFlagsVertical() {
        int sizeFlagsVertical = DebugBridge.value(this, "sizeFlagsVertical", this.sizeFlagsVertical);
        return sizeFlagsVertical;
    }
    @Inspectable(min = 0.0001, max = 100000)
    public float sizeFlagsStretchRatio() {
        float sizeFlagsStretchRatio = DebugBridge.value(this, "sizeFlagsStretchRatio", this.sizeFlagsStretchRatio);
        return sizeFlagsStretchRatio;
    }

    public Control focusable(boolean focusable) {
        this.focusable = focusable;
        return this;
    }

    public boolean focusable() { return focusable; }

    /** Pixels added to each side of the focused outline. */
    public Control focusExpansion(int pixels) {
        focusExpansion = Math.max(0, pixels);
        return this;
    }

    public int focusExpansion() { return focusExpansion; }

    public boolean acceptsFocus() { return isAlive() && !suspended && focusable && visible; }

    public boolean focusScope() { return false; }

    /** Z translation applied by this node when it paints its subtree. */
    public float contentDepth() { return 0.0F; }

    /** Bounds in Window coordinates; virtual controls may return their active item's bounds. */
    public Rect focusBounds() { return absoluteBounds(); }

    public boolean onFocusKey(int keyCode, int modifiers) { return false; }

    public boolean onFocusMove(FocusManager.Direction direction) { return false; }

    public boolean activate() { return false; }

    /** Paired keyboard activation; simple controls may still activate on the initial press. */
    public boolean onActivationPressed(int keyCode) { return activate(); }
    public boolean onActivationReleased(int keyCode) { return false; }
    protected boolean pointerUp(boolean over) { return false; }
    protected boolean pointerUp(int rootX, int rootY, boolean over) { return pointerUp(over); }
    /** Coordinates use the same unscrolled tree space as pointer presses and releases. */
    protected boolean pointerMove(int rootX, int rootY) { return false; }
    public void cancelInteraction() { }

    public final void cancelInteractions() {
        try { cancelInteraction(); }
        catch (RuntimeException error) { lifecycleError("cancel interaction", error); }
        finally { for (Control child : ownedSnapshot()) child.cancelInteractions(); }
    }

    public void onFocusChanged(boolean focused) { }
    public io.github.luyingyilong.ore.window.WindowContext windowContext() {
        for (Control node = this; node != null; node = node.parent())
            if (node instanceof Window window) return window.context();
        if (treeContext != null) return treeContext;
        if (lifecycleOwner != null) return lifecycleOwner.windowContext();
        return io.github.luyingyilong.ore.window.WindowContext.current();
    }
    public Control parent() { return parent; }
    public List<Control> children() { return List.copyOf(children); }

    public Control addChild(Control child) {
        Objects.requireNonNull(child);
        signalScope.checkThread();
        if (!isAlive() || !child.isAlive()) throw new IllegalStateException("Cannot attach a disposed control");
        if (child.parent != null) throw new IllegalStateException("Control already has a parent");
        if (child == this) throw new IllegalArgumentException("Control cannot parent itself");
        for (Control ancestor = this; ancestor != null; ancestor = ancestor.parent)
            if (ancestor == child) throw new IllegalArgumentException("Control tree cannot contain a cycle");
        child.signalScope.checkThread();
        if (child.lifecycleOwner != null) {
            boolean sameOwner = false;
            for (Control n = this; n != null; n = n.parent) if (n == child.lifecycleOwner) sameOwner = true;
            if (!sameOwner) throw new IllegalStateException("Control belongs to another lifecycle owner");
        }
        child.parent = this;
        children.add(child);
        if (insideTree) { child.enterTree(treeContext, suspended, effectiveVisible); LifecycleScheduler.prepare(child); }
        requestLayout();
        return this;
    }

    public boolean removeChild(Control child) {
        signalScope.checkThread();
        if (child == null || child.parent != this) return false;
        if(windowContext().owner()!=null)windowContext().owner().focusManager().forgetSubtree(child);
        child.exitTree();
        children.removeIf(node -> node == child);
        child.cancelInteractions();
        DebugBridge.backend().removed(child);
        child.parent = null;
        requestLayout();
        return true;
    }

    /** Free controls can place children; a Container owns its children's rectangles. */
    public Control position(int x, int y) {
        requireFreePlacement();
        requestedBounds = new Rect(x, y, requestedBounds.width(), requestedBounds.height());
        requestLayout();
        return this;
    }

    public Control requestedSize(int width, int height) {
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
    }

    public boolean layoutDirty() { return layoutDirty; }

    /** Resource reloads can change glyph advances without changing the text itself. */
    public void invalidateTextLayout() {
        requestLayout();
        for (Control child : ownedSnapshot()) if (child.isAlive()) child.invalidateTextLayout();
    }

    public int minimumWidth() {
        int minimumWidth = DebugBridge.value(this, "minimumWidth", this.minimumWidth);
        return minimumWidth;
    }
    public int minimumHeight() {
        int minimumHeight = DebugBridge.value(this, "minimumHeight", this.minimumHeight);
        return minimumHeight;
    }
    public Size minimumSize() { return new Size(minimumWidth(), minimumHeight()); }
    public boolean visible() { return visible; }
    public void visible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            if (!visible) {
                if(windowContext().owner()!=null)windowContext().owner().cancelCaptures(this,CancelEvent.Reason.DETACHED);
                cancelInteractions();
            }
            updateEffectiveVisibility(parent != null ? parent.effectiveVisible : lifecycleOwner != null ? lifecycleOwner.effectiveVisible : true);
            requestLayout();
            visibilityChanged.emit(visible);
        }
    }
    /** Layout translation shared by rendering, descendants, hit testing and focus. */
    public Control offset(Offset offset) {
        Offset next = Objects.requireNonNull(offset);
        if (!this.offset.equals(next)) { this.offset = next; requestLayout(); }
        return this;
    }
    public Control offset(int x, int y) { return offset(new Offset(x, y)); }
    public Offset offset() { if (!DebugBridge.active()) return offset; return new Offset(debugValue("offsetX", offset.x()), debugValue("offsetY", offset.y())); }
    protected final <T> T debugValue(String key, T base) { return DebugBridge.value(this, key, base); }
    protected final int debugValue(String key, int base) { return DebugBridge.value(this, key, base); }
    public String debugName() { return debugName; }
    public Control debugName(String name) { debugName = java.util.Objects.requireNonNull(name); return this; }
    public Rect debugRequestedBounds() { if (!DebugBridge.active()) return requestedBounds; return new Rect(debugValue("x", requestedBounds.x()), debugValue("y", requestedBounds.y()),
            debugValue("width", requestedBounds.width()), debugValue("height", requestedBounds.height())); }
    /** Complex/forwarded properties only; the development Inspector also discovers @Inspectable getters. */
    public void debugProperties(java.util.List<DebugProperty> out) {
        var number = (java.util.function.BiConsumer<String, java.util.function.Supplier<?>>)(key, read) -> out.add(
                DebugProperty.number(this, key, "Layout", read,
                key.startsWith("offset") || key.equals("x") || key.equals("y") ? -100000 : key.equals("sizeFlagsStretchRatio") ? 0.0001 : 0, 100000));
        number.accept("minimumWidth", this::minimumWidth); number.accept("minimumHeight", this::minimumHeight);
        number.accept("preferredWidth", () -> debugValue("preferredWidth", preferredWidth));
        number.accept("preferredHeight", () -> debugValue("preferredHeight", preferredHeight));
        number.accept("offsetX", () -> offset().x()); number.accept("offsetY", () -> offset().y());
        if (!(parent instanceof Container) && !(this instanceof Window)) {
            number.accept("x", () -> debugRequestedBounds().x()); number.accept("y", () -> debugRequestedBounds().y());
            number.accept("width", () -> debugRequestedBounds().width()); number.accept("height", () -> debugRequestedBounds().height());
        }
    }
    /** The parent's assigned rectangle, before this control's Offset is applied. */
    public Rect layoutBounds() { return bounds; }
    public Rect bounds() { return bounds.translated(offset().x(), offset().y()); }
    /** The size assigned by the most recent layout pass. */
    public Size size() { return new Size(bounds.width(), bounds.height()); }

    public Rect absoluteBounds() {
        Rect local = bounds();
        if (parent == null) return local;
        Rect origin = parent.absoluteBounds();
        return local.translated(origin.x(), origin.y());
    }

    protected void attach(Control child) {
        addChild(child);
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
    /** The desired size before layout, which may differ from the current size. */
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        int minimumWidth = DebugBridge.value(this, "minimumWidth", this.minimumWidth);
        int minimumHeight = DebugBridge.value(this, "minimumHeight", this.minimumHeight);
        return new Size(Math.max(minimumWidth, measureWidth(font, available.width())),
                Math.max(minimumHeight, measureHeight(font, available.height())));
    }

    /** Containers use this envelope so overrides of natural measurement still respect preferences. */
    public final Size desiredSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        Size natural = measureSize(font, available);
        int width = Math.max(natural.width(), debugValue("preferredWidth", preferredWidth));
        int height = Math.max(natural.height(), debugValue("preferredHeight", preferredHeight));
        return width == natural.width() && height == natural.height() ? natural : new Size(width, height);
    }

    /** Coordinates are relative to the parent control. */
    public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            if (bounds.width() != localBounds.width() || bounds.height() != localBounds.height()) LifecycleScheduler.resized(this);
            layoutFont=font;
            bounds = localBounds;
            layoutDirty = false;
            if (!(this instanceof Container))
                for (Control child : children())
                    if (child.parent == this && child.isAlive() && child.visible()) child.layout(child.debugRequestedBounds(), font);
        }
    }

    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var phase = DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            List<Control> snapshot = children();
            render(context, screenX, screenY);
            for (Control child : snapshot)
                if (child.parent == this && child.isAlive() && child.visible()) child.draw(context, screenX, screenY);
        }
    }

    protected void render(ModularGuiContext context, int screenX, int screenY) {
    }

    /** Window paints only the topmost eligible tooltip after all scrolling clips have closed. */
    public boolean hasTooltip() { return false; }
    public void renderTooltip(ModularGuiContext context, int mouseX, int mouseY) { }

    /** Called only by the owning Window, in target-to-parent order. */
    public void input(InputEvent event) {
        boolean handled = false;
        if (event instanceof CancelEvent) { cancelInteraction(); return; }
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

    protected boolean pointerDown(int x, int y) { return false; }
    protected boolean wheel(int x, int y, double delta, boolean horizontal) { return false; }

    public Rect screenBounds(int screenX, int screenY) {
        return absoluteBounds().translated(screenX, screenY);
    }

    public boolean hit(int rootX, int rootY) {
        return isAlive() && visible && absoluteBounds().contains(rootX, rootY);
    }

    /** Last drawn child is hit first. Children may overflow their parent's bounds. */
    public Control hitTop(int rootX, int rootY) {
        if (!isAlive() || !visible) return null;
        List<Control> snapshot = children();
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            Control child = snapshot.get(index);
            if (child.parent != this || !child.isAlive()) continue;
            Control hit = child.hitTop(rootX, rootY);
            if (hit != null) return hit;
        }
        return hit(rootX, rootY) ? this : null;
    }
}
