package io.github.luyingyilong.geg.mail;

import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public final class MailPacketTest {
    public static void main(String[] args) {
        UUID id = UUID.randomUUID();
        MailActionPacket action = new MailActionPacket(MailActionPacket.Action.PREVIEW, id, MailState.Filter.PENDING, 50,
                MailActionPacket.Scope.ORGANIZATION, true, UUID.randomUUID(), "Builders", "😀".repeat(64), "中文\nBody", Long.MAX_VALUE);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MailActionPacket.encode(action, buffer); check(MailActionPacket.decode(buffer).equals(action));
            buffer.clear();
            MailMessage message = new MailMessage(id, 123, MailMessage.Kind.SYSTEM, "Builders", "mail.geg.system.title",
                    "mail.geg.system.promote", List.of("Builders"), 0);
            MailSyncPacket sync = new MailSyncPacket(MailSyncPacket.Kind.DETAIL, 7, 3, true, MailState.Filter.ALL, 0, false,
                    List.of(new MailSyncPacket.Row(id, Component.literal("Hello"), "OP", MailMessage.Kind.ADMIN, 123, 100, false, false, MailActionPacket.Scope.PLAYER)),
                    message, true, id, 1, Long.MAX_VALUE, "92233720368547758.07", "");
            MailSyncPacket.encode(sync, buffer); check(MailSyncPacket.decode(buffer).equals(sync));
            buffer.clear();
            buffer.writeEnum(MailSyncPacket.Kind.LIST); buffer.writeLong(0); buffer.writeVarInt(0); buffer.writeBoolean(false);
            buffer.writeEnum(MailState.Filter.ALL); buffer.writeVarInt(0); buffer.writeBoolean(false); buffer.writeVarInt(51);
            try { MailSyncPacket.decode(buffer); throw new AssertionError("Oversized page accepted"); }
            catch (IllegalArgumentException expected) { }
        } finally { buffer.release(); }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Mail wire roundtrip regression"); }
}
