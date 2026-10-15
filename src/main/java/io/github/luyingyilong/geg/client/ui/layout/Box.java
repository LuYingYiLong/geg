package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

public final class Box extends Container {
    public enum Orientation { VERTICAL, HORIZONTAL }

    private final List<Control> children = new ArrayList<>();
    private Orientation orientation = Orientation.VERTICAL;
    private int gap;

    public Box orientation(Orientation orientation) { this.orientation = orientation; requestLayout(); return this; }
    public Box gap(int gap) { this.gap = Math.max(0, gap); requestLayout(); return this; }
    public Box add(Control child) { addChild(child); return this; }
    @Override public Box addChild(Control child) { super.addChild(child); children.add(child); return this; }
    @Override public boolean removeChild(Control child) {
        if (!super.removeChild(child)) return false;
        children.remove(child);
        return true;
    }
    public List<Control> children() { return List.copyOf(children); }

    @Override
    public int measureWidth(Font font, int available) {
        int total = Math.max(0, (int) children.stream().filter(Control::visible).count() - 1) * gap;
        int maximum = 0;
        for (Control child : children) if (child.visible()) {
            int value = child.measureSize(font, new Size(available, available)).width();
            total += value;
            maximum = Math.max(maximum, value);
        }
        return orientation == Orientation.HORIZONTAL ? total : maximum;
    }

    @Override
    public int measureHeight(Font font, int available) {
        int total = Math.max(0, (int) children.stream().filter(Control::visible).count() - 1) * gap;
        int maximum = 0;
        for (Control child : children) if (child.visible()) {
            int value = child.measureSize(font, new Size(available, available)).height();
            total += value;
            maximum = Math.max(maximum, value);
        }
        return orientation == Orientation.VERTICAL ? total : maximum;
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        List<Control> active = children.stream().filter(Control::visible).toList();
        int axis = orientation == Orientation.VERTICAL ? localBounds.height() : localBounds.width();
        int cross = orientation == Orientation.VERTICAL ? localBounds.width() : localBounds.height();
        int space = Math.max(0, axis - Math.max(0, active.size() - 1) * gap);
        int[] sizes = new int[active.size()];
        int desired = 0;
        for (int index = 0; index < active.size(); index++) {
            Control child = active.get(index);
            Size measured = child.measureSize(font, orientation == Orientation.VERTICAL
                    ? new Size(cross, axis) : new Size(axis, cross));
            sizes[index] = orientation == Orientation.VERTICAL ? measured.height() : measured.width();
            desired += sizes[index];
        }
        if (desired > space) {
            int shortage = shrink(active, sizes, desired - space, false);
            if (shortage > 0) shortage = shrink(active, sizes, shortage, true);
            // The children retain their minimum size and overflow the box.
        } else {
            int remaining = space - desired;
            double ratioRemaining = active.stream().filter(child -> (orientation == Orientation.VERTICAL
                    ? child.verticalPolicy() : child.horizontalPolicy()) == SizePolicy.EXPAND)
                    .mapToDouble(Control::stretchRatio).sum();
            for (int index = 0; index < active.size() && ratioRemaining > 0.0D; index++) {
                Control child = active.get(index);
                if ((orientation == Orientation.VERTICAL ? child.verticalPolicy()
                        : child.horizontalPolicy()) == SizePolicy.EXPAND) {
                    int extra = (int) Math.round(remaining * child.stretchRatio() / ratioRemaining);
                    sizes[index] += extra;
                    remaining -= extra;
                    ratioRemaining -= child.stretchRatio();
                }
            }
        }
        int cursor = 0;
        for (int index = 0; index < active.size(); index++) {
            Control child = active.get(index);
            Size measured = child.measureSize(font, orientation == Orientation.VERTICAL
                    ? new Size(cross, sizes[index]) : new Size(sizes[index], cross));
            int crossSize = orientation == Orientation.VERTICAL ? measured.width() : measured.height();
            SizePolicy crossPolicy = orientation == Orientation.VERTICAL
                    ? child.horizontalPolicy() : child.verticalPolicy();
            int crossMinimum = orientation == Orientation.VERTICAL
                    ? child.minimumWidth() : child.minimumHeight();
            if (crossPolicy != SizePolicy.CONTENT) crossSize = Math.max(crossMinimum, cross);
            else crossSize = Math.max(crossMinimum, crossSize);
            int crossOffset = crossPolicy == SizePolicy.CONTENT ? Math.max(0, (cross - crossSize) / 2) : 0;
            fitChild(child, orientation == Orientation.VERTICAL
                    ? new Rect(crossOffset, cursor, crossSize, sizes[index])
                    : new Rect(cursor, crossOffset, sizes[index], crossSize), font);
            cursor += sizes[index] + gap;
        }
    }

    private int shrink(List<Control> active, int[] sizes, int shortage, boolean expandChildren) {
        for (int index = 0; index < active.size() && shortage > 0; index++) {
            Control child = active.get(index);
            SizePolicy policy = orientation == Orientation.VERTICAL
                    ? child.verticalPolicy() : child.horizontalPolicy();
            if ((policy == SizePolicy.EXPAND) != expandChildren) continue;
            int minimum = orientation == Orientation.VERTICAL
                    ? child.minimumHeight() : child.minimumWidth();
            int reduction = Math.min(shortage, Math.max(0, sizes[index] - minimum));
            sizes[index] -= reduction;
            shortage -= reduction;
        }
        return shortage;
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        for (Control child : children) if (child.visible()) child.draw(context, screenX, screenY);
    }
}
