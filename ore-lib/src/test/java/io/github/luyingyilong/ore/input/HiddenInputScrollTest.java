package io.github.luyingyilong.ore.input;

import brachy.modularui.widget.scroll.ScrollArea;

public final class HiddenInputScrollTest {
    public static void main(String[] args) {
        ScrollArea area = new ScrollArea(0, 0, 80, 20);
        var horizontal = HiddenInputScroll.horizontal();
        var vertical = HiddenInputScroll.vertical();
        area.setScrollX(horizontal);
        area.setScrollY(vertical);
        horizontal.setScrollSize(200);
        vertical.setScrollSize(160);
        check(horizontal.getThickness() == 0 && vertical.getThickness() == 0);
        check(horizontal.isScrollBarActive(area) && vertical.isScrollBarActive(area));
        horizontal.scrollTo(area, 60);
        vertical.scrollTo(area, 50);
        check(horizontal.getScroll() == 60 && vertical.getScroll() == 50);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 80; x++)
            check(!area.isInsideScrollbarArea(x, y));
        vertical.scrollTo(area, Integer.MAX_VALUE);
        check(vertical.getScroll() == 140);
        horizontal.setScrollSize(0);
        horizontal.clamp(area);
        check(horizontal.getScroll() == 0);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Hidden native input scrollbar regression");
    }
}
