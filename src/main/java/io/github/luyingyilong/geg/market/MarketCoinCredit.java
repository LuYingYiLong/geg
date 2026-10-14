package io.github.luyingyilong.geg.market;

import net.minecraft.nbt.CompoundTag;

public record MarketCoinCredit(long id, long amount) {
    public MarketCoinCredit {
        if (id <= 0 || amount <= 0) throw new IllegalArgumentException("Invalid coin credit");
    }

    public MarketCoinCredit withAmount(long value) {
        return new MarketCoinCredit(id, value);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", id);
        tag.putLong("amount", amount);
        return tag;
    }

    public static MarketCoinCredit load(CompoundTag tag) {
        try {
            return new MarketCoinCredit(tag.getLong("id"), tag.getLong("amount"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
