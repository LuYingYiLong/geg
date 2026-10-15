package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

public final class Box extends Control {
    public enum Orientation { VERTICAL, HORIZONTAL }

    private final List<Control> children = new ArrayList<>();
    private Orientation orientation = Orientation.VERTICAL;
    private int gap;

    public Box orientation(Orientation orientation) { this.orientation = orientation; return this; }
    public Box gap(int gap) { this.gap = Math.max(0, gap); return this; }
    public Box add(Control child) { attach(child); children.add(child); return this; }
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
        long expandable = active.stream().filter(child -> (orientation == Orientation.VERTICAL
                ? child.verticalPolicy() : child.horizontalPolicy()) == SizePolicy.EXPAND).count();
        if (desired > space) {
            int shortage = shrink(active, sizes, desired - space, false);
            if (shortage > 0) shortage = shrink(active, sizes, shortage, true);
            if (shortage > 0) shrinkBelowMinimum(sizes, space);
        } else {
            int remaining = space - desired;
            for (int index = 0; index < active.size() && expandable > 0; index++) {
                Control child = active.get(index);
                if ((orientation == Orientation.VERTICAL ? child.verticalPolicy()
                        : child.horizontalPolicy()) == SizePolicy.EXPAND) {
                    int extra = remaining / (int) expandable;
                    sizes[index] += extra;
                    remaining -= extra;
                    expandable--;
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
            if (crossPolicy != SizePolicy.CONTENT) crossSize = cross;
            crossSize = Math.min(cross, crossSize);
            int crossOffset = crossPolicy == SizePolicy.CONTENT ? Math.max(0, (cross - crossSize) / 2) : 0;
            child.layout(orientation == Orientation.VERTICAL
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

    /** An undersized viewport cannot honor every minimum; keep every child in the viewport. */
    private static void shrinkBelowMinimum(int[] sizes, int space) {
        int total = 0;
        for (int size : sizes) total += size;
        if (total == 0) return;
        int remaining = space;
        for (int index = 0; index < sizes.length; index++) {
            int allocated = index == sizes.length - 1 ? remaining
                    : (int) ((long) sizes[index] * space / total);
            sizes[index] = allocated;
            remaining -= allocated;
        }
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        for (Control child : children) if (child.visible()) child.draw(context, screenX, screenY);
    }
}
