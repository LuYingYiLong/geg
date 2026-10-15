package io.github.luyingyilong.geg.organization;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class OrganizationEvents {
    private OrganizationEvents() {
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) OrganizationNetwork.sync(player);
    }
}
