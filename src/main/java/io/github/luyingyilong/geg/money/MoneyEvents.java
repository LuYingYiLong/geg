package io.github.luyingyilong.geg.money;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class MoneyEvents {
    private MoneyEvents() {
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MoneyNetwork.sync(player, MoneyService.balance(player.server, player.getUUID()));
        }
    }
}
