package io.github.luyingyilong.geg.chat;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public record ChatMessage(UUID id, long sequence, long time, UUID sender, String senderName, Component text) {
    public ChatMessage {
        Objects.requireNonNull(id); Objects.requireNonNull(sender); Objects.requireNonNull(senderName);
        text = Objects.requireNonNull(text).copy();
        if (sequence <= 0 || senderName.length() > 40) throw new IllegalArgumentException("Invalid chat message");
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("id", id); tag.putLong("sequence", sequence);
        tag.putLong("time", time); tag.putUUID("sender", sender); tag.putString("name", senderName);
        tag.putString("text", Component.Serializer.toJson(text)); return tag;
    }
    public static ChatMessage load(CompoundTag tag) {
        String json = tag.getString("text");
        if (json.length() > 32768) throw new IllegalArgumentException("Oversized chat component");
        return new ChatMessage(tag.getUUID("id"), tag.getLong("sequence"), tag.getLong("time"),
                tag.getUUID("sender"), tag.getString("name"), Component.Serializer.fromJson(json));
    }
}
