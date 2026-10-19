package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.Inspectable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

public final class Box extends Container {
    public enum Orientation { VERTICAL, HORIZONTAL }

    private Orientation orientation = Orientation.VERTICAL;
    private int gap;

    public Box orientation(Orientation orientation) { java.util.Objects.requireNonNull(orientation); if (DebugBridge.previewWrite(this, "orientation", orientation)) return this; this.orientation = orientation; requestLayout(); return this; }
    public Box gap(int gap) { gap = Math.max(0, gap); if (DebugBridge.previewWrite(this, "gap", gap)) return this; this.gap = gap; requestLayout(); return this; }
    @Inspectable
    public Orientation orientation() { return DebugBridge.value(this, "orientation", orientation); }
    @Inspectable(min = 0, max = 10000)
    public int gap() { return DebugBridge.value(this, "gap", gap); }
    public Box add(Control child) { addChild(child); return this; }
    @Override public Box addChild(Control child) { super.addChild(child); return this; }

    @Override public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        Orientation orientation = DebugBridge.value(this, "orientation", this.orientation);
        int gap = DebugBridge.value(this, "gap", this.gap);
        int totalWidth = 0;
        int totalHeight = 0;
        int count = 0;
        for (Control child : children()) if (child.parent() == this && child.isAlive() && child.visible()) {
            Size measured = child.desiredSize(font, available);
            totalWidth = orientation == Orientation.HORIZONTAL ? totalWidth + measured.width()
                    : Math.max(totalWidth, measured.width());
            totalHeight = orientation == Orientation.VERTICAL ? totalHeight + measured.height()
                    : Math.max(totalHeight, measured.height());
            count++;
        }
        int gaps = Math.max(0, count - 1) * gap;
        if (orientation == Orientation.HORIZONTAL) totalWidth += gaps;
        else totalHeight += gaps;
        return new Size(Math.max(super.measureWidth(font, available.width()), totalWidth),
                Math.max(super.measureHeight(font, available.height()), totalHeight));
    }

    @Override public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).width();
    }

    @Override public int measureHeight(Font font, int available) {
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
            List<Control> active = children().stream().filter(c -> c.isAlive() && c.visible()).toList();
            int axis = orientation == Orientation.VERTICAL ? localBounds.height() : localBounds.width();
            int cross = orientation == Orientation.VERTICAL ? localBounds.width() : localBounds.height();
            int space = Math.max(0, axis - Math.max(0, active.size() - 1) * gap);
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

    private boolean expands(Control child) {
        Orientation orientation = DebugBridge.value(this, "orientation", this.orientation);
        return SizeFlags.expands(orientation == Orientation.VERTICAL
                ? child.sizeFlagsVertical() : child.sizeFlagsHorizontal());
    }

    private int mainMinimum(Control child) {
        Orientation orientation = DebugBridge.value(this,"orientation",this.orientation);
        return orientation == Orientation.VERTICAL ? child.minimumHeight() : child.minimumWidth();
    }

    /** Freeze children whose proportional slot would be below their minimum, then redistribute. */
    private void allocateExpanded(List<Control> active, int[] sizes, int space) {
        List<Integer> pending = new ArrayList<>();
        for (int index = 0; index < active.size(); index++) if (expands(active.get(index))) pending.add(index);
        int remaining = space;
        while (!pending.isEmpty()) {
            double weight = pending.stream().mapToDouble(index -> active.get(index).sizeFlagsStretchRatio()).sum();
            List<Integer> frozen = new ArrayList<>();
            for (int index : pending)
                if (remaining * (double) active.get(index).sizeFlagsStretchRatio() / weight < mainMinimum(active.get(index)))
                    frozen.add(index);
            if (frozen.isEmpty()) {
                int assigned = 0;
                for (int index : pending) {
                    sizes[index] = (int) Math.floor(remaining * (double) active.get(index).sizeFlagsStretchRatio() / weight);
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
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            for (Control child : children()) if (child.parent() == this && child.isAlive() && child.visible()) child.draw(context, screenX, screenY);
        }
    }

}
