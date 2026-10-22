package io.github.luyingyilong.ore.control.layout;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;

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
    private boolean wrap;
    private Integer lineGap;

    @Inspectable
    @io.github.luyingyilong.ore.gui.UiProperty
    public boolean getWrap() { return DebugBridge.value(this, "wrap", wrap); }

    public Box setWrap(boolean value) {
        if (DebugBridge.previewWrite(this, "wrap", value)) return this;
        wrap = value;
        requestLayout();
        return this;
    }

    @Inspectable(min = 0, max = 10000)
    @io.github.luyingyilong.ore.gui.UiProperty
    public int getLineGap() { return DebugBridge.value(this, "lineGap", lineGap == null ? getGap() : lineGap); }

    public Box setLineGap(int value) {
        value = Math.max(0, value);
        if (DebugBridge.previewWrite(this, "lineGap", value)) return this;
        lineGap = value;
        requestLayout();
        return this;
    }

    /** Null means that line spacing still follows gap. */
    public Integer getLineGapOverride() { return lineGap; }

    public Box clearLineGap() { lineGap = null; requestLayout(); return this; }

    @Inspectable
    @io.github.luyingyilong.ore.gui.UiProperty
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
    @io.github.luyingyilong.ore.gui.UiProperty
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
        if (getWrap()) {
            Flow flow = flow(font, available);
            return new Size(Math.max(super.measureWidth(font, available.width()), flow.size.width()),
                    Math.max(super.measureHeight(font, available.height()), flow.size.height()));
        }
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
            if (getWrap()) {
                Flow flow = flow(font, new Size(localBounds.width(), localBounds.height()));
                for (Placement placement : flow.placements)
                    if (placement.child.getParent() == this && placement.child.isAlive() && placement.child.getVisible())
                        fitChild(placement.child, placement.slot, font);
                return;
            }
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

    /** Shared by measurement and arrangement: grouping never consumes EXPAND space. */
    private Flow flow(Font font, Size available) {
        boolean horizontal = getOrientation() == Orientation.HORIZONTAL;
        int limit = horizontal ? available.width() : available.height();
        int crossLimit = horizontal ? available.height() : available.width();
        List<List<Control>> lines = new ArrayList<>();
        List<Control> line = new ArrayList<>();
        long used = 0;
        for (Control child : activeChildren()) {
            Size natural = child.desiredSize(font, available);
            int basis = horizontal ? natural.width() : natural.height();
            basis = Math.max(mainMinimum(child), Math.min(basis, limit));
            if (!line.isEmpty() && limit != Integer.MAX_VALUE && used + getGap() + basis > limit) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
            }
            if (!line.isEmpty()) used += getGap();
            line.add(child);
            used += basis;
        }
        if (!line.isEmpty()) lines.add(line);
        List<Placement> placements = new ArrayList<>();
        int crossCursor = 0, mainExtent = 0;
        for (List<Control> row : lines) {
            int rowLimit = limit;
            if (limit == Integer.MAX_VALUE) {
                long naturalExtent = (long) Math.max(0, row.size() - 1) * getGap();
                for (Control child : row) {
                    Size natural = child.desiredSize(font, available);
                    naturalExtent += horizontal ? natural.width() : natural.height();
                }
                rowLimit = saturated(naturalExtent);
            }
            int[] sizes = allocateSizes(row, font, rowLimit, crossLimit);
            int rowCross = 0;
            for (int index = 0; index < row.size(); index++) {
                Control child = row.get(index);
                Size measured = child.desiredSize(font, horizontal ? new Size(sizes[index], crossLimit)
                        : new Size(crossLimit, sizes[index]));
                int flags = horizontal ? child.getSizeFlagsHorizontal() : child.getSizeFlagsVertical();
                if (!SizeFlags.fills(flags)) {
                    int actual = Math.max(mainMinimum(child), Math.min(sizes[index], horizontal ? measured.width() : measured.height()));
                    measured = child.desiredSize(font, horizontal ? new Size(actual, crossLimit) : new Size(crossLimit, actual));
                }
                rowCross = Math.max(rowCross, horizontal ? measured.height() : measured.width());
            }
            int cursor = 0;
            for (int index = 0; index < row.size(); index++) {
                placements.add(new Placement(row.get(index), horizontal
                        ? new Rect(cursor, crossCursor, sizes[index], rowCross)
                        : new Rect(crossCursor, cursor, rowCross, sizes[index])));
                cursor = saturated((long) cursor + sizes[index] + (index + 1 < row.size() ? getGap() : 0));
            }
            mainExtent = Math.max(mainExtent, cursor);
            crossCursor = saturated((long) crossCursor + rowCross + (row != lines.get(lines.size() - 1) ? getLineGap() : 0));
        }
        return new Flow(horizontal ? new Size(mainExtent, crossCursor) : new Size(crossCursor, mainExtent), placements);
    }

    private static int saturated(long value) { return (int) Math.min(Integer.MAX_VALUE, Math.max(0, value)); }
    private record Placement(Control child, Rect slot) { }
    private record Flow(Size size, List<Placement> placements) { }

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
    protected void drawContent(DrawContext context, int screenX, int screenY) {
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
