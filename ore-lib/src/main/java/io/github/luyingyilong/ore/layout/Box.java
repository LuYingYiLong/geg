package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.Inspectable;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

public final class Box extends Container {
    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) { io.github.luyingyilong.ore.theme.OreTheme.registerType(db,"Box"); }
    private Orientation orientation = Orientation.VERTICAL;
    private int gap;

    @Inspectable
    public Orientation getOrientation() {
        return DebugBridge.value(this, "orientation", orientation);
    }

    public Box setOrientation(Orientation orientation) {
        java.util.Objects.requireNonNull(orientation);
        if (DebugBridge.previewWrite(this, "orientation", orientation)) return this;
        this.orientation = orientation;
        requestLayout();
        return this;
    }

    @Inspectable(min = 0, max = 10000)
    public int getGap() {
        return DebugBridge.value(this, "gap", gap);
    }

    public Box setGap(int gap) {
        gap = Math.max(0, gap);
        if (DebugBridge.previewWrite(this, "gap", gap)) return this;
        this.gap = gap;
        requestLayout();
        return this;
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        Orientation orientation = DebugBridge.value(this, "orientation", this.orientation);
        int gap = DebugBridge.value(this, "gap", this.gap);
        int totalWidth = 0;
        int totalHeight = 0;
        int count = 0;
        List<Control> active = activeChildren();
        for (Control child : active) {
            Size measured = child.desiredSize(font, available);
            totalWidth = orientation == Orientation.HORIZONTAL ? totalWidth + measured.width()
                    : Math.max(totalWidth, measured.width());
            totalHeight = orientation == Orientation.VERTICAL ? totalHeight + measured.height()
                    : Math.max(totalHeight, measured.height());
            count++;
        }
        int gaps = Math.max(0, count - 1) * gap;
        if (orientation == Orientation.HORIZONTAL) {
            totalWidth += gaps;
            // Wrapping depends on the width the child will actually receive, not the
            // entire row width. Use the same allocation as layout before measuring height.
            int[] widths = allocateSizes(active, font, available.width(), available.height());
            totalHeight = 0;
            for (int index = 0; index < active.size(); index++) {
                Control child = active.get(index);
                int width = widths[index];
                if (!SizeFlags.fills(child.getSizeFlagsHorizontal()))
                    width = Math.min(width, child.desiredSize(font, new Size(width, available.height())).width());
                width = Math.max(child.getMinimumWidth(), width);
                totalHeight = Math.max(totalHeight, child.desiredSize(font, new Size(width, available.height())).height());
            }
        } else totalHeight += gaps;
        return new Size(Math.max(super.measureWidth(font, available.width()), totalWidth),
                Math.max(super.measureHeight(font, available.height()), totalHeight));
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).width();
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).height();
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            Orientation orientation = DebugBridge.value(this, "orientation", this.orientation);
            int gap = DebugBridge.value(this, "gap", this.gap);
            super.layout(localBounds, font);
            List<Control> active = activeChildren();
            int axis = orientation == Orientation.VERTICAL ? localBounds.height() : localBounds.width();
            int cross = orientation == Orientation.VERTICAL ? localBounds.width() : localBounds.height();
            int[] sizes = allocateSizes(active, font, axis, cross);
            int cursor = 0;
            for (int index = 0; index < active.size(); index++) {
                Control child = active.get(index);
                fitChild(child, orientation == Orientation.VERTICAL
                        ? new Rect(0, cursor, cross, sizes[index])
                        : new Rect(cursor, 0, sizes[index], cross), font);
                cursor += sizes[index] + gap;
            }
        }
    }

    private List<Control> activeChildren() {
        return getChildren().stream().filter(child -> child.getParent() == this && child.isAlive() && child.getVisible()).toList();
    }

    private int[] allocateSizes(List<Control> active, Font font, int axis, int cross) {
        Orientation orientation = getOrientation();
        int space = Math.max(0, axis - Math.max(0, active.size() - 1) * getGap());
        int[] sizes = new int[active.size()];
        int fixed = 0;
        for (int index = 0; index < active.size(); index++) {
            Control child = active.get(index);
            Size measured = child.desiredSize(font, orientation == Orientation.VERTICAL
                    ? new Size(cross, axis) : new Size(axis, cross));
            sizes[index] = orientation == Orientation.VERTICAL ? measured.height() : measured.width();
            if (!expands(child)) fixed += sizes[index];
        }
        int expandedMinimum = active.stream().filter(this::expands).mapToInt(this::mainMinimum).sum();
        int shortage = Math.max(0, fixed + expandedMinimum - space);
        for (int index = 0; index < active.size() && shortage > 0; index++) {
            Control child = active.get(index);
            if (expands(child)) continue;
            int reduction = Math.min(shortage, sizes[index] - mainMinimum(child));
            sizes[index] -= reduction;
            fixed -= reduction;
            shortage -= reduction;
        }
        allocateExpanded(active, sizes, Math.max(0, space - fixed));
        return sizes;
    }

    private boolean expands(Control child) {
        Orientation orientation = DebugBridge.value(this, "orientation", this.orientation);
        return SizeFlags.expands(orientation == Orientation.VERTICAL
                ? child.getSizeFlagsVertical() : child.getSizeFlagsHorizontal());
    }

    private int mainMinimum(Control child) {
        Orientation orientation = DebugBridge.value(this, "orientation", this.orientation);
        return orientation == Orientation.VERTICAL ? child.getMinimumHeight() : child.getMinimumWidth();
    }

    /**
     * Freeze children whose proportional slot would be below their minimum, then redistribute.
     */
    private void allocateExpanded(List<Control> active, int[] sizes, int space) {
        List<Integer> pending = new ArrayList<>();
        for (int index = 0; index < active.size(); index++) if (expands(active.get(index))) pending.add(index);
        int remaining = space;
        while (!pending.isEmpty()) {
            double weight = pending.stream().mapToDouble(index -> active.get(index).getSizeFlagsStretchRatio()).sum();
            List<Integer> frozen = new ArrayList<>();
            for (int index : pending)
                if (remaining * (double) active.get(index).getSizeFlagsStretchRatio() / weight < mainMinimum(active.get(index)))
                    frozen.add(index);
            if (frozen.isEmpty()) {
                int assigned = 0;
                for (int index : pending) {
                    sizes[index] = (int) Math.floor(remaining * (double) active.get(index).getSizeFlagsStretchRatio() / weight);
                    assigned += sizes[index];
                }
                int remainder = remaining - assigned;
                for (int index : pending) if (remainder-- > 0) sizes[index]++;
                return;
            }
            for (int index : frozen) {
                sizes[index] = mainMinimum(active.get(index));
                remaining = Math.max(0, remaining - sizes[index]);
            }
            pending.removeAll(frozen);
        }
    }

    @Override
    public void draw(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            for (Control child : getChildren())
                if (child.getParent() == this && child.isAlive() && child.getVisible())
                    child.draw(context, screenX, screenY);
        }
    }

    public enum Orientation {VERTICAL, HORIZONTAL}

}
