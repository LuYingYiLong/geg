package io.github.luyingyilong.geg.chat;

import io.github.luyingyilong.geg.GegMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ChatNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GegMod.MODID, "chat"), () -> VERSION, VERSION::equals, VERSION::equals);
    private ChatNetwork() { }
    public static void register() {
        CHANNEL.messageBuilder(ChatRequest.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder(ChatRequest::encode)
                .decoder(ChatRequest::decode).consumerNetworkThread(ChatRequest::handle).add();
        CHANNEL.messageBuilder(ChatSyncPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT).encoder(ChatSyncPacket::encode)
                .decoder(ChatSyncPacket::decode).consumerNetworkThread(ChatSyncPacket::handle).add();
    }
    public static void send(ChatRequest packet) { CHANNEL.sendToServer(packet); }
    public static void reply(ServerPlayer player, ChatSyncPacket packet) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet); }
}
