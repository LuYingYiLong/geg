package io.github.luyingyilong.geg.money;

import net.minecraft.nbt.CompoundTag;

public record CoinAuditEntry(long sequence, long time, String reason, String currency,
                             String from, String to, long amount, String reference) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("sequence", sequence);
        tag.putLong("time", time);
        tag.putString("reason", reason);
        tag.putString("currency", currency);
        tag.putString("from", from);
        tag.putString("to", to);
        tag.putLong("amount", amount);
        tag.putString("reference", reference);
        return tag;
    }

    public static CoinAuditEntry load(CompoundTag tag) {
        return new CoinAuditEntry(tag.getLong("sequence"), tag.getLong("time"),
                tag.getString("reason"), tag.getString("currency"), tag.getString("from"),
                tag.getString("to"), tag.getLong("amount"), tag.getString("reference"));
    }
}
