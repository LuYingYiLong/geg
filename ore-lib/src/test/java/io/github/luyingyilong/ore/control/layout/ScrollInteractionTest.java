package io.github.luyingyilong.ore.control.layout;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.overlay.Overlay;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.event.WindowInputs;

/** Exercise pointer capture through the same Window entrypoints used by all three screens. */
public final class ScrollInteractionTest {
    public static void main(String[] args) {
        for (boolean embedded : new boolean[]{false, true}) {
            Scroll scroll = new Scroll().setEmbedded(embedded).setContent(new Control().setMinimum(300, 400));
            Window window = new Window(true).setContent(scroll);
            window.layoutViewport(100, 80, null);
            scroll.setScroll(50, 100);
            Rect thumb = scroll.getVerticalBar().grabberBounds();
            check(io.github.luyingyilong.ore.event.WindowInputs.press(window,thumb.x() + 1, thumb.y() + 2, null), "Vertical press");
            check(scroll.getScrollY() == 100, "Press must not apply own scroll offset");
            int expected = positionAfterDrag(scroll, scroll.getVerticalBar(), 10, true);
            check(io.github.luyingyilong.ore.event.WindowInputs.move(window,thumb.x() + 1, thumb.y() + 12), "Captured vertical drag");
            check(scroll.getScrollY() == expected, "Vertical coordinates agree with geometry");
            check(io.github.luyingyilong.ore.event.WindowInputs.move(window,-200, 500) && scroll.getScrollY() == scroll.getMaxScrollY(), "Drag outside viewport");
            check(io.github.luyingyilong.ore.event.WindowInputs.release(window,-200, 500) && !io.github.luyingyilong.ore.event.WindowInputs.move(window,10, 10), "Outside release ends capture");
            check(!scroll.dragTo(10, 10), "Release clears owner drag");

            scroll.setScroll(50, 100);
            thumb = scroll.getHorizontalBar().grabberBounds();
            check(io.github.luyingyilong.ore.event.WindowInputs.press(window,thumb.x() + 2, thumb.y() + 1, null), "Horizontal press");
            check(scroll.getScrollX() == 50, "Horizontal press keeps position");
            expected = positionAfterDrag(scroll, scroll.getHorizontalBar(), 10, false);
            io.github.luyingyilong.ore.event.WindowInputs.move(window,thumb.x() + 12, thumb.y() + 1);
            check(scroll.getScrollX() == expected, "Horizontal drag geometry");
            io.github.luyingyilong.ore.event.WindowInputs.release(window,500, -50);
            check(!scroll.dragTo(20, 20), "Horizontal release ends drag");

            for (int cancellation = 0; cancellation < 6; cancellation++) {
                scroll.setVertical(Scroll.BarMode.AUTO);
                scroll.setVisible(true);
                if (scroll.getParent() == null) window.setContent(scroll);
                window.setOverlay(new Overlay());
                window.layoutViewport(100, 80, null);
                scroll.setScroll(0, 0);
                thumb = scroll.getVerticalBar().grabberBounds();
                io.github.luyingyilong.ore.event.WindowInputs.press(window,thumb.x() + 1, thumb.y() + 1, null);
                switch (cancellation) {
                    case 0 -> scroll.setVisible(false);
                    case 1 -> scroll.setVertical(Scroll.BarMode.DISABLED);
                    case 2 -> window.removeChild(scroll);
                    case 3 -> window.cancelInteractions();
                    case 4 -> { var childContainer45 = io.github.luyingyilong.ore.window.WindowTestAccess.overlay(window);
        childContainer45.addChild(new Control() {
                        @Override public boolean getFocusScope() { return true; }
                    }); }
                    case 5 -> scroll.getVerticalBar().setVisible(false);
                    default -> throw new AssertionError();
                }
                io.github.luyingyilong.ore.event.WindowInputs.move(window,100, 500);
                check(!scroll.dragTo(100, 500), "Canceled drag cannot resume: " + cancellation);
                io.github.luyingyilong.ore.event.WindowInputs.release(window,100, 500);
            }
        }
        nestedScroll();
        themedTrackInset();
    }

