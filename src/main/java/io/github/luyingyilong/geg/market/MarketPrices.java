package io.github.luyingyilong.geg.market;

import net.minecraft.world.item.ItemStack;

/** Suggested form defaults only: final prices and transactions remain server-validated. */
public final class MarketPrices {
    private MarketPrices() { }

    /** Lowest active sale for the complete item data, or one coin cent when none exists. */
    public static long suggestedUnitPrice(ItemStack item, Iterable<MarketSummary> summaries) {
        if (item.isEmpty()) return 1L;
        MarketKey key = MarketKey.of(item);
        long lowest = Long.MAX_VALUE;
        boolean found = false;
        for (MarketSummary summary : summaries) {
            if (summary.quantity() > 0 && summary.minPrice() > 0L && MarketKey.of(summary.item()).equals(key)) {
                lowest = Math.min(lowest, summary.minPrice());
                found = true;
            }
        }
        return found ? lowest : 1L;
    }
}
