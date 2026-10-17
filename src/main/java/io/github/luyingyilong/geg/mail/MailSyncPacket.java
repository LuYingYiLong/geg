package io.github.luyingyilong.geg.mail;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record MailSyncPacket(Kind kind, long revision, int unread, boolean admin, MailState.Filter filter,
        int offset, boolean more, List<Row> rows, MailMessage detail, boolean claimed,
        UUID token, int recipients, long coin, String total, String result) {
    public enum Kind { STATUS, LIST, DETAIL, PREVIEW, RESULT }
    public record Row(UUID id, Component title, String sender, MailMessage.Kind kind, long sentAt, long coin,
                      boolean read, boolean claimed, MailActionPacket.Scope scope) { }
    public static void encode(MailSyncPacket p, FriendlyByteBuf b) {
        b.writeEnum(p.kind); b.writeLong(p.revision); b.writeVarInt(p.unread); b.writeBoolean(p.admin);
        b.writeEnum(p.filter); b.writeVarInt(p.offset); b.writeBoolean(p.more); b.writeVarInt(p.rows.size());
        for (Row row : p.rows) {
            b.writeUUID(row.id); b.writeComponent(row.title); b.writeUtf(row.sender, 128);
            b.writeEnum(row.kind); b.writeLong(row.sentAt); b.writeLong(row.coin); b.writeBoolean(row.read); b.writeBoolean(row.claimed);
            b.writeEnum(row.scope);
        }
        b.writeBoolean(p.detail != null); if (p.detail != null) b.writeNbt(p.detail.save());
        b.writeBoolean(p.claimed); b.writeUUID(p.token); b.writeVarInt(p.recipients); b.writeLong(p.coin);
        b.writeUtf(p.total, 128); b.writeUtf(p.result, 96);
    }
    public static MailSyncPacket decode(FriendlyByteBuf b) {
        Kind kind = b.readEnum(Kind.class); long revision = b.readLong(); int unread = b.readVarInt();
        boolean admin = b.readBoolean(); MailState.Filter filter = b.readEnum(MailState.Filter.class);
        int offset = b.readVarInt(); boolean more = b.readBoolean(); int count = b.readVarInt();
        if (count < 0 || count > 50) throw new IllegalArgumentException("Invalid mail page");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) rows.add(new Row(b.readUUID(), b.readComponent(), b.readUtf(128), b.readEnum(MailMessage.Kind.class),
                b.readLong(), b.readLong(), b.readBoolean(), b.readBoolean(), b.readEnum(MailActionPacket.Scope.class)));
        MailMessage detail = b.readBoolean() ? MailMessage.load(Objects.requireNonNull(b.readNbt())) : null;
        return new MailSyncPacket(kind, revision, unread, admin, filter, offset, more, List.copyOf(rows), detail,
                b.readBoolean(), b.readUUID(), b.readVarInt(), b.readLong(), b.readUtf(128), b.readUtf(96));
    }
    public static void handle(MailSyncPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.github.luyingyilong.geg.mail.client.ClientMailScreen.accept(p)));
        context.setPacketHandled(true);
    }
}
