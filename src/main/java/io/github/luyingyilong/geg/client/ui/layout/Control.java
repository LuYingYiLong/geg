package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** A local-coordinate layout node. Window owns keyboard focus; ModularUI edits text. */
public class Control {
    private Control parent;
    private final List<Control> children = new ArrayList<>();
    private Rect bounds = new Rect(0, 0, 0, 0);
    private Rect requestedBounds = new Rect(0, 0, 0, 0);
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
        if (sizeFlagsHorizontal != flags) {
            sizeFlagsHorizontal = flags;
            requestLayout();
        }
        return this;
    }

    public Control sizeFlagsVertical(int flags) {
        SizeFlags.validate(flags);
        if (sizeFlagsVertical != flags) {
            sizeFlagsVertical = flags;
            requestLayout();
        }
        return this;
    }

    public Control sizeFlagsStretchRatio(float ratio) {
        if (!Float.isFinite(ratio) || ratio <= 0.0F)
            throw new IllegalArgumentException("Stretch ratio must be positive");
        if (sizeFlagsStretchRatio != ratio) {
            sizeFlagsStretchRatio = ratio;
            requestLayout();
        }
        return this;
    }

    public int sizeFlagsHorizontal() { return sizeFlagsHorizontal; }
    public int sizeFlagsVertical() { return sizeFlagsVertical; }
    public float sizeFlagsStretchRatio() { return sizeFlagsStretchRatio; }

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

    public int minimumWidth() { return minimumWidth; }
    public int minimumHeight() { return minimumHeight; }
    public Size minimumSize() { return new Size(minimumWidth, minimumHeight); }
    public boolean visible() { return visible; }
    public void visible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            if (!visible) cancelInteractions();
            requestLayout();
        }
    }
    public Rect bounds() { return bounds; }
    /** The size assigned by the most recent layout pass. */
    public Size size() { return new Size(bounds.width(), bounds.height()); }

    public Rect absoluteBounds() {
        return parent == null ? bounds : bounds.translated(parent.absoluteBounds().x(), parent.absoluteBounds().y());
    }

    protected void attach(Control child) {
        addChild(child);
    }

    public int measureWidth(Font font, int available) { return Math.max(minimumWidth, preferredWidth); }
    public int measureHeight(Font font, int available) { return Math.max(minimumHeight, preferredHeight); }
    /** The desired size before layout, which may differ from the current size. */
    public Size measureSize(Font font, Size available) {
        return new Size(Math.max(minimumWidth, measureWidth(font, available.width())),
                Math.max(minimumHeight, measureHeight(font, available.height())));
    }

    /** Coordinates are relative to the parent control. */
    public void layout(Rect localBounds, Font font) {
        bounds = localBounds;
        layoutDirty = false;
        if (!(this instanceof Container))
            for (Control child : children)
                if (child.visible()) child.layout(child.requestedBounds, font);
    }

    public void draw(ModularGuiContext context, int screenX, int screenY) {
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
