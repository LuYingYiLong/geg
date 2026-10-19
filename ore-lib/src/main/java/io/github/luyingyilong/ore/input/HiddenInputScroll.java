package io.github.luyingyilong.ore.input;

import brachy.modularui.widget.scroll.HorizontalScrollData;
import brachy.modularui.widget.scroll.ScrollArea;
import brachy.modularui.widget.scroll.VerticalScrollData;

/**
 * Retain MUI's scroll offsets and wheel handling without a scrollbar gutter or hit target.
 */
public final class HiddenInputScroll {
    private HiddenInputScroll() {
    }

    public static HorizontalScrollData horizontal() {
        return new HorizontalScrollData() {
            @Override
            public int getThickness() {
                return 0;
            }

            @Override
            public boolean isInsideScrollbarArea(ScrollArea area, int x, int y) {
                return false;
            }
        };
    }

    public static VerticalScrollData vertical() {
        return new VerticalScrollData() {
            @Override
            public int getThickness() {
                return 0;
            }

            @Override
            public boolean isInsideScrollbarArea(ScrollArea area, int x, int y) {
                return false;
            }
        };
    }
}
