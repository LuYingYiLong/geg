package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;

/** The market header uses content-specific one-, two-, and three-row breakpoints. */
public final class MarketHeaderLayoutTest {
    private MarketHeaderLayoutTest() {
    }

    public static void main(String[] args) {
        for (int width : new int[]{127, 128, 129, 200, 320, 427}) {
            Control title = new Control().preferred(60, 20).minimum(0, 20)
                    .policy(Control.SizePolicy.EXPAND, Control.SizePolicy.CONTENT);
            Control balance = new Control().preferred(90, 20).minimum(64, 20);
            Control search = new Control().preferred(138, 18).minimum(72, 18);
            Control filter = new Control().preferred(82, 20).minimum(74, 20);
            MarketCanvasWidget.ResponsiveHeader header = new MarketCanvasWidget.ResponsiveHeader(
                    title, balance, search, filter);
            int innerWidth = width - 10;
            int rows = header.rows(innerWidth);
            header.layout(new Rect(0, 0, innerWidth, 25 * rows), null);
            check(title.size().width() == title.bounds().width());
            check(balance.minimumSize().equals(new Size(64, 20)));
            check(title.bounds().right() + 6 <= balance.bounds().x());
            check(search.bounds().height() == 18 && search.bounds().y() == (rows == 1 ? 3 : 28));
            check(filter.bounds().height() == 20 && filter.bounds().y()
                    == (rows == 3 ? 52 : rows == 2 ? 27 : 2));
            check(filter.bounds().right() <= innerWidth);
            if (rows <= 2) check(search.bounds().right() + 6 <= filter.bounds().x());
            check(rows == (width <= 129 ? 3 : width == 200 ? 2 : 1));
            if (width == 427) {
                check(title.size().width() > 60);
                check(balance.size().width() >= balance.minimumWidth());
                check(search.size().width() >= search.minimumWidth());
                check(filter.size().width() >= filter.minimumWidth());
            }
        }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected market header layout");
    }
}
