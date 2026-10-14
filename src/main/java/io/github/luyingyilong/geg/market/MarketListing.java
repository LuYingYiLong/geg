package io.github.luyingyilong.geg.market;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;


public record MarketListing(
        long id,
        UUID seller,
        String sellerName,
        ItemStack item,
        int quantity,
        long unitPrice,
        long version,
        long createdAt
) {
    public MarketListing {
        if (id <= 0L || seller == null || item.isEmpty() || quantity <= 0 || unitPrice <= 0L || version <= 0L) {
            throw new IllegalArgumentException("Invalid market listing");
        }
        item = item.copyWithCount(1);
    }


    public MarketListing withQuantity(int value) {
        return new MarketListing(id, seller, sellerName, item, value, unitPrice, version + 1L, createdAt);
    }


    public MarketListing withPrice(long value) {
        return new MarketListing(id, seller, sellerName, item, quantity, value, version + 1L, createdAt);
    }


    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", id);
        tag.putUUID("seller", seller);
        tag.putString("sellerName", sellerName);
        tag.put("item", item.save(new CompoundTag()));
        tag.putInt("quantity", quantity);
        tag.putLong("unitPrice", unitPrice);
        tag.putLong("version", version);
        tag.putLong("createdAt", createdAt);
        return tag;
    }


    public static MarketListing load(CompoundTag tag) {
        if (!tag.hasUUID("seller")) {
            return null;
        }
        ItemStack item = ItemStack.of(tag.getCompound("item"));
        if (item.isEmpty()) {
            return null;
        }
        try {
            return new MarketListing(
                    tag.getLong("id"), tag.getUUID("seller"), tag.getString("sellerName"), item,
                    tag.getInt("quantity"), tag.getLong("unitPrice"), tag.getLong("version"), tag.getLong("createdAt")
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
