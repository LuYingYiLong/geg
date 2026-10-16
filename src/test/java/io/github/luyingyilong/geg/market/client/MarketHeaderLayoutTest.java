package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.Header;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** The common header centers its title and preserves market action minimum sizes. */
public final class MarketHeaderLayoutTest {
    private MarketHeaderLayoutTest() {
    }

    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(net.minecraft.network.chat.FormattedText text) {
                return text.getString().length() * 6;
            }
        };
        for (int width : new int[]{127, 128, 129, 200, 320, 427, 640}) {
            Control balance = new Control().preferred(90, 20).minimum(64, 20);
            Control search = new Control().preferred(138, 18).minimum(72, 18);
            Control filter = new Control().preferred(82, 20).minimum(74, 20);
            Header header = new Header(Component.literal("Market")).extra(balance, search, filter);
            int height = header.measureSize(font, new Size(width, 300)).height();
            header.layout(new Rect(0, 0, width, height), font);
            Control title = header.children().get(0);
            check(title.bounds().x() * 2 + title.bounds().width() == width);
            check(balance.minimumSize().equals(new Size(64, 20)));
            check(search.bounds().height() == 18);
            check(filter.bounds().height() == 20);
            for (Control action : new Control[]{balance, search, filter}) {
                check(action.bounds().width() >= action.minimumWidth());
                check(action.bounds().right() <= width - 4);
                check(action.bounds().bottom() <= height);
                check(header.hitTop(action.bounds().x(), action.bounds().y()) == action);
            }
            if (height == 25) check(title.bounds().right() + 6 <= balance.bounds().x());
            else check(balance.bounds().y() >= 25);
            check(height == (width <= 129 ? 100 : width == 200 ? 75 : width < 640 ? 50 : 25));
            if (balance.bounds().y() == filter.bounds().y())
                check(search.bounds().right() + 6 <= filter.bounds().x());
            filter.visible(false);
            check(header.layoutDirty());
        }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected market header layout");
    }
}
