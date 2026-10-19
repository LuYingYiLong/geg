package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.debug.DebugProperty;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import io.github.luyingyilong.ore.theme.OreDrawables;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** Clips and scrolls one child; layout containers do not own scroll offsets. */
public final class Scroll extends Container {
    public enum BarMode {
        /** Keep the scrollbar visible, including when its content fits. */
        ALWAYS,
        /** Show it only while its axis overflows. */
        AUTO,
        /** Hide it while keeping wheel, drag, and programmatic scrolling available. */
        NEVER,
        /** Hide it and lock that axis at zero. */
        DISABLED
    }

    private static final int BAR_SIZE = 6;
    private static final int TRACK_INSET = 2;
    private static final int TRACK_COLOR = 0xFF58585A;
    private static final int MIN_GRABBER_LENGTH = 12;

    private final VScrollBar verticalBar = new VScrollBar(this);
    private final HScrollBar horizontalBar = new HScrollBar(this);
    private Control content;
    private boolean embedded;
    private boolean showVertical;
    private boolean showHorizontal;
    private Rect viewport = new Rect(0, 0, 0, 0);
    private Size extent = new Size(0, 0);
    private int scrollX;
    private int scrollY;
    private int wheelStep = 28;
    private ScrollBar dragging;
    private int grabOffset;

    public Scroll() {
        super.addChild(verticalBar);
        super.addChild(horizontalBar);
        verticalBar.setVisible(false);
        horizontalBar.setVisible(false);
    }

    public Scroll setContent(Control content) {
        if (this.content != null) removeChild(this.content);
        addChild(content);
        return this;
    }

    @Override public Scroll addChild(Control child) {
        if (content != null) throw new IllegalStateException("Scroll accepts one content child");
        if (child == verticalBar || child == horizontalBar)
            throw new IllegalArgumentException("Scrollbars are owned by Scroll");
        super.addChild(child);
        content = child;
        return this;
    }

    @Override public boolean removeChild(Control child) {
        if (child == verticalBar || child == horizontalBar) return false;
        if (!super.removeChild(child)) return false;
        if (content == child) content = null;
        return true;
    }

    /** False by default: visible bars reserve 6px and draw a track; true overlays only the grabbers. */
    public Scroll setEmbedded(boolean embedded) {
        if (DebugBridge.previewWrite(this, "embedded", embedded)) return this;
        if (this.embedded != embedded) {
            this.embedded = embedded;
            requestLayout();
        }
        return this;
    }

    public Scroll setWheelStep(int pixels) {
        if (pixels < 1) throw new IllegalArgumentException("Wheel step must be positive");
        wheelStep = pixels;
        return this;
    }

    public Scroll setVertical(BarMode mode) { verticalBar.setMode(mode); return this; }
    public Scroll setHorizontal(BarMode mode) { horizontalBar.setMode(mode); return this; }
    public VScrollBar getVerticalBar() { return verticalBar; }
    public HScrollBar getHorizontalBar() { return horizontalBar; }
    @Inspectable
    public boolean getEmbedded() { return debugValue("embedded", embedded); }
    /** Uses the same rectangles as pointer dispatch, including embedded bars. */
    public boolean scrollbarAt(int rootX, int rootY) {
        return showVertical && verticalBar.hit(rootX, rootY) || showHorizontal && horizontalBar.hit(rootX, rootY);
    }
    public boolean getVerticalVisible() { return showVertical; }
    public boolean getHorizontalVisible() { return showHorizontal; }
    public Rect getViewport() { return viewport; }
    public Size getContentExtent() { return extent; }
    public int getScrollX() { return Math.min(getMaxScrollX(), debugValue("scrollX", scrollX)); }
    public int getScrollY() { return Math.min(getMaxScrollY(), debugValue("scrollY", scrollY)); }
    public int getMaxScrollX() { return Math.max(0, extent.width() - viewport.width()); }
    public int getMaxScrollY() { return Math.max(0, extent.height() - viewport.height()); }
    public boolean isContentChild(Control child) { return child == content; }

    public void scrollToTop() {
        if (content instanceof ScrollContent virtual) virtual.clearScrollAnchor();
        setScroll(scrollX, 0);
    }

    public void setScroll(int x, int y) {
        scrollX = horizontalBar.getMode() == BarMode.DISABLED ? 0 : clamp(x, getMaxScrollX());
        scrollY = verticalBar.getMode() == BarMode.DISABLED ? 0 : clamp(y, getMaxScrollY());
        updateVisibleRegion();
    }

