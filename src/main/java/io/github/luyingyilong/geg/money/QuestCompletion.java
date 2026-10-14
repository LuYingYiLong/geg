package io.github.luyingyilong.geg.money;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record QuestCompletion(String key, long questId, UUID teamId, long completedAt,
                              Set<UUID> members) {
    public QuestCompletion {
        members = Set.copyOf(members);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putLong("questId", questId);
        tag.putUUID("teamId", teamId);
        tag.putLong("completedAt", completedAt);
        tag.putString("members", members.stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse(""));
        return tag;
    }

    public static QuestCompletion load(CompoundTag tag) {
        Set<UUID> members = new HashSet<>();
        for (String raw : tag.getString("members").split(",")) {
            try {
                members.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy members rather than rejecting the whole snapshot.
            }
        }
        return new QuestCompletion(tag.getString("key"), tag.getLong("questId"),
                tag.getUUID("teamId"), tag.getLong("completedAt"), members);
    }
}