    private static void themedTrackInset() {
        Scroll scroll = new Scroll().setEmbedded(false).setContent(new Control().setMinimum(300, 400));
        Window window = Window.embedded().setContent(scroll);
        try {
            window.layoutViewport(100, 80, null);
            Rect vertical = scroll.getVerticalBar().grabberBounds(), horizontal = scroll.getHorizontalBar().grabberBounds();
            scroll.setThemeOverride(Scroll.TRACK_INSET, 9);
            Rect vbar = scroll.getVerticalBar().getAbsoluteBounds(), hbar = scroll.getHorizontalBar().getAbsoluteBounds();
            check(scroll.getVerticalBar().trackBounds().y() == vbar.y() + 9
                    && scroll.getVerticalBar().trackBounds().height() == vbar.height() - 18, "Themed vertical track inset");
            check(scroll.getHorizontalBar().trackBounds().x() == hbar.x() + 9
                    && scroll.getHorizontalBar().trackBounds().width() == hbar.width() - 18, "Themed horizontal track inset");
            check(scroll.getVerticalBar().grabberBounds().equals(vertical) && scroll.getHorizontalBar().grabberBounds().equals(horizontal),
                    "Visual track inset does not shrink grabber travel or hit bounds");
            WindowInputs.press(window, vertical.x() + 1, vertical.y() + 1, null);
            WindowInputs.move(window, vertical.x() + 1, 500);
            WindowInputs.release(window, vertical.x() + 1, 500);
            check(scroll.getScrollY() == scroll.getMaxScrollY(), "Themed track still permits full drag travel");
            scroll.setEmbedded(true);
            check(scroll.getVerticalBar().trackBounds().height() == 0, "Embedded bars still omit the visual track");
        } finally { window.queueFree();LifecycleScheduler.flush(); }
    }

    private static int positionAfterDrag(Scroll scroll, Scroll.ScrollBar bar, int delta, boolean vertical) {
        Rect thumb = bar.grabberBounds(), track = bar.getAbsoluteBounds();
        int travel = vertical ? track.height() - thumb.height() : track.width() - thumb.width();
        int start = vertical ? thumb.y() - track.y() : thumb.x() - track.x();
        int maximum = vertical ? scroll.getMaxScrollY() : scroll.getMaxScrollX();
        return travel == 0 ? 0 : (int) Math.round((double) Math.min(travel, start + delta) * maximum / travel);
    }

    private static void nestedScroll() {
        Scroll inner = new Scroll().setContent(new Control().setMinimum(300, 400));
        inner.setPosition(40, 80).setRequestedSize(100, 80);
        Control content = new Control().setMinimum(400, 600);
        content.addChild(inner);
        Scroll outer = new Scroll().setContent(content);
        Window window = new Window(true).setContent(outer);
        window.layoutViewport(160, 140, null);
        outer.setScroll(30, 60);
        inner.setScroll(50, 100);
        Rect thumb = inner.getVerticalBar().grabberBounds();
        int x = thumb.x() + 1 - outer.getScrollX(), y = thumb.y() + 2 - outer.getScrollY();
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null), "Nested bar is hittable");
        check(inner.getScrollY() == 100, "Nested press keeps position");
        int expected = positionAfterDrag(inner, inner.getVerticalBar(), 10, true);
        check(io.github.luyingyilong.ore.event.WindowInputs.move(window,x, y + 10), "Nested capture");
        check(inner.getScrollY() == expected && outer.getScrollY() == 60 && outer.getScrollX() == 30,
                "Apply outer content offset exactly once");
        io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y + 10);
        check(!inner.dragTo(x, y), "Nested release ends drag");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
