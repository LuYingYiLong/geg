package io.github.luyingyilong.geg.money;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record QuestCredit(String key, UUID playerId, long questId, long completedAt,
                          long amount, boolean pending) {
    public QuestCredit paid() {
        return new QuestCredit(key, playerId, questId, completedAt, amount, false);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putUUID("player", playerId);
        tag.putLong("questId", questId);
        tag.putLong("completedAt", completedAt);
        tag.putLong("amount", amount);
        tag.putBoolean("pending", pending);
        return tag;
    }

    public static QuestCredit load(CompoundTag tag) {
        return new QuestCredit(tag.getString("key"), tag.getUUID("player"), tag.getLong("questId"),
                tag.getLong("completedAt"), tag.getLong("amount"), tag.getBoolean("pending"));
    }
}
