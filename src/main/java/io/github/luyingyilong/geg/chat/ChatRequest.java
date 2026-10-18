package io.github.luyingyilong.geg.chat;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record ChatRequest(Action action, UUID request, ChatChannel channel, long cursor, String text) {
    public enum Action { DIRECTORY, HISTORY, SEND, READ, OPEN_DIRECT }
    public static ChatRequest query(Action action, ChatChannel channel, long cursor) {
        return new ChatRequest(action, UUID.randomUUID(), channel, cursor, "");
    }
    public static void encode(ChatRequest p, FriendlyByteBuf b) {
        b.writeEnum(p.action); b.writeUUID(p.request); b.writeNbt(p.channel.save()); b.writeLong(p.cursor); b.writeUtf(p.text, 1024);
    }
    public static ChatRequest decode(FriendlyByteBuf b) {
        return new ChatRequest(b.readEnum(Action.class), b.readUUID(), ChatChannel.load(java.util.Objects.requireNonNull(b.readNbt())),
                b.readLong(), b.readUtf(1024));
    }
    public static void handle(ChatRequest packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get(); ctx.enqueueWork(() -> { if (ctx.getSender() != null) ChatService.handle(ctx.getSender(), packet); });
        ctx.setPacketHandled(true);
    }
}
