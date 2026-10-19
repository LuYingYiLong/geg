package io.github.luyingyilong.ore.layout;

/** Exercise pointer capture through the same Window entrypoints used by all three screens. */
public final class ScrollInteractionTest {
    public static void main(String[] args) {
        for (boolean embedded : new boolean[]{false, true}) {
            Scroll scroll = new Scroll().embedded(embedded).content(new Control().minimum(300, 400));
            Window window = new Window(true).content(scroll);
            window.layoutViewport(100, 80, null);
            scroll.setScroll(50, 100);
            Rect thumb = scroll.verticalBar().grabberBounds();
            check(io.github.luyingyilong.ore.event.WindowInputs.press(window,thumb.x() + 1, thumb.y() + 2, null), "Vertical press");
            check(scroll.scrollY() == 100, "Press must not apply own scroll offset");
            int expected = positionAfterDrag(scroll, scroll.verticalBar(), 10, true);
            check(io.github.luyingyilong.ore.event.WindowInputs.move(window,thumb.x() + 1, thumb.y() + 12), "Captured vertical drag");
            check(scroll.scrollY() == expected, "Vertical coordinates agree with geometry");
            check(io.github.luyingyilong.ore.event.WindowInputs.move(window,-200, 500) && scroll.scrollY() == scroll.maxScrollY(), "Drag outside viewport");
            check(io.github.luyingyilong.ore.event.WindowInputs.release(window,-200, 500) && !io.github.luyingyilong.ore.event.WindowInputs.move(window,10, 10), "Outside release ends capture");
            check(!scroll.dragTo(10, 10), "Release clears owner drag");

            scroll.setScroll(50, 100);
            thumb = scroll.horizontalBar().grabberBounds();
            check(io.github.luyingyilong.ore.event.WindowInputs.press(window,thumb.x() + 2, thumb.y() + 1, null), "Horizontal press");
            check(scroll.scrollX() == 50, "Horizontal press keeps position");
            expected = positionAfterDrag(scroll, scroll.horizontalBar(), 10, false);
            io.github.luyingyilong.ore.event.WindowInputs.move(window,thumb.x() + 12, thumb.y() + 1);
            check(scroll.scrollX() == expected, "Horizontal drag geometry");
            io.github.luyingyilong.ore.event.WindowInputs.release(window,500, -50);
            check(!scroll.dragTo(20, 20), "Horizontal release ends drag");

            for (int cancellation = 0; cancellation < 6; cancellation++) {
                scroll.vertical(Scroll.BarMode.AUTO);
                scroll.visible(true);
                if (scroll.parent() == null) window.content(scroll);
                window.overlay(new Overlay());
                window.layoutViewport(100, 80, null);
                scroll.setScroll(0, 0);
                thumb = scroll.verticalBar().grabberBounds();
                io.github.luyingyilong.ore.event.WindowInputs.press(window,thumb.x() + 1, thumb.y() + 1, null);
                switch (cancellation) {
                    case 0 -> scroll.visible(false);
                    case 1 -> scroll.vertical(Scroll.BarMode.DISABLED);
                    case 2 -> window.removeChild(scroll);
                    case 3 -> window.cancelInteractions();
                    case 4 -> window.overlayLayer().add(new Control() {
                        @Override public boolean focusScope() { return true; }
                    });
                    case 5 -> scroll.verticalBar().visible(false);
                    default -> throw new AssertionError();
                }
                io.github.luyingyilong.ore.event.WindowInputs.move(window,100, 500);
                check(!scroll.dragTo(100, 500), "Canceled drag cannot resume: " + cancellation);
                io.github.luyingyilong.ore.event.WindowInputs.release(window,100, 500);
            }
        }
        nestedScroll();
    }

    private static int positionAfterDrag(Scroll scroll, Scroll.ScrollBar bar, int delta, boolean vertical) {
        Rect thumb = bar.grabberBounds(), track = bar.absoluteBounds();
        int travel = vertical ? track.height() - thumb.height() : track.width() - thumb.width();
        int start = vertical ? thumb.y() - track.y() : thumb.x() - track.x();
        int maximum = vertical ? scroll.maxScrollY() : scroll.maxScrollX();
        return travel == 0 ? 0 : (int) Math.round((double) Math.min(travel, start + delta) * maximum / travel);
    }

    private static void nestedScroll() {
        Scroll inner = new Scroll().content(new Control().minimum(300, 400));
        inner.position(40, 80).requestedSize(100, 80);
        Control content = new Control().minimum(400, 600).addChild(inner);
        Scroll outer = new Scroll().content(content);
        Window window = new Window(true).content(outer);
        window.layoutViewport(160, 140, null);
        outer.setScroll(30, 60);
        inner.setScroll(50, 100);
        Rect thumb = inner.verticalBar().grabberBounds();
        int x = thumb.x() + 1 - outer.scrollX(), y = thumb.y() + 2 - outer.scrollY();
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,x, y, null), "Nested bar is hittable");
        check(inner.scrollY() == 100, "Nested press keeps position");
        int expected = positionAfterDrag(inner, inner.verticalBar(), 10, true);
        check(io.github.luyingyilong.ore.event.WindowInputs.move(window,x, y + 10), "Nested capture");
        check(inner.scrollY() == expected && outer.scrollY() == 60 && outer.scrollX() == 30,
                "Apply outer content offset exactly once");
        io.github.luyingyilong.ore.event.WindowInputs.release(window,x, y + 10);
        check(!inner.dragTo(x, y), "Nested release ends drag");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
