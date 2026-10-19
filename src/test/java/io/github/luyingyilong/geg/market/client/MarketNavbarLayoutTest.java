package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.Navbar;
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
            Control balance = new Control().setPreferred(90, 20).setMinimum(64, 20);
            Control search = new Control().setPreferred(138, 18).setMinimum(72, 18);
            Control filter = new Control().setPreferred(82, 20).setMinimum(74, 20);
            Navbar navbar = new Navbar(Component.literal("Market")).setExtra(balance, search, filter);
            int height = navbar.measureSize(font, new Size(width, 300)).height();
            navbar.layout(new Rect(0, 0, width, height), font);
            Control title = navbar.getChildren().get(0);
            check(title.getBounds().x() * 2 + title.getBounds().width() == width);
            check(balance.getMinimumSize().equals(new Size(64, 20)));
            check(search.getBounds().height() == 18);
            check(filter.getBounds().height() == 20);
            for (Control action : new Control[]{balance, search, filter}) {
                check(action.getBounds().width() >= action.getMinimumWidth());
                check(action.getBounds().right() <= width);
                check(action.getBounds().bottom() <= height);
                check(navbar.hitTop(action.getBounds().x(), action.getBounds().y()) == action);
            }
            if (height == Navbar.ROW_HEIGHT + Navbar.BOTTOM_PADDING) check(title.getBounds().right() + 6 <= balance.getBounds().x());
            else check(balance.getBounds().y() >= Navbar.ROW_HEIGHT);
            int rows = width <= 129 ? 4 : width == 200 ? 3 : width < 640 ? 2 : 1;
            check(height == rows * Navbar.ROW_HEIGHT + Navbar.BOTTOM_PADDING);
            for (Control action : new Control[]{balance, search, filter})
                check(action.getBounds().bottom() <= height - Navbar.BOTTOM_PADDING);
            if (balance.getBounds().y() == filter.getBounds().y())
                check(search.getBounds().right() + 6 <= filter.getBounds().x());
            filter.setVisible(false);
            check(navbar.getLayoutDirty());
        }
        Control search = new Control().setMinimum(64, 18).setPreferred(138, 18)
                .setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Control filter = new Control().setMinimum(20, 20);
        Box compact = new Box().setOrientation(Box.Orientation.HORIZONTAL);
        compact.addChild(search);
        compact.addChild(filter);
        Navbar navbar = new Navbar(Component.literal("Market")).setExtra(compact);
        navbar.layout(new Rect(0, 0, 640, Navbar.ROW_HEIGHT + Navbar.BOTTOM_PADDING), font);
        check(search.getSize().height() == 18 && filter.getSize().height() == 20);
        check(search.getAbsoluteBounds().y() == filter.getAbsoluteBounds().y() + 1);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected market navbar layout");
    }
}