    public boolean scrollBy(int x, int y) {
        int beforeX = scrollX;
        int beforeY = scrollY;
        setScroll(scrollX + x, scrollY + y);
        return scrollX != beforeX || scrollY != beforeY;
    }

    /** Bring a rectangle in content coordinates into the visible viewport. */
    public void ensureVisible(Rect area) {
        int x = scrollX;
        int y = scrollY;
        if (area.x() < x) x = area.x();
        else if (area.right() > x + viewport.width()) x = area.right() - viewport.width();
        if (area.y() < y) y = area.y();
        else if (area.bottom() > y + viewport.height()) y = area.bottom() - viewport.height();
        setScroll(x, y);
    }

    @Override public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        if (content == null) return getMinimumSize();
        Size measured = content.desiredSize(font, available);
        int reserveX = !getEmbedded() && verticalBar.getMode() == BarMode.ALWAYS ? BAR_SIZE : 0;
        int reserveY = !getEmbedded() && horizontalBar.getMode() == BarMode.ALWAYS ? BAR_SIZE : 0;
        return new Size(Math.max(getMinimumWidth(), measured.width() + reserveX),
                Math.max(getMinimumHeight(), measured.height() + reserveY));
    }

    @Override public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).width();
    }

    @Override public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).height();
    }

    @Override public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(localBounds, font);
            boolean vertical = verticalBar.getMode() == BarMode.ALWAYS;
            boolean horizontal = horizontalBar.getMode() == BarMode.ALWAYS;
            for (int pass = 0; pass < 4; pass++) {
                measureContent(localBounds, font, vertical, horizontal);
                boolean nextVertical = visibleFor(verticalBar.getMode(), extent.height() > viewport.height());
                boolean nextHorizontal = visibleFor(horizontalBar.getMode(), extent.width() > viewport.width());
                if (vertical == nextVertical && horizontal == nextHorizontal) break;
                vertical = nextVertical;
                horizontal = nextHorizontal;
            }
            // The last visibility change may have altered the available viewport.
            measureContent(localBounds, font, vertical, horizontal);
            showVertical = vertical;
            showHorizontal = horizontal;
            verticalBar.setVisible(showVertical);
            horizontalBar.setVisible(showHorizontal);
            int corner = showVertical && showHorizontal ? BAR_SIZE : 0;
            layoutChild(verticalBar, new Rect(localBounds.width() - BAR_SIZE, 0, BAR_SIZE,
                    Math.max(0, getEmbedded() ? localBounds.height() - corner : viewport.height())), font);
            layoutChild(horizontalBar, new Rect(0, localBounds.height() - BAR_SIZE,
                    Math.max(0, getEmbedded() ? localBounds.width() - corner : viewport.width()), BAR_SIZE), font);
            if (content instanceof ScrollContent virtual) {
                int restored = virtual.consumeRestoredScrollY();
                if (restored >= 0) scrollY = restored + content.getBounds().y();
            }
            setScroll(scrollX, scrollY);
        }
    }

    private void measureContent(Rect bounds, Font font, boolean vertical, boolean horizontal) {
        int width = Math.max(0, bounds.width() - (!getEmbedded() && vertical ? BAR_SIZE : 0));
        int height = Math.max(0, bounds.height() - (!getEmbedded() && horizontal ? BAR_SIZE : 0));
        viewport = new Rect(0, 0, width, height);
        if (content == null) {
            extent = new Size(width, height);
            return;
        }
        if (content instanceof ScrollContent virtual) {
            fitChild(content, new Rect(0, 0, width, height), font);
            Size size = virtual.getContentExtent();
            extent = new Size(Math.max(width, content.getBounds().x() + size.width()),
                    Math.max(height, content.getBounds().y() + size.height()));
        } else {
            Size measured = content.desiredSize(font, new Size(width, height));
            int contentWidth = Math.max(width, measured.width());
            int contentHeight = Math.max(height, measured.height());
            fitChild(content, new Rect(0, 0, contentWidth, contentHeight), font);
            extent = new Size(Math.max(width, content.getBounds().right()), Math.max(height, content.getBounds().bottom()));
        }
    }

    private void updateVisibleRegion() {
        if (content instanceof ScrollContent virtual)
            virtual.setVisibleRegion(new Rect(getScrollX() - content.getBounds().x(), getScrollY() - content.getBounds().y(),
                    viewport.width(), viewport.height()));
    }

    private static boolean visibleFor(BarMode mode, boolean overflow) {
        return mode == BarMode.ALWAYS || mode == BarMode.AUTO && overflow;
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }

    @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            if (!getVisible()) return;
            if (content != null && viewport.width() > 0 && viewport.height() > 0) {
                Rect clip = viewport.translated(screenX + getAbsoluteBounds().x(),
                        screenY + getAbsoluteBounds().y());
                GuiGraphics graphics = context.getGraphics();
                graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
            DebugBridge.backend().clip(clip);
                try {
                    content.draw(context, screenX - getScrollX(), screenY - getScrollY());
                } finally {
                    graphics.disableScissor(); DebugBridge.backend().unclip();
                }
            }
            GuiGraphics graphics = context.getGraphics();
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, io.github.luyingyilong.ore.util.OreLayers.SCROLLBAR);
            try {
                if (showVertical) verticalBar.draw(context, screenX, screenY);
                if (showHorizontal) horizontalBar.draw(context, screenX, screenY);
                graphics.flush();
            } finally { graphics.pose().popPose(); }
        }
    }



    @Override protected boolean wheel(int rootX, int rootY, double delta, boolean horizontal) {
        if (!hit(rootX, rootY)) return false;
        int amount = -(int) Math.signum(delta) * wheelStep;
        if (amount == 0) return false;
        if (horizontal || getMaxScrollY() == 0) {
            if (scrollBy(amount, 0)) return true;
            if (horizontal) return false;
        }
        return scrollBy(0, amount);
    }

    @Override public Control hitTop(int rootX, int rootY) {
        if (!hit(rootX, rootY)) return null;
        if (showVertical && verticalBar.hit(rootX, rootY)) return verticalBar;
        if (showHorizontal && horizontalBar.hit(rootX, rootY)) return horizontalBar;
        if (!viewport.translated(getAbsoluteBounds().x(), getAbsoluteBounds().y()).contains(rootX, rootY))
            return this;
        Control hit = content == null ? null : content.hitTop(rootX + getScrollX(), rootY + getScrollY());
        return hit == null ? this : hit;
    }

    public boolean dragTo(int rootX, int rootY) {
        if (dragging == null) return false;
        Rect track = dragging.getAbsoluteBounds();
        int coordinate = dragging.vertical ? rootY : rootX;
        int start = dragging.vertical ? track.y() : track.x();
        int travel = Math.max(0, dragging.trackLength() - dragging.grabberLength());
        int position = clamp(coordinate - grabOffset - start, travel);
        int maximum = dragging.vertical ? getMaxScrollY() : getMaxScrollX();
        int next = travel == 0 ? 0 : (int) Math.round((double) position * maximum / travel);
        if (dragging.vertical) setScroll(scrollX, next);
        else setScroll(next, scrollY);
        return true;
    }

    public boolean endDrag() {
        boolean wasDragging = dragging != null;
        dragging = null;
        return wasDragging;
    }

    @Override public void cancelInteraction() { endDrag(); }

    private void beginDrag(ScrollBar bar, int rootX, int rootY) {
        Rect thumb = bar.grabberBounds();
        int coordinate = bar.vertical ? rootY : rootX;
        int thumbStart = bar.vertical ? thumb.y() : thumb.x();
        grabOffset = thumb.contains(rootX, rootY) ? coordinate - thumbStart
                : (bar.vertical ? thumb.height() : thumb.width()) / 2;
        dragging = bar;
        if (!thumb.contains(rootX, rootY)) dragTo(rootX, rootY);
    }

    public abstract static class ScrollBar extends Control {
        private final Scroll owner;
        private final boolean vertical;
        private BarMode mode = BarMode.AUTO;

        private ScrollBar(Scroll owner, boolean vertical) {
            this.owner = owner;
            this.vertical = vertical;
            setMinimum(BAR_SIZE, BAR_SIZE);
        }

        public BarMode getMode() { return owner.debugValue(vertical ? "verticalMode" : "horizontalMode", mode); }

        public ScrollBar setMode(BarMode mode) {
            BarMode next = Objects.requireNonNull(mode);
            if (this.mode != next) {
                if (owner.dragging == this) owner.endDrag();
                this.mode = next;
                if (next == BarMode.DISABLED) owner.setScroll(owner.scrollX, owner.scrollY);
                owner.requestLayout();
            }
            return this;
        }

        private int trackLength() {
            return Math.max(0, vertical ? getBounds().height() : getBounds().width());
        }

        private int grabberLength() {
            int track = trackLength();
            int viewport = vertical ? owner.viewport.height() : owner.viewport.width();
            int extent = vertical ? owner.extent.height() : owner.extent.width();
            if (extent <= 0 || viewport >= extent) return track;
            return Math.min(track, Math.max(MIN_GRABBER_LENGTH,
                    (int) Math.round((double) track * viewport / extent)));
        }

        Rect grabberBounds() {
            Rect bar = getAbsoluteBounds();
            int length = grabberLength();
            int travel = Math.max(0, trackLength() - length);
            int maximum = vertical ? owner.getMaxScrollY() : owner.getMaxScrollX();
            int scroll = vertical ? owner.getScrollY() : owner.getScrollX();
            int position = maximum == 0 ? 0 : (int) Math.round((double) travel * scroll / maximum);
            return vertical ? new Rect(bar.x(), bar.y() + position, BAR_SIZE, length)
                    : new Rect(bar.x() + position, bar.y(), length, BAR_SIZE);
        }

        /** The 2px end inset is visual only; the grabber still travels over the whole bar. */
        Rect trackBounds() {
            Rect bar = getAbsoluteBounds();
            if (owner.getEmbedded() || trackLength() <= 2 * TRACK_INSET)
                return new Rect(bar.x(), bar.y(), 0, 0);
            return vertical
                    ? new Rect(bar.x() + (BAR_SIZE - 2) / 2, bar.y() + TRACK_INSET, 2, bar.height() - 2 * TRACK_INSET)
                    : new Rect(bar.x() + TRACK_INSET, bar.y() + (BAR_SIZE - 2) / 2, bar.width() - 2 * TRACK_INSET, 2);
        }

        @Override protected boolean pointerDown(int rootX, int rootY) {
            if (!hit(rootX, rootY)) return false;

            owner.beginDrag(this, rootX, rootY);
            return true;
        }

        @Override protected boolean pointerMove(int rootX, int rootY) {
            return getVisible() && mode != BarMode.DISABLED && owner.dragTo(rootX, rootY);
        }

        @Override protected boolean pointerUp(int rootX, int rootY, boolean over) {
            return owner.endDrag();
        }

        @Override public void cancelInteraction() {
            if (owner.dragging == this) owner.endDrag();
        }

        @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
            int length = trackLength();
            if (length <= 0) return;
            GuiGraphics graphics = context.getGraphics();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            Rect track = trackBounds().translated(screenX, screenY);
            if (track.width() > 0 && track.height() > 0)
                graphics.fill(track.x(), track.y(), track.right(), track.bottom(), TRACK_COLOR);
            Rect thumb = grabberBounds().translated(screenX, screenY);
            if (thumb.width() <= 0 || thumb.height() <= 0) return;
            WidgetThemeEntry<WidgetTheme> entry = context.getTheme().getWidgetTheme(
                    vertical ? OreTheme.V_GRABBER : OreTheme.H_GRABBER);
            if (entry == null) return;
            WidgetTheme style = entry.theme();
            IDrawable background = style.getBackground();
            OreDrawables.draw(context, background, thumb, style);
        }
    }

    public static final class VScrollBar extends ScrollBar {
        private VScrollBar(Scroll owner) { super(owner, true); }

        @Override public VScrollBar setMode(BarMode mode) { super.setMode(mode); return this; }
    }

    public static final class HScrollBar extends ScrollBar {
        private HScrollBar(Scroll owner) { super(owner, false); }

        @Override public HScrollBar setMode(BarMode mode) { super.setMode(mode); return this; }
    }
    @Override public void debugProperties(java.util.List<DebugProperty> out) {
        super.debugProperties(out);
        out.add(DebugProperty.choice(this,"verticalMode","Layout",()->verticalBar.getMode()));
        out.add(DebugProperty.choice(this,"horizontalMode","Layout",()->horizontalBar.getMode()));
        out.add(DebugProperty.number(this,"scrollX","Layout",this::getScrollX,0,Integer.MAX_VALUE));
        out.add(DebugProperty.number(this,"scrollY","Layout",this::getScrollY,0,Integer.MAX_VALUE));
    }
}
