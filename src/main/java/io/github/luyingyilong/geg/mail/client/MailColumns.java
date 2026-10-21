package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.layout.ScrollContent;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import net.minecraft.client.gui.Font;

/** Preserve usable column widths in narrow GUIs; the surrounding Scroll exposes overflow. */
final class MailColumns extends Container implements ScrollContent {
    private final Box columns;
    private Size extent = new Size(0, 0);

    MailColumns(Control messages, Control detail) {
        var columnRow = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
        columnRow.addChild(new Column(messages, 100, 180, SizeFlags.SIZE_EXPAND_FILL));
        columnRow.addChild(new Column(detail, 120, 300, SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsStretchRatio(2.0F));
        columns = columnRow;
        addChild(columns);
    }

    @Override public Size measureSize(Font font, Size available) {
        return new Size(Math.max(224, available.width()), 35);
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        extent = new Size(Math.max(224, bounds.width()), Math.max(35, bounds.height()));
        fitChild(columns, new Rect(0, 0, extent.width(), extent.height()), font);
    }

    @Override public Size getContentExtent() { return extent; }
    @Override public void setVisibleRegion(Rect region) { }

    /** Long titles/body text must not determine how much width a column receives. */
    private static final class Column extends Container {
        private final Control content;
        private final int desiredWidth;

        Column(Control content, int minimumWidth, int desiredWidth, int flags) {
            this.content = content;
            this.desiredWidth = desiredWidth;
            setMinimum(minimumWidth, 35).setSizeFlagsHorizontal(flags).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
            this.addChild(content);
        }

        @Override public Size measureSize(Font font, Size available) { return new Size(desiredWidth, getMinimumHeight()); }

        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            fitChild(content, new Rect(0, 0, bounds.width(), bounds.height()), font);
        }
    }
}
