package io.github.luyingyilong.geg.market;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;


public record MarketDetail(long id, boolean buyOrder, UUID owner, String ownerName, ItemStack item,
                           int quantity, long unitPrice, long version) {
    public MarketDetail {
        if (id <= 0L || owner == null || item.isEmpty() || quantity <= 0 || unitPrice <= 0L || version <= 0L)
            throw new IllegalArgumentException("Invalid market detail");
        item = item.copyWithCount(1);
    }


    public static MarketDetail of(MarketListing listing) {
        return new MarketDetail(listing.id(), false, listing.seller(), listing.sellerName(),
                listing.item(), listing.quantity(), listing.unitPrice(), listing.version());
    }


    public static MarketDetail of(MarketBuyOrder order) {
        return new MarketDetail(order.id(), true, order.buyer(), order.buyerName(),
                order.item(), order.quantity(), order.unitPrice(), order.version());
    }


    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", id);
        tag.putBoolean("buyOrder", buyOrder);
        tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        tag.put("item", item.save(new CompoundTag()));
        tag.putInt("quantity", quantity);
        tag.putLong("unitPrice", unitPrice);
        tag.putLong("version", version);
        return tag;
    }


    public static MarketDetail load(CompoundTag tag) {
        if (!tag.hasUUID("owner")) return null;
        try {
            return new MarketDetail(tag.getLong("id"), tag.getBoolean("buyOrder"), tag.getUUID("owner"),
                    tag.getString("ownerName"), ItemStack.of(tag.getCompound("item")), tag.getInt("quantity"),
                    tag.getLong("unitPrice"), tag.getLong("version"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
