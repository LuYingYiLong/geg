package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.Navbar;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** The common navbar centers its title and preserves market action minimum sizes. */
public final class MarketNavbarLayoutTest {
    private MarketNavbarLayoutTest() {
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
            Navbar navbar = new Navbar(Component.literal("Market")).extra(balance, search, filter);
            int height = navbar.measureSize(font, new Size(width, 300)).height();
            navbar.layout(new Rect(0, 0, width, height), font);
            Control title = navbar.children().get(0);
            check(title.bounds().x() * 2 + title.bounds().width() == width);
            check(balance.minimumSize().equals(new Size(64, 20)));
            check(search.bounds().height() == 18);
            check(filter.bounds().height() == 20);
            for (Control action : new Control[]{balance, search, filter}) {
                check(action.bounds().width() >= action.minimumWidth());
                check(action.bounds().right() <= width);
                check(action.bounds().bottom() <= height);
                check(navbar.hitTop(action.bounds().x(), action.bounds().y()) == action);
            }
            if (height == 25) check(title.bounds().right() + 6 <= balance.bounds().x());
            else check(balance.bounds().y() >= 25);
            check(height == (width <= 129 ? 100 : width == 200 ? 75 : width < 640 ? 50 : 25));
            if (balance.bounds().y() == filter.bounds().y())
                check(search.bounds().right() + 6 <= filter.bounds().x());
            filter.visible(false);
            check(navbar.layoutDirty());
        }
        Control search = new Control().minimum(64, 18).preferred(138, 18)
                .sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Control filter = new Control().minimum(20, 20);
        Box compact = new Box().orientation(Box.Orientation.HORIZONTAL).add(search).add(filter);
        Navbar navbar = new Navbar(Component.literal("Market")).extra(compact);
        navbar.layout(new Rect(0, 0, 640, 25), font);
        check(search.size().height() == 18 && filter.size().height() == 20);
        check(search.absoluteBounds().y() == filter.absoluteBounds().y() + 1);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected market navbar layout");
    }
}
