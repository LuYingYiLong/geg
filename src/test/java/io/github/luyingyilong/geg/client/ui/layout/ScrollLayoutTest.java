package io.github.luyingyilong.geg.client.ui.layout;

/** Both axes reserve space by default; embedded bars overlay content without tracks. */
public final class ScrollLayoutTest {
    public static void main(String[] args) {
        int[] contentClicks = {0};
        Control content = new Control() {
            @Override protected boolean onPointerPressed(int x, int y) { contentClicks[0]++; return true; }
        }.minimum(200, 240);
        Scroll scroll = new Scroll().content(content);
        Rect area = new Rect(10, 20, 100, 80);
        scroll.layout(area, null);
        check(!scroll.embedded());
        check(scroll.viewport().equals(new Rect(0, 0, 94, 74)));
        check(scroll.verticalBar().trackBounds().equals(new Rect(106, 22, 2, 70)));
        check(scroll.horizontalBar().trackBounds().equals(new Rect(12, 96, 90, 2)));
        check(scroll.verticalBar().grabberBounds().y() == scroll.verticalBar().absoluteBounds().y());
        check(scroll.horizontalBar().grabberBounds().x() == scroll.horizontalBar().absoluteBounds().x());
        check(scroll.dispatchPointer(105, 23) && contentClicks[0] == 0);
        check(scroll.dragTo(105, 100) && scroll.scrollY() == scroll.maxScrollY());
        check(scroll.endDrag());
        check(scroll.verticalBar().grabberBounds().bottom() == scroll.verticalBar().absoluteBounds().bottom());
        check(scroll.dispatchPointer(11, 95) && contentClicks[0] == 0);
        check(scroll.dragTo(110, 95) && scroll.scrollX() == scroll.maxScrollX());
        check(scroll.endDrag());
        check(scroll.horizontalBar().grabberBounds().right() == scroll.horizontalBar().absoluteBounds().right());

        scroll.embedded(true); scroll.layout(area, null);
        check(scroll.viewport().equals(new Rect(0, 0, 100, 80)));
        check(scroll.verticalBar().trackBounds().width() == 0 && scroll.horizontalBar().trackBounds().height() == 0);
        scroll.setScroll(0, 0);
        check(scroll.dispatchPointer(105, 23) && contentClicks[0] == 0);
        scroll.endDrag();
        scroll.embedded(false); scroll.layout(area, null);
        check(scroll.viewport().equals(new Rect(0, 0, 94, 74)));
        check(scroll.verticalBar().trackBounds().width() == 2);

        scroll.vertical(Scroll.BarMode.NEVER).horizontal(Scroll.BarMode.NEVER); scroll.layout(area, null);
        check(!scroll.verticalVisible() && !scroll.horizontalVisible());
        check(scroll.viewport().equals(new Rect(0, 0, 100, 80)) && scroll.scrollBy(10, 10));
        scroll.vertical(Scroll.BarMode.DISABLED).horizontal(Scroll.BarMode.DISABLED); scroll.layout(area, null);
        check(scroll.scrollX() == 0 && scroll.scrollY() == 0 && !scroll.scrollBy(10, 10));
        scroll.content(new Control().minimum(10, 10)).vertical(Scroll.BarMode.ALWAYS).horizontal(Scroll.BarMode.ALWAYS);
        scroll.layout(area, null);
        check(scroll.viewport().equals(new Rect(0, 0, 94, 74)) && scroll.verticalVisible() && scroll.horizontalVisible());
        scroll.layout(new Rect(0, 0, 3, 3), null);
        check(scroll.verticalBar().trackBounds().width() == 0 && scroll.horizontalBar().trackBounds().height() == 0);
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError("Scroll embedding/track regression"); }
}
