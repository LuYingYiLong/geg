package io.github.luyingyilong.geg.organization;

import io.github.luyingyilong.geg.GegMod;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class OrganizationNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GegMod.MODID, "organizations"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    private OrganizationNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, OrganizationActionPacket.class,
                OrganizationActionPacket::encode, OrganizationActionPacket::decode,
                OrganizationActionPacket::handle);
        CHANNEL.registerMessage(1, OrganizationSyncPacket.class,
                OrganizationSyncPacket::encode, OrganizationSyncPacket::decode,
                OrganizationSyncPacket::handle);
    }

    public static void request() {
        CHANNEL.sendToServer(OrganizationActionPacket.request());
    }

    public static void create(String name, String description, Organization.JoinPolicy policy) {
        CHANNEL.sendToServer(OrganizationActionPacket.create(name, description, policy));
    }

    public static void operate(OrganizationSavedData.Operation operation, java.util.UUID organizationId,
                               java.util.UUID targetId) {
        CHANNEL.sendToServer(OrganizationActionPacket.operate(operation, organizationId, targetId));
    }

    public static void sync(ServerPlayer player) {
        sync(player, "");
    }

    static void sync(ServerPlayer player, String resultKey) {
        List<OrganizationSyncPacket.Summary> summaries = OrganizationSavedData.get(player.server)
                .memberships(player.getUUID()).stream()
                .map(organization -> OrganizationSyncPacket.Summary.from(organization, player.server)).toList();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OrganizationSyncPacket(summaries, resultKey));
    }
}
