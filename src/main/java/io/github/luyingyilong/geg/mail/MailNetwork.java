package io.github.luyingyilong.geg.mail;

import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.money.MoneySavedData;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MailNetwork {
    private static final String VERSION = "4";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GegMod.MODID, "mail"), () -> VERSION, VERSION::equals, VERSION::equals);
    private MailNetwork() { }
    public static void register() {
        CHANNEL.messageBuilder(MailActionPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MailActionPacket::encode).decoder(MailActionPacket::decode).consumerNetworkThread(MailActionPacket::handle).add();
        CHANNEL.messageBuilder(MailSyncPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MailSyncPacket::encode).decoder(MailSyncPacket::decode).consumerNetworkThread(MailSyncPacket::handle).add();
    }
    public static void send(MailActionPacket packet) { CHANNEL.sendToServer(packet); }
    public static void reply(ServerPlayer player, MailSyncPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    public static MailSyncPacket packet(ServerPlayer player, MailSyncPacket.Kind kind, String result) {
        var state = MoneySavedData.get(player.server).mail();
        return new MailSyncPacket(kind, state.revision(), state.unread(player.getUUID()), player.hasPermissions(2),
                MailState.Filter.ALL, 0, false, List.of(), null, false, MailActionPacket.EMPTY, 0, 0, "", result);
    }
    public static void status(ServerPlayer player) { status(player, true); }
    /** Synchronize counters even when a related operation already supplies the visible notification. */
    public static void status(ServerPlayer player, boolean notify) {
        reply(player, packet(player, notify ? MailSyncPacket.Kind.STATUS : MailSyncPacket.Kind.STATUS_SILENT, ""));
    }
}
