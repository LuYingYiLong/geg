package io.github.luyingyilong.geg.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;


public record MarketPurchase(ItemStack item, int quantity, long unitPrice, String sellerName, long purchasedAt) {
    public MarketPurchase {
        item = item.copyWithCount(1);
    }


    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("item", item.save(new CompoundTag()));
        tag.putInt("quantity", quantity);
        tag.putLong("unitPrice", unitPrice);
        tag.putString("sellerName", sellerName);
        tag.putLong("purchasedAt", purchasedAt);
        return tag;
    }


    public static MarketPurchase load(CompoundTag tag) {
        ItemStack item = ItemStack.of(tag.getCompound("item"));
        if (item.isEmpty() || tag.getInt("quantity") <= 0 || tag.getLong("unitPrice") <= 0L) {
            return null;
        }
        return new MarketPurchase(item, tag.getInt("quantity"), tag.getLong("unitPrice"),
                tag.getString("sellerName"), tag.getLong("purchasedAt"));
    }
}
