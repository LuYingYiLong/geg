package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

/** Two panes with a draggable divider. Minimum sizes overflow instead of being crushed. */
public final class SplitContainer extends Container {
    public enum Orientation { HORIZONTAL, VERTICAL }
    private final Control first, second;
    private Orientation orientation = Orientation.HORIZONTAL;
    private double ratio = 0.35;
    private final int gap = 4;
    private Rect divider = new Rect(0, 0, 0, 0);
    private boolean dragging;
    public SplitContainer(Control first, Control second) { this.first = first; this.second = second; super.addChild(first); super.addChild(second); }
    public SplitContainer orientation(Orientation value) { orientation = java.util.Objects.requireNonNull(value); requestLayout(); return this; }
    public SplitContainer ratio(double ratio) { if (!Double.isFinite(ratio)) throw new IllegalArgumentException("Invalid split ratio"); this.ratio = Math.max(0, Math.min(1, ratio)); requestLayout(); return this; }
    public double ratio() { return ratio; }
    @Override public Size measureSize(Font font, Size available) {
        return orientation == Orientation.HORIZONTAL ? new Size(first.minimumWidth() + gap + second.minimumWidth(), Math.max(first.minimumHeight(), second.minimumHeight()))
                : new Size(Math.max(first.minimumWidth(), second.minimumWidth()), first.minimumHeight() + gap + second.minimumHeight());
    }
    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font); boolean horizontal = orientation == Orientation.HORIZONTAL;
        int extent = horizontal ? bounds.width() : bounds.height(); int minA = horizontal ? first.minimumWidth() : first.minimumHeight(), minB = horizontal ? second.minimumWidth() : second.minimumHeight();
        int available = Math.max(minA + minB, extent - gap); int a = Math.max(minA, Math.min(available - minB, (int) Math.round(available * ratio)));
        if (horizontal) {
            fitChild(first, new Rect(0, 0, a, bounds.height()), font); divider = new Rect(a, 0, gap, bounds.height());
            fitChild(second, new Rect(a + gap, 0, available - a, bounds.height()), font);
        } else {
            fitChild(first, new Rect(0, 0, bounds.width(), a), font); divider = new Rect(0, a, bounds.width(), gap);
            fitChild(second, new Rect(0, a + gap, bounds.width(), available - a), font);
        }
    }
    @Override protected boolean onPointerPressed(int x, int y) {
        if (!divider.translated(absoluteBounds().x(), absoluteBounds().y()).contains(x, y)) return false;
        dragging = true; capturePointer(); return true;
    }
    @Override public boolean dragPointer(int x, int y) {
        if (!dragging) return false; Rect area = absoluteBounds();
        ratio((double) (orientation == Orientation.HORIZONTAL ? x - area.x() : y - area.y()) / Math.max(1, (orientation == Orientation.HORIZONTAL ? area.width() : area.height()) - gap)); return true;
    }
    @Override public boolean releasePointer(boolean over) { boolean result = dragging; dragging = false; return result; }
    @Override public void cancelInteraction() { dragging = false; }
    @Override protected void render(ModularGuiContext context, int x, int y) {
        Rect area = divider.translated(screenBounds(x, y).x(), screenBounds(x, y).y());
        context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), dragging ? 0xFFFFFFFF : 0xFF58585A);
    }
}
