package io.github.luyingyilong.geg.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;


public record MarketDelivery(long id, ItemStack item, int quantity) {
    public MarketDelivery {
        if (id <= 0L || item.isEmpty() || quantity <= 0) {
            throw new IllegalArgumentException("Invalid delivery");
        }
        item = item.copyWithCount(1);
    }


    public MarketDelivery withQuantity(int value) {
        return new MarketDelivery(id, item, value);
    }


    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", id);
        tag.put("item", item.save(new CompoundTag()));
        tag.putInt("quantity", quantity);
        return tag;
    }


    public static MarketDelivery load(CompoundTag tag) {
        ItemStack item = ItemStack.of(tag.getCompound("item"));
        if (item.isEmpty()) {
            return null;
        }
        try {
            return new MarketDelivery(tag.getLong("id"), item, tag.getInt("quantity"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
