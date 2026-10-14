package io.github.luyingyilong.geg.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public record MarketFavorite(long orderId, boolean buyOrder, ItemStack item, String ownerName,
                             int quantity, long unitPrice, boolean ended) {
    public MarketFavorite {
        if (orderId < 0 || item.isEmpty()) throw new IllegalArgumentException("Invalid favorite");
        item = item.copyWithCount(1);
    }

    public MarketFavorite markEnded() {
        return new MarketFavorite(orderId, buyOrder, item, ownerName, quantity, unitPrice, true);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("orderId", orderId);
        tag.putBoolean("buyOrder", buyOrder);
        tag.put("item", item.save(new CompoundTag()));
        tag.putString("ownerName", ownerName);
        tag.putInt("quantity", quantity);
        tag.putLong("unitPrice", unitPrice);
        tag.putBoolean("ended", ended);
        return tag;
    }

    public static MarketFavorite load(CompoundTag tag) {
        try {
            return new MarketFavorite(tag.getLong("orderId"), tag.getBoolean("buyOrder"),
                    ItemStack.of(tag.getCompound("item")), tag.getString("ownerName"),
                    tag.getInt("quantity"), tag.getLong("unitPrice"), tag.getBoolean("ended"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
