package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.geg.market.MarketDetail;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** Real row controls retain their labels across bindings and preserve the old text rectangles. */
public final class MarketOfferCellLayoutTest {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrap = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true); bootstrap.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        var status = new MarketCanvasWidget.OfferStatusCell();
        var price = new MarketCanvasWidget.OfferPriceCell();
        List<Control> statusLabels = status.getChildren();
        Control priceLabel = price.getChild(0);
        try {
            for (int width : new int[]{87, 127, 128, 129, 200}) for (int height : new int[]{10, 20, 23, 40}) {
                MarketDetail offer = offer(false, 5);
                status.bind(offer); price.bind(offer);
                status.layout(new Rect(7, 9, width, height), font);
                price.layout(new Rect(7, 9, width, height), font);
                int top = height >= 23 ? 3 : 0;
                check(status.getChild(0).getBounds().equals(new Rect(4, top, width - 8, 10)), "Status keeps its previous inset and baseline slot");
                check(status.getChild(1).getBounds().equals(new Rect(4, top + 10, width - 8, 10)), "Owner keeps the second line");
                check(priceLabel.getBounds().equals(new Rect(4, Math.max(0, (height - 10) / 2), width - 8, 10)), "Price stays vertically centered");
            }
            status.bind(offer(true, 8)); status.layout(new Rect(0, 0, 110, 23), font);
            check(((Label) status.getChild(0)).getThemeItem(Label.FONT_COLOR).equals(status.getThemeItem(OreTheme.TEXT_ACCENT_KEY)), "Buy status retains its accent role");
            check(((Label) status.getChild(1)).getThemeItem(Label.FONT_COLOR).equals(status.getThemeItem(OreTheme.TEXT_SECONDARY_KEY)), "Owner retains its secondary role");
            check(((Label) priceLabel).getText().getString().equals("0.25"), "Price is rebound from the same coin value");
            for (int i = 0; i < 500; i++) {
                MarketDetail offer = offer(i % 2 == 0, 1 + i);
                status.bind(offer); price.bind(offer);
                status.layout(new Rect(0, 0, 127 + i % 3, 23), font);
                price.layout(new Rect(0, 0, 127 + i % 3, 23), font);
            }
            check(status.getChildren().equals(statusLabels) && price.getChild(0) == priceLabel, "Repeated refreshes reuse the same per-row Labels");
            status.setVisible(false); status.draw(null, 0, 0);
            price.setVisible(false); price.draw(null, 0, 0);
        } finally {
            status.queueFree(); price.queueFree();
            for (Control label : statusLabels) label.draw(null, 0, 0);
            priceLabel.draw(null, 0, 0);
            LifecycleScheduler.flush();
        }
        check(statusLabels.stream().allMatch(Control::isDisposed) && priceLabel.isDisposed(), "Row destruction releases every retained Label");
        System.out.println("GEG_MARKET_OFFER_CELL_PASS");
    }

    private static MarketDetail offer(boolean buy, int count) {
        return new MarketDetail(1, buy, new UUID(0, 1), "Player", new ItemStack(Items.STONE), count, 25, 1);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
