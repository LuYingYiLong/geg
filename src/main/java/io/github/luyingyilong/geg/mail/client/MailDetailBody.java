package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;

/** Wrap at the viewport width; expand short bodies and scroll vertically for long bodies. */
final class MailDetailBody extends Container implements ScrollContent {
    private final Label body;
    private final Box column = new Box().gap(4);
    private Size extent = new Size(0, 0);

    MailDetailBody(Label body, Control... footer) {
        this.body = body;
        body.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        column.add(body);
        for (Control child : footer) column.add(child);
        addChild(column);
    }

    @Override public Size measureSize(Font font, Size available) {
        return minimumSize();
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        int naturalHeight = body.visible() ? body.lines(font, bounds.width()).size() * body.lineHeight(font) : 0;
        if (body.minimumHeight() != naturalHeight) body.minimum(0, naturalHeight);
        Size measured = column.measureSize(font, new Size(bounds.width(), bounds.height()));
        extent = new Size(bounds.width(), Math.max(bounds.height(), measured.height()));
        fitChild(column, new Rect(0, 0, extent.width(), extent.height()), font);
    }

    @Override public Size contentExtent() { return extent; }
    @Override public void visibleRegion(Rect region) { }
}
