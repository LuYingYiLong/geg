package io.github.luyingyilong.geg.chat;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record ChatSyncPacket(Kind kind, List<Conversation> conversations, List<Player> players,
                             ChatChannel channel, List<ChatMessage> messages, boolean more, UUID request, String result) {
    public enum Kind { DIRECTORY, HISTORY, MESSAGE, RESULT, SELECT }
    public record Conversation(ChatChannel channel, Component title, UUID epoch, int unread, long latest) { }
    public record Player(UUID id, String name, boolean online) { }
    public static ChatSyncPacket result(UUID request, String result) {
        return new ChatSyncPacket(Kind.RESULT, List.of(), List.of(), ChatChannel.WORLD, List.of(), false, request, result);
    }
    public static void encode(ChatSyncPacket p, FriendlyByteBuf buffer) {
        CompoundTag root = new CompoundTag(); root.putString("kind", p.kind.name()); root.put("channel", p.channel.save());
        root.putBoolean("more", p.more); root.putUUID("request", p.request); root.putString("result", p.result);
        ListTag channels = new ListTag();
        for (Conversation c : p.conversations) {
            CompoundTag t = c.channel.save(); t.putString("title", Component.Serializer.toJson(c.title));
            t.putUUID("epoch", c.epoch); t.putInt("unread", c.unread); t.putLong("latest", c.latest); channels.add(t);
        } root.put("conversations", channels);
        ListTag players = new ListTag(); for (Player v : p.players) {
            CompoundTag t = new CompoundTag(); t.putUUID("id", v.id); t.putString("name", v.name); t.putBoolean("online", v.online); players.add(t);
        } root.put("players", players);
        ListTag messages = new ListTag(); p.messages.forEach(m -> messages.add(m.save())); root.put("messages", messages);
        buffer.writeNbt(root);
    }
    public static ChatSyncPacket decode(FriendlyByteBuf b) {
        CompoundTag root = Objects.requireNonNull(b.readNbt());
        ListTag channels = root.getList("conversations", Tag.TAG_COMPOUND), players = root.getList("players", Tag.TAG_COMPOUND);
        ListTag messages = root.getList("messages", Tag.TAG_COMPOUND);
        if (channels.size() > 8192 || players.size() > 4096 || messages.size() > 100) throw new IllegalArgumentException("Oversized chat response");
        return new ChatSyncPacket(Kind.valueOf(root.getString("kind")), channels.stream().map(raw -> {
            CompoundTag t = (CompoundTag) raw; return new Conversation(ChatChannel.load(t),
                    Objects.requireNonNull(Component.Serializer.fromJson(t.getString("title"))), t.getUUID("epoch"),
                    Math.max(0, Math.min(100, t.getInt("unread"))), t.getLong("latest"));
        }).toList(), players.stream().map(raw -> {
            CompoundTag t = (CompoundTag) raw; return new Player(t.getUUID("id"), t.getString("name"), t.getBoolean("online"));
        }).toList(), ChatChannel.load(root.getCompound("channel")), messages.stream().map(raw -> ChatMessage.load((CompoundTag) raw)).toList(),
                root.getBoolean("more"), root.getUUID("request"), root.getString("result"));
    }
    public static void handle(ChatSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get(); ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.github.luyingyilong.geg.chat.client.ClientChatState.accept(packet)));
        ctx.setPacketHandled(true);
    }
}
