package io.github.luyingyilong.geg.market;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;


public final class MarketEvents {
    private MarketEvents() {
    }


    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MarketNetwork.close(player);
        }
    }
}
