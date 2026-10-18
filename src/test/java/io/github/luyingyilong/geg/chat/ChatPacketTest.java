package io.github.luyingyilong.geg.chat;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.*;

public final class ChatPacketTest {
    public static void main(String[] args) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(); ChatChannel channel = ChatChannel.direct(a, b);
        for (ChatRequest.Action action : ChatRequest.Action.values()) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ChatRequest request = new ChatRequest(action, UUID.randomUUID(), channel, 100, "你好\nworld");
                ChatRequest.encode(request, buffer); check(request.equals(ChatRequest.decode(buffer)));
            } finally { buffer.release(); }
        }
        for (ChatSyncPacket.Kind kind : ChatSyncPacket.Kind.values()) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                Component styled = Component.literal("website").withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.org"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("tip"))));
                ChatSyncPacket response = new ChatSyncPacket(kind,
                        List.of(new ChatSyncPacket.Conversation(channel, Component.literal("Bob"), a, 8, 55)),
                        List.of(new ChatSyncPacket.Player(b, "Bob", false)), channel,
                        List.of(new ChatMessage(UUID.randomUUID(), 55, 123, a, "Alice", styled)), true, b, "");
                ChatSyncPacket.encode(response, buffer); check(response.equals(ChatSyncPacket.decode(buffer)));
            } finally { buffer.release(); }
        }
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Chat protocol roundtrip failed"); }
}
