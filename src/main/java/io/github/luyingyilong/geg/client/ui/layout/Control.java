package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.Objects;

/** A local-coordinate layout node; ModularUI remains responsible for focus and text editing. */
public class Control {
    public enum SizePolicy { CONTENT, FILL, EXPAND }

    private Control parent;
    private Rect bounds = new Rect(0, 0, 0, 0);
    private int minimumWidth;
    private int minimumHeight;
    private int preferredWidth;
    private int preferredHeight;
    private SizePolicy horizontal = SizePolicy.CONTENT;
    private SizePolicy vertical = SizePolicy.CONTENT;
    private boolean visible = true;

    public Control minimum(int width, int height) {
        minimumWidth = Math.max(0, width);
        minimumHeight = Math.max(0, height);
        return this;
    }

    public Control minimum(Size size) {
        return minimum(size.width(), size.height());
    }

    public Control preferred(int width, int height) {
        preferredWidth = Math.max(0, width);
        preferredHeight = Math.max(0, height);
        return this;
    }

    public Control policy(SizePolicy horizontal, SizePolicy vertical) {
        this.horizontal = Objects.requireNonNull(horizontal);
        this.vertical = Objects.requireNonNull(vertical);
        return this;
    }

    public SizePolicy horizontalPolicy() { return horizontal; }
    public SizePolicy verticalPolicy() { return vertical; }
    public int minimumWidth() { return minimumWidth; }
    public int minimumHeight() { return minimumHeight; }
    public Size minimumSize() { return new Size(minimumWidth, minimumHeight); }
    public boolean visible() { return visible; }
    public void visible(boolean visible) { this.visible = visible; }
    public Rect bounds() { return bounds; }
    /** The size assigned by the most recent layout pass. */
    public Size size() { return new Size(bounds.width(), bounds.height()); }

    public Rect absoluteBounds() {
        return parent == null ? bounds : bounds.translated(parent.absoluteBounds().x(), parent.absoluteBounds().y());
    }

    protected void attach(Control child) {
        if (child.parent != null) throw new IllegalStateException("Control already has a parent");
        child.parent = this;
    }

    public int measureWidth(Font font, int available) { return Math.max(minimumWidth, preferredWidth); }
    public int measureHeight(Font font, int available) { return Math.max(minimumHeight, preferredHeight); }
    /** The desired size before layout, which may differ from the current size. */
    public Size measureSize(Font font, Size available) {
        return new Size(Math.max(minimumWidth, measureWidth(font, available.width())),
                Math.max(minimumHeight, measureHeight(font, available.height())));
    }

    /** Coordinates are relative to the parent control. */
    public void layout(Rect localBounds, Font font) { bounds = localBounds; }

    public void draw(ModularGuiContext context, int screenX, int screenY) {
        render(context, screenX, screenY);
    }

    protected void render(ModularGuiContext context, int screenX, int screenY) {
    }

    public Rect screenBounds(int screenX, int screenY) {
        return absoluteBounds().translated(screenX, screenY);
    }

    public boolean hit(int rootX, int rootY) {
        return visible && absoluteBounds().contains(rootX, rootY);
    }
}
