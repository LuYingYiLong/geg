package io.github.luyingyilong.geg.mail;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record MailActionPacket(Action action, UUID id, MailState.Filter filter, int offset,
        Scope scope, boolean organizationMail, UUID organization, String target, String title, String body, long coin) {
    public enum Action { LIST, DETAIL, READ, CLAIM, DELETE, PREVIEW, CONFIRM }
    public enum Scope { PLAYER, ORGANIZATION, ALL }
    public static final UUID EMPTY = new UUID(0, 0);
    public static MailActionPacket query(Action action, UUID id, MailState.Filter filter, int offset) {
        return new MailActionPacket(action, id, filter, offset, Scope.PLAYER, false, EMPTY, "", "", "", 0);
    }
    public static void encode(MailActionPacket p, FriendlyByteBuf b) {
        b.writeEnum(p.action); b.writeUUID(p.id); b.writeEnum(p.filter); b.writeVarInt(p.offset);
        b.writeEnum(p.scope); b.writeBoolean(p.organizationMail); b.writeUUID(p.organization);
        b.writeUtf(p.target, 64); b.writeUtf(p.title, 128); b.writeUtf(p.body, 4096); b.writeLong(p.coin);
    }
    public static MailActionPacket decode(FriendlyByteBuf b) {
        return new MailActionPacket(b.readEnum(Action.class), b.readUUID(), b.readEnum(MailState.Filter.class),
                b.readVarInt(), b.readEnum(Scope.class), b.readBoolean(), b.readUUID(),
                b.readUtf(64), b.readUtf(128), b.readUtf(4096), b.readLong());
    }
    public static void handle(MailActionPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> { if (context.getSender() != null) MailService.handle(context.getSender(), p); });
        context.setPacketHandled(true);
    }
}
