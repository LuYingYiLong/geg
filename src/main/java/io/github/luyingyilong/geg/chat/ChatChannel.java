package io.github.luyingyilong.geg.chat;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Server identity, independent of translated names and client selection. */
public record ChatChannel(Type type, UUID first, UUID second) {
    public enum Type { WORLD, ORGANIZATION, DIRECT }
    public static final UUID NONE = new UUID(0, 0);
    public static final ChatChannel WORLD = new ChatChannel(Type.WORLD, NONE, NONE);

    public ChatChannel {
        if (type == null || first == null || second == null) throw new IllegalArgumentException("Missing channel identity");
        if (type == Type.WORLD && (!first.equals(NONE) || !second.equals(NONE))
                || type == Type.ORGANIZATION && (first.equals(NONE) || !second.equals(NONE))
                || type == Type.DIRECT && (first.equals(NONE) || second.equals(NONE) || first.compareTo(second) >= 0))
            throw new IllegalArgumentException("Invalid channel identity");
    }
    public static ChatChannel organization(UUID id) { return new ChatChannel(Type.ORGANIZATION, id, NONE); }
    public static ChatChannel direct(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? new ChatChannel(Type.DIRECT, a, b) : new ChatChannel(Type.DIRECT, b, a);
    }
    public boolean includes(UUID player) { return type == Type.DIRECT && (first.equals(player) || second.equals(player)); }
    public UUID other(UUID player) {
        if (!includes(player)) throw new IllegalArgumentException("Not a participant");
        return first.equals(player) ? second : first;
    }
    public String key() { return type.name() + ":" + first + ":" + second; }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putString("type", type.name());
        tag.putUUID("first", first); tag.putUUID("second", second); return tag;
    }
    public static ChatChannel load(CompoundTag tag) {
        return new ChatChannel(Type.valueOf(tag.getString("type")), tag.getUUID("first"), tag.getUUID("second"));
    }
}
