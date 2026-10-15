package io.github.luyingyilong.geg.client.ui.layout;

import net.minecraft.client.gui.Font;

/** Four controls arranged in one, two or three 25px rows based on minimum widths. */
public final class ResponsiveHeader extends Container {
    private static final int ROW_HEIGHT = 25;
    private static final int GAP = 6;
    private final Control title;
    private final Control balance;
    private final Control search;
    private final Control filter;

    public ResponsiveHeader(Control title, Control balance, Control search, Control filter) {
        this.title = title;
        this.balance = balance;
        this.search = search;
        this.filter = filter;
        addChild(title);
        addChild(balance);
        addChild(search);
        addChild(filter);
    }

    public int rows(int width) {
        int oneRow = title.minimumWidth() + balance.minimumWidth() + search.minimumWidth()
                + filter.minimumWidth() + 3 * GAP;
        if (width >= oneRow) return 1;
        return width >= search.minimumWidth() + filter.minimumWidth() + GAP ? 2 : 3;
    }

    @Override public Size measureSize(Font font, Size available) {
        return new Size(Math.max(minimumWidth(), available.width()),
                Math.max(minimumHeight(), ROW_HEIGHT * rows(available.width())));
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        int width = bounds.width();
        int count = rows(width);
        if (count == 1) {
            int balanceWidth = balance.minimumWidth();
            int searchWidth = search.minimumWidth();
            int filterWidth = filter.minimumWidth();
            int extra = Math.max(0, width - balanceWidth - searchWidth - filterWidth - 3 * GAP);
            int titleWidth = Math.min(60, extra);
            extra -= titleWidth;
            int grow = Math.min(extra, Math.max(0, search.measureWidth(font, width) - searchWidth));
            searchWidth += grow;
            extra -= grow;
            grow = Math.min(extra, Math.max(0, balance.measureWidth(font, width) - balanceWidth));
            balanceWidth += grow;
            extra -= grow;
            grow = Math.min(extra, Math.max(0, filter.measureWidth(font, width) - filterWidth));
            filterWidth += grow;
            titleWidth += extra - grow;
            int x = 0;
            fit(title, x, 0, titleWidth, font); x += titleWidth + GAP;
            fit(balance, x, 0, balanceWidth, font); x += balanceWidth + GAP;
            fit(search, x, 0, searchWidth, font); x += searchWidth + GAP;
            fit(filter, x, 0, filterWidth, font);
        } else {
            int balanceWidth = balance.minimumWidth();
            int titleWidth = Math.max(title.minimumWidth(), width - balanceWidth - GAP);
            fit(title, 0, 0, titleWidth, font);
            fit(balance, titleWidth + GAP, 0, balanceWidth, font);
            if (count == 2) {
                int filterWidth = Math.min(filter.measureWidth(font, width),
                        width - search.minimumWidth() - GAP);
                filterWidth = Math.max(filter.minimumWidth(), filterWidth);
                fit(search, 0, ROW_HEIGHT, Math.max(search.minimumWidth(), width - filterWidth - GAP), font);
                fit(filter, Math.max(search.minimumWidth(), width - filterWidth - GAP) + GAP,
                        ROW_HEIGHT, filterWidth, font);
            } else {
                fit(search, 0, ROW_HEIGHT, Math.max(width, search.minimumWidth()), font);
                fit(filter, 0, ROW_HEIGHT * 2, Math.max(width, filter.minimumWidth()), font);
            }
        }
    }

    private void fit(Control control, int x, int rowY, int width, Font font) {
        int height = Math.max(control.minimumHeight(), control.measureHeight(font, width));
        height = Math.min(ROW_HEIGHT, height);
        fitChild(control, new Rect(x, rowY + (ROW_HEIGHT - height) / 2, width, height), font);
    }
}
