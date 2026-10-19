package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;

/** Wrap at the viewport width; expand short bodies and scroll vertically for long bodies. */
final class MailDetailBody extends Container implements ScrollContent {
    private final Label body;
    private final Box column = new Box().setGap(4);
    private Size extent = new Size(0, 0);

    MailDetailBody(Label body, Control... footer) {
        this.body = body;
        body.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        column.addChild(body);
        for (Control child : footer) { column.addChild(child); }
        this.addChild(column);
    }

    @Override public Size measureSize(Font font, Size available) {
        return getMinimumSize();
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        int naturalHeight = body.getVisible() ? body.lines(font, bounds.width()).size() * body.lineHeight(font) : 0;
        if (body.getMinimumHeight() != naturalHeight) body.setMinimum(0, naturalHeight);
        Size measured = column.measureSize(font, new Size(bounds.width(), bounds.height()));
        extent = new Size(bounds.width(), Math.max(bounds.height(), measured.height()));
        fitChild(column, new Rect(0, 0, extent.width(), extent.height()), font);
    }

    @Override public Size getContentExtent() { return extent; }
    @Override public void setVisibleRegion(Rect region) { }
}
