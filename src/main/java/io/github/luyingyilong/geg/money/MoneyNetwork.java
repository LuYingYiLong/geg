package io.github.luyingyilong.geg.money;

import io.github.luyingyilong.geg.GegMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;


public final class MoneyNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GegMod.MODID, "money"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );


    private MoneyNetwork() {
    }


    public static void register() {
        CHANNEL.registerMessage(
                0,
                MoneySyncPacket.class,
                MoneySyncPacket::encode,
                MoneySyncPacket::decode,
                MoneySyncPacket::handle
        );
    }


    public static void sync(ServerPlayer player, MoneyBalance balance) {
        if (player.connection == null) {
            return;
        }
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new MoneySyncPacket(balance.coinMinorUnits(), balance.gem())
        );
    }
}
