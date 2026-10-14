package io.github.luyingyilong.geg.market;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public record MarketBuyOrder(long id, UUID buyer, String buyerName, ItemStack item, int quantity,
                             long unitPrice, long escrow, long version, long createdAt) {
    public MarketBuyOrder {
        if (id <= 0 || buyer == null || item.isEmpty() || quantity <= 0 || unitPrice <= 0
                || escrow <= 0 || version <= 0 || Math.multiplyExact(unitPrice, quantity) != escrow) {
            throw new IllegalArgumentException("Invalid buy order");
        }
        item = item.copyWithCount(1);
    }

    public MarketBuyOrder withQuantity(int value) {
        return new MarketBuyOrder(id, buyer, buyerName, item, value, unitPrice,
                Math.multiplyExact(unitPrice, value), version + 1, createdAt);
    }

    public MarketBuyOrder withPrice(long value) {
        return new MarketBuyOrder(id, buyer, buyerName, item, quantity, value,
                Math.multiplyExact(value, quantity), version + 1, createdAt);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", id);
        tag.putUUID("buyer", buyer);
        tag.putString("buyerName", buyerName);
        tag.put("item", item.save(new CompoundTag()));
        tag.putInt("quantity", quantity);
        tag.putLong("unitPrice", unitPrice);
        tag.putLong("escrow", escrow);
        tag.putLong("version", version);
        tag.putLong("createdAt", createdAt);
        return tag;
    }

    public static MarketBuyOrder load(CompoundTag tag) {
        if (!tag.hasUUID("buyer")) return null;
        try {
            return new MarketBuyOrder(tag.getLong("id"), tag.getUUID("buyer"), tag.getString("buyerName"),
                    ItemStack.of(tag.getCompound("item")), tag.getInt("quantity"), tag.getLong("unitPrice"),
                    tag.getLong("escrow"), tag.getLong("version"), tag.getLong("createdAt"));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return null;
        }
    }
}
