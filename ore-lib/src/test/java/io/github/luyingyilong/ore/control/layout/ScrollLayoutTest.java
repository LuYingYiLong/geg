package io.github.luyingyilong.ore.control.layout;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;

/** Both axes reserve space by default; embedded bars overlay content without tracks. */
public final class ScrollLayoutTest {
    public static void main(String[] args) {
        int[] contentClicks = {0};
        Control content = new Control() {
            @Override protected boolean pointerDown(int x, int y) { contentClicks[0]++; return true; }
        }.setMinimum(200, 240);
        Scroll scroll = new Scroll().setContent(content);
        Rect area = new Rect(10, 20, 100, 80);
        scroll.layout(area, null);
        check(!scroll.getEmbedded());
        check(scroll.getViewport().equals(new Rect(0, 0, 94, 74)));
        check(scroll.getVerticalBar().trackBounds().equals(new Rect(106, 22, 2, 70)));
        check(scroll.getHorizontalBar().trackBounds().equals(new Rect(12, 96, 90, 2)));
        check(scroll.getVerticalBar().grabberBounds().y() == scroll.getVerticalBar().getAbsoluteBounds().y());
        check(scroll.getHorizontalBar().grabberBounds().x() == scroll.getHorizontalBar().getAbsoluteBounds().x());
        check(io.github.luyingyilong.ore.support.EventFixture.press(scroll,105, 23) && contentClicks[0] == 0);
        check(scroll.dragTo(105, 100) && scroll.getScrollY() == scroll.getMaxScrollY());
        check(scroll.endDrag());
        check(scroll.getVerticalBar().grabberBounds().bottom() == scroll.getVerticalBar().getAbsoluteBounds().bottom());
        check(io.github.luyingyilong.ore.support.EventFixture.press(scroll,11, 95) && contentClicks[0] == 0);
        check(scroll.dragTo(110, 95) && scroll.getScrollX() == scroll.getMaxScrollX());
        check(scroll.endDrag());
        check(scroll.getHorizontalBar().grabberBounds().right() == scroll.getHorizontalBar().getAbsoluteBounds().right());

        scroll.setEmbedded(true); scroll.layout(area, null);
        check(scroll.getViewport().equals(new Rect(0, 0, 100, 80)));
        check(scroll.getVerticalBar().trackBounds().width() == 0 && scroll.getHorizontalBar().trackBounds().height() == 0);
        scroll.setScroll(0, 0);
        check(io.github.luyingyilong.ore.support.EventFixture.press(scroll,105, 23) && contentClicks[0] == 0);
        scroll.endDrag();
        scroll.setEmbedded(false); scroll.layout(area, null);
        check(scroll.getViewport().equals(new Rect(0, 0, 94, 74)));
        check(scroll.getVerticalBar().trackBounds().width() == 2);

        scroll.setVertical(Scroll.BarMode.NEVER).setHorizontal(Scroll.BarMode.NEVER); scroll.layout(area, null);
        check(!scroll.getVerticalVisible() && !scroll.getHorizontalVisible());
        check(scroll.getViewport().equals(new Rect(0, 0, 100, 80)) && scroll.scrollBy(10, 10));
        scroll.setVertical(Scroll.BarMode.DISABLED).setHorizontal(Scroll.BarMode.DISABLED); scroll.layout(area, null);
        check(scroll.getScrollX() == 0 && scroll.getScrollY() == 0 && !scroll.scrollBy(10, 10));
        scroll.setContent(new Control().setMinimum(10, 10)).setVertical(Scroll.BarMode.ALWAYS).setHorizontal(Scroll.BarMode.ALWAYS);
        scroll.layout(area, null);
        check(scroll.getViewport().equals(new Rect(0, 0, 94, 74)) && scroll.getVerticalVisible() && scroll.getHorizontalVisible());
        scroll.layout(new Rect(0, 0, 3, 3), null);
        check(scroll.getVerticalBar().trackBounds().width() == 0 && scroll.getHorizontalBar().trackBounds().height() == 0);
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError("Scroll embedding/track regression"); }
}
