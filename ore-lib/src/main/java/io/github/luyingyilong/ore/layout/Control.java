package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.debug.DebugProperty;
import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** A local-coordinate layout node. Window owns keyboard focus; ModularUI edits text. */
public class Control {
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

    public boolean acceptsFocus() { return focusable && visible; }

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
    public boolean releasePointer(boolean over) { return false; }
    public boolean releasePointer(int rootX, int rootY, boolean over) { return releasePointer(over); }
    /** Coordinates use the same unscrolled tree space as pointer presses and releases. */
    public boolean dragPointer(int rootX, int rootY) { return false; }
    /** A composite control captures itself even when a non-interactive child was hit. */
    protected final void capturePointer() {
        for (Control node = this; node != null; node = node.parent())
            if (node instanceof Window window) { window.capturePointer(this); return; }
    }
    public void cancelInteraction() { }

    public final void cancelInteractions() {
        cancelInteraction();
        for (Control child : children) child.cancelInteractions();
    }

    public void onFocusChanged(boolean focused) { }
    public io.github.luyingyilong.ore.window.WindowContext windowContext() {
        for (Control node = this; node != null; node = node.parent())
            if (node instanceof Window window) return window.context();
        return io.github.luyingyilong.ore.window.WindowContext.current();
    }
    public Control parent() { return parent; }
    public List<Control> children() { return List.copyOf(children); }

    public Control addChild(Control child) {
        Objects.requireNonNull(child);
        if (child.parent != null) throw new IllegalStateException("Control already has a parent");
        if (child == this) throw new IllegalArgumentException("Control cannot parent itself");
        for (Control ancestor = this; ancestor != null; ancestor = ancestor.parent)
            if (ancestor == child) throw new IllegalArgumentException("Control tree cannot contain a cycle");
        child.parent = this;
        children.add(child);
        requestLayout();
        return this;
    }

    public boolean removeChild(Control child) {
        if (!children.remove(child)) return false;
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
        for (Control child : children) child.invalidateTextLayout();
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
            if (!visible) cancelInteractions();
            requestLayout();
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
        int minimumWidth = DebugBridge.value(this, "minimumWidth", this.minimumWidth);
        int preferredWidth = DebugBridge.value(this, "preferredWidth", this.preferredWidth);
        return Math.max(minimumWidth, preferredWidth);
    }
    public int measureHeight(Font font, int available) {
        int minimumHeight = DebugBridge.value(this, "minimumHeight", this.minimumHeight);
        int preferredHeight = DebugBridge.value(this, "preferredHeight", this.preferredHeight);
        return Math.max(minimumHeight, preferredHeight);
    }
    /** The desired size before layout, which may differ from the current size. */
    public Size measureSize(Font font, Size available) {
        int minimumWidth = DebugBridge.value(this, "minimumWidth", this.minimumWidth);
        int minimumHeight = DebugBridge.value(this, "minimumHeight", this.minimumHeight);
        return new Size(Math.max(minimumWidth, measureWidth(font, available.width())),
                Math.max(minimumHeight, measureHeight(font, available.height())));
    }

    /** Containers use this envelope so overrides of natural measurement still respect preferences. */
    public final Size desiredSize(Font font, Size available) {
        Size natural = measureSize(font, available);
        int width = Math.max(natural.width(), debugValue("preferredWidth", preferredWidth));
        int height = Math.max(natural.height(), debugValue("preferredHeight", preferredHeight));
        return width == natural.width() && height == natural.height() ? natural : new Size(width, height);
    }

    /** Coordinates are relative to the parent control. */
    public void layout(Rect localBounds, Font font) {
        bounds = localBounds;
        layoutDirty = false;
        if (!(this instanceof Container))
            for (Control child : children)
                if (child.visible()) child.layout(child.debugRequestedBounds(), font);
    }

    public void draw(ModularGuiContext context, int screenX, int screenY) {
        DebugBridge.backend().paint(this, context, screenX, screenY);
        render(context, screenX, screenY);
        for (Control child : children)
            if (child.visible()) child.draw(context, screenX, screenY);
    }

    protected void render(ModularGuiContext context, int screenX, int screenY) {
    }

    /** Window paints only the topmost eligible tooltip after all scrolling clips have closed. */
    public boolean hasTooltip() { return false; }
    public void renderTooltip(ModularGuiContext context, int mouseX, int mouseY) { }

    /** Dispatches in reverse draw order; clipping containers can narrow this route. */
    public boolean dispatchPointer(int rootX, int rootY) {
        if (!visible) return false;
        for (int index = children.size() - 1; index >= 0; index--)
            if (children.get(index).dispatchPointer(rootX, rootY)) return true;
        return onPointerPressed(rootX, rootY);
    }

    protected boolean onPointerPressed(int rootX, int rootY) { return false; }

    /** Routes wheel input through the same tree, with the topmost child first. */
    public boolean dispatchScroll(int rootX, int rootY, double delta, boolean horizontal) {
        if (!visible) return false;
        for (int index = children.size() - 1; index >= 0; index--)
            if (children.get(index).dispatchScroll(rootX, rootY, delta, horizontal)) return true;
        return false;
    }

    public Rect screenBounds(int screenX, int screenY) {
        return absoluteBounds().translated(screenX, screenY);
    }

    public boolean hit(int rootX, int rootY) {
        return visible && absoluteBounds().contains(rootX, rootY);
    }

    /** Last drawn child is hit first. Children may overflow their parent's bounds. */
    public Control hitTop(int rootX, int rootY) {
        if (!visible) return null;
        for (int index = children.size() - 1; index >= 0; index--) {
            Control hit = children.get(index).hitTop(rootX, rootY);
            if (hit != null) return hit;
        }
        return hit(rootX, rootY) ? this : null;
    }
}
