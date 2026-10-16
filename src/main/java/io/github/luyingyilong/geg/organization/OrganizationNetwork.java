package io.github.luyingyilong.geg.organization;

import io.github.luyingyilong.geg.GegMod;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class OrganizationNetwork {
    private static final String PROTOCOL_VERSION = "4";
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
        OrganizationSavedData data = OrganizationSavedData.get(player.server);
        List<OrganizationSyncPacket.Summary> summaries = data
                .memberships(player.getUUID()).stream()
                .map(organization -> OrganizationSyncPacket.Summary.from(organization,
                        player.server, data, player.getUUID())).toList();
        List<OrganizationSyncPacket.Directory> directory = data.directory().stream()
                .filter(organization -> organization.roleOf(player.getUUID()) == null)
                .limit(4096)
                .map(organization -> new OrganizationSyncPacket.Directory(organization.id(),
                        organization.name(), organization.joinPolicy(),
                        data.hasApplication(organization.id(), player.getUUID()))).toList();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OrganizationSyncPacket(summaries, directory, resultKey));
    }
}
