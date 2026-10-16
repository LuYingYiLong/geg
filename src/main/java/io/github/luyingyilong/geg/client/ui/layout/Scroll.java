package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import io.github.luyingyilong.geg.client.ui.OreTheme;
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
        verticalBar.visible(false);
        horizontalBar.visible(false);
    }

    public Scroll content(Control content) {
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

    public Scroll embedded(boolean embedded) {
        if (this.embedded != embedded) {
            this.embedded = embedded;
            requestLayout();
        }
        return this;
    }

    public Scroll wheelStep(int pixels) {
        if (pixels < 1) throw new IllegalArgumentException("Wheel step must be positive");
        wheelStep = pixels;
        return this;
    }

    public Scroll vertical(BarMode mode) { verticalBar.mode(mode); return this; }
    public Scroll horizontal(BarMode mode) { horizontalBar.mode(mode); return this; }
    public VScrollBar verticalBar() { return verticalBar; }
    public HScrollBar horizontalBar() { return horizontalBar; }
    public boolean embedded() { return embedded; }
    public boolean verticalVisible() { return showVertical; }
    public boolean horizontalVisible() { return showHorizontal; }
    public Rect viewport() { return viewport; }
    public Size contentExtent() { return extent; }
    public int scrollX() { return scrollX; }
    public int scrollY() { return scrollY; }
    public int maxScrollX() { return Math.max(0, extent.width() - viewport.width()); }
    public int maxScrollY() { return Math.max(0, extent.height() - viewport.height()); }

    public void scrollToTop() {
        if (content instanceof ScrollContent virtual) virtual.clearScrollAnchor();
        setScroll(scrollX, 0);
    }

    public void setScroll(int x, int y) {
        scrollX = horizontalBar.mode() == BarMode.DISABLED ? 0 : clamp(x, maxScrollX());
        scrollY = verticalBar.mode() == BarMode.DISABLED ? 0 : clamp(y, maxScrollY());
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
        if (content == null) return minimumSize();
        Size measured = content.measureSize(font, available);
        int reserveX = !embedded && verticalBar.mode() == BarMode.ALWAYS ? BAR_SIZE : 0;
        int reserveY = !embedded && horizontalBar.mode() == BarMode.ALWAYS ? BAR_SIZE : 0;
        return new Size(Math.max(minimumWidth(), measured.width() + reserveX),
                Math.max(minimumHeight(), measured.height() + reserveY));
    }

    @Override public int measureWidth(Font font, int available) {
        return measureSize(font, new Size(available, available)).width();
    }

    @Override public int measureHeight(Font font, int available) {
        return measureSize(font, new Size(available, available)).height();
    }

    @Override public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        boolean vertical = verticalBar.mode() == BarMode.ALWAYS;
        boolean horizontal = horizontalBar.mode() == BarMode.ALWAYS;
        for (int pass = 0; pass < 4; pass++) {
            measureContent(localBounds, font, vertical, horizontal);
            boolean nextVertical = visibleFor(verticalBar.mode(), extent.height() > viewport.height());
            boolean nextHorizontal = visibleFor(horizontalBar.mode(), extent.width() > viewport.width());
            if (vertical == nextVertical && horizontal == nextHorizontal) break;
            vertical = nextVertical;
            horizontal = nextHorizontal;
        }
        // The last visibility change may have altered the available viewport.
        measureContent(localBounds, font, vertical, horizontal);
        showVertical = vertical;
        showHorizontal = horizontal;
        verticalBar.visible(showVertical);
        horizontalBar.visible(showHorizontal);
        int corner = showVertical && showHorizontal ? BAR_SIZE : 0;
        fitChild(verticalBar, new Rect(localBounds.width() - BAR_SIZE, 0, BAR_SIZE,
                Math.max(0, embedded ? localBounds.height() - corner : viewport.height())), font);
        fitChild(horizontalBar, new Rect(0, localBounds.height() - BAR_SIZE,
                Math.max(0, embedded ? localBounds.width() - corner : viewport.width()), BAR_SIZE), font);
        if (content instanceof ScrollContent virtual) {
            int restored = virtual.consumeRestoredScrollY();
            if (restored >= 0) scrollY = restored;
        }
        setScroll(scrollX, scrollY);
    }

    private void measureContent(Rect bounds, Font font, boolean vertical, boolean horizontal) {
        int width = Math.max(0, bounds.width() - (!embedded && vertical ? BAR_SIZE : 0));
        int height = Math.max(0, bounds.height() - (!embedded && horizontal ? BAR_SIZE : 0));
        viewport = new Rect(0, 0, width, height);
        if (content == null) {
            extent = new Size(width, height);
            return;
        }
        if (content instanceof ScrollContent virtual) {
            fitChild(content, new Rect(0, 0, width, height), font);
            Size size = virtual.contentExtent();
            extent = new Size(Math.max(width, size.width()), Math.max(height, size.height()));
        } else {
            Size measured = content.measureSize(font, new Size(width, height));
            int contentWidth = Math.max(width, measured.width());
            int contentHeight = Math.max(height, measured.height());
            fitChild(content, new Rect(0, 0, contentWidth, contentHeight), font);
            extent = new Size(contentWidth, contentHeight);
        }
    }

    private void updateVisibleRegion() {
        if (content instanceof ScrollContent virtual)
            virtual.visibleRegion(new Rect(scrollX, scrollY, viewport.width(), viewport.height()));
    }

    private static boolean visibleFor(BarMode mode, boolean overflow) {
        return mode == BarMode.ALWAYS || mode == BarMode.AUTO && overflow;
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }

    @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!visible()) return;
        if (content != null && viewport.width() > 0 && viewport.height() > 0) {
            Rect clip = viewport.translated(screenX + absoluteBounds().x(),
                    screenY + absoluteBounds().y());
            GuiGraphics graphics = context.getGraphics();
            graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
            try {
                content.draw(context, screenX - scrollX, screenY - scrollY);
            } finally {
                graphics.disableScissor();
            }
        }
        if (showVertical) verticalBar.draw(context, screenX, screenY);
        if (showHorizontal) horizontalBar.draw(context, screenX, screenY);
    }

    @Override public boolean dispatchPointer(int rootX, int rootY) {
        if (!hit(rootX, rootY)) return false;
        if (showVertical && verticalBar.dispatchPointer(rootX, rootY)) return true;
        if (showHorizontal && horizontalBar.dispatchPointer(rootX, rootY)) return true;
        if (!viewport.translated(absoluteBounds().x(), absoluteBounds().y()).contains(rootX, rootY))
            return true;
        return content != null && content.dispatchPointer(rootX + scrollX, rootY + scrollY);
    }

    @Override public boolean dispatchScroll(int rootX, int rootY, double delta, boolean horizontal) {
        if (!hit(rootX, rootY)) return false;
        boolean overContent = viewport.translated(absoluteBounds().x(), absoluteBounds().y())
                .contains(rootX, rootY);
        if (overContent && content != null && content.dispatchScroll(rootX + scrollX, rootY + scrollY,
                delta, horizontal)) return true;
        int amount = -(int) Math.signum(delta) * wheelStep;
        if (amount == 0) return false;
        if (horizontal || maxScrollY() == 0) {
            if (scrollBy(amount, 0)) return true;
            if (horizontal) return false;
        }
        return scrollBy(0, amount);
    }

    @Override public Control hitTop(int rootX, int rootY) {
        if (!hit(rootX, rootY)) return null;
        if (showVertical && verticalBar.hit(rootX, rootY)) return verticalBar;
        if (showHorizontal && horizontalBar.hit(rootX, rootY)) return horizontalBar;
        if (!viewport.translated(absoluteBounds().x(), absoluteBounds().y()).contains(rootX, rootY))
            return this;
        Control hit = content == null ? null : content.hitTop(rootX + scrollX, rootY + scrollY);
        return hit == null ? this : hit;
    }

    public boolean dragTo(int rootX, int rootY) {
        if (dragging == null) return false;
        Rect track = dragging.absoluteBounds();
        int coordinate = dragging.vertical ? rootY : rootX;
        int start = dragging.vertical ? track.y() : track.x();
        int travel = Math.max(0, dragging.trackLength() - dragging.grabberLength());
        int position = clamp(coordinate - grabOffset - start, travel);
        int maximum = dragging.vertical ? maxScrollY() : maxScrollX();
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

    private void beginDrag(ScrollBar bar, int rootX, int rootY) {
        Rect thumb = bar.grabberBounds();
        int coordinate = bar.vertical ? rootY : rootX;
        int thumbStart = bar.vertical ? thumb.y() : thumb.x();
        grabOffset = thumb.contains(rootX, rootY) ? coordinate - thumbStart
                : (bar.vertical ? thumb.height() : thumb.width()) / 2;
        dragging = bar;
        dragTo(rootX, rootY);
    }

    public abstract static class ScrollBar extends Control {
        private final Scroll owner;
        private final boolean vertical;
        private BarMode mode = BarMode.AUTO;

        private ScrollBar(Scroll owner, boolean vertical) {
            this.owner = owner;
            this.vertical = vertical;
            minimum(BAR_SIZE, BAR_SIZE);
        }

        public BarMode mode() { return mode; }

        public ScrollBar mode(BarMode mode) {
            BarMode next = Objects.requireNonNull(mode);
            if (this.mode != next) {
                this.mode = next;
                owner.requestLayout();
            }
            return this;
        }

        private int trackLength() {
            return Math.max(0, vertical ? bounds().height() : bounds().width());
        }

        private int grabberLength() {
            int track = trackLength();
            int viewport = vertical ? owner.viewport.height() : owner.viewport.width();
            int extent = vertical ? owner.extent.height() : owner.extent.width();
            if (extent <= 0 || viewport >= extent) return track;
            return Math.min(track, Math.max(MIN_GRABBER_LENGTH,
                    (int) Math.round((double) track * viewport / extent)));
        }

        private Rect grabberBounds() {
            Rect bar = absoluteBounds();
            int length = grabberLength();
            int travel = Math.max(0, trackLength() - length);
            int maximum = vertical ? owner.maxScrollY() : owner.maxScrollX();
            int scroll = vertical ? owner.scrollY : owner.scrollX;
            int position = maximum == 0 ? 0 : (int) Math.round((double) travel * scroll / maximum);
            return vertical ? new Rect(bar.x(), bar.y() + position, BAR_SIZE, length)
                    : new Rect(bar.x() + position, bar.y(), length, BAR_SIZE);
        }

        @Override protected boolean onPointerPressed(int rootX, int rootY) {
            if (!hit(rootX, rootY)) return false;
            owner.beginDrag(this, rootX, rootY);
            return true;
        }

        @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
            Rect bar = screenBounds(screenX, screenY);
            int length = trackLength();
            if (length <= 0) return;
            GuiGraphics graphics = context.getGraphics();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (!owner.embedded) {
                if (vertical) {
                    int x = bar.x() + (BAR_SIZE - 2) / 2;
                    graphics.fill(x, bar.y() + TRACK_INSET, x + 2,
                            bar.bottom() - TRACK_INSET, TRACK_COLOR);
                } else {
                    int y = bar.y() + (BAR_SIZE - 2) / 2;
                    graphics.fill(bar.x() + TRACK_INSET, y,
                            bar.right() - TRACK_INSET, y + 2, TRACK_COLOR);
                }
            }
            Rect thumb = grabberBounds().translated(screenX, screenY);
            if (thumb.width() <= 0 || thumb.height() <= 0) return;
            WidgetThemeEntry<WidgetTheme> entry = context.getTheme().getWidgetTheme(
                    vertical ? OreTheme.V_GRABBER : OreTheme.H_GRABBER);
            if (entry == null) return;
            WidgetTheme style = entry.theme();
            IDrawable background = style.getBackground();
            if (background != null)
                background.draw(context, thumb.x(), thumb.y(), thumb.width(), thumb.height(), style);
        }
    }

    public static final class VScrollBar extends ScrollBar {
        private VScrollBar(Scroll owner) { super(owner, true); }

        @Override public VScrollBar mode(BarMode mode) { super.mode(mode); return this; }
    }

    public static final class HScrollBar extends ScrollBar {
        private HScrollBar(Scroll owner) { super(owner, false); }

        @Override public HScrollBar mode(BarMode mode) { super.mode(mode); return this; }
    }
}
