package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Memberships received from the server for the current player. */
@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class ClientOrganizationState {
    private static List<OrganizationSyncPacket.Summary> organizations = List.of();
    private static List<OrganizationSyncPacket.Directory> directory = List.of();
    private static boolean loaded;

    private ClientOrganizationState() {
    }

    static boolean hasOrganizations() {
        return !organizations.isEmpty();
    }

    static boolean loaded() {
        return loaded;
    }

    static List<OrganizationSyncPacket.Summary> organizations() {
        return organizations;
    }

    static List<OrganizationSyncPacket.Directory> directory() {
        return directory;
    }

    static void acceptOrganizations(OrganizationSyncPacket packet) {
        organizations = List.copyOf(packet.organizations());
        directory = List.copyOf(packet.directory());
        loaded = true;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        organizations = List.of();
        directory = List.of();
        loaded = false;
    }
}
