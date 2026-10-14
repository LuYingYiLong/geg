package io.github.luyingyilong.geg.money;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

public final class MoneyEvents {
    private MoneyEvents() {
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MoneyNetwork.sync(player, MoneyService.balance(player.server, player.getUUID()));
            if (net.minecraftforge.fml.ModList.get().isLoaded("ftbquests")) {
                io.github.luyingyilong.geg.money.integration.FtbCoinIntegration.onLogin(player);
            }
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0) {
            CoinAuditService.flush(event.getServer());
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        CoinAuditService.flush(event.getServer());
    }
}
