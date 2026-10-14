package io.github.luyingyilong.geg.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;


public record MarketSummary(ItemStack item, long quantity, long minPrice, long buyQuantity,
                            long maxBid, long representativeId) {
    public MarketSummary {
        if (item.isEmpty() || (quantity <= 0 && buyQuantity <= 0) || representativeId <= 0L) {
            throw new IllegalArgumentException("Invalid market summary");
        }
        item = item.copyWithCount(1);
    }


    public MarketSummary add(MarketListing listing) {
        long total = quantity > Long.MAX_VALUE - listing.quantity()
                ? Long.MAX_VALUE : quantity + listing.quantity();
        if (minPrice == 0L || listing.unitPrice() < minPrice) {
            return new MarketSummary(item, total, listing.unitPrice(), buyQuantity, maxBid, representativeId);
        }
        return new MarketSummary(item, total, minPrice, buyQuantity, maxBid, representativeId);
    }


    public MarketSummary add(MarketBuyOrder order) {
        long total = buyQuantity > Long.MAX_VALUE - order.quantity()
                ? Long.MAX_VALUE : buyQuantity + order.quantity();
        return new MarketSummary(item, quantity, minPrice, total,
                Math.max(maxBid, order.unitPrice()), representativeId);
    }


    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("item", item.save(new CompoundTag()));
        tag.putLong("quantity", quantity);
        tag.putLong("minPrice", minPrice);
        tag.putLong("buyQuantity", buyQuantity);
        tag.putLong("maxBid", maxBid);
        tag.putLong("representativeId", representativeId);
        return tag;
    }


    public static MarketSummary load(CompoundTag tag) {
        ItemStack item = ItemStack.of(tag.getCompound("item"));
        if (item.isEmpty()) {
            return null;
        }
        try {
            return new MarketSummary(item, tag.getLong("quantity"), tag.getLong("minPrice"),
                    tag.getLong("buyQuantity"), tag.getLong("maxBid"),
                    tag.getLong("representativeId"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
