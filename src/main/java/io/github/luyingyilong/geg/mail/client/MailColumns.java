package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.ScrollContent;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import net.minecraft.client.gui.Font;

/** Preserve usable column widths in narrow GUIs; the surrounding Scroll exposes overflow. */
final class MailColumns extends Container implements ScrollContent {
    private final Box columns;
    private Size extent = new Size(0, 0);

    MailColumns(Control messages, Control detail) {
        columns = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4)
                .add(new Column(messages, 100, 180, SizeFlags.SIZE_EXPAND_FILL))
                .add(new Column(detail, 120, 300, SizeFlags.SIZE_EXPAND_FILL).sizeFlagsStretchRatio(2.0F));
        super.addChild(columns);
    }

    @Override public Size measureSize(Font font, Size available) {
        return new Size(Math.max(224, available.width()), 35);
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        extent = new Size(Math.max(224, bounds.width()), Math.max(35, bounds.height()));
        fitChild(columns, new Rect(0, 0, extent.width(), extent.height()), font);
    }

    @Override public Size contentExtent() { return extent; }
    @Override public void visibleRegion(Rect region) { }

    /** Long titles/body text must not determine how much width a column receives. */
    private static final class Column extends Container {
        private final Control content;
        private final int desiredWidth;

        Column(Control content, int minimumWidth, int desiredWidth, int flags) {
            this.content = content;
            this.desiredWidth = desiredWidth;
            minimum(minimumWidth, 35).sizeFlagsHorizontal(flags).sizeFlagsVertical(SizeFlags.SIZE_FILL);
            addChild(content);
        }

        @Override public Size measureSize(Font font, Size available) { return new Size(desiredWidth, minimumHeight()); }

        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            fitChild(content, new Rect(0, 0, bounds.width(), bounds.height()), font);
        }
    }
}
