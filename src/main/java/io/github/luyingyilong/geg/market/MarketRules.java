package io.github.luyingyilong.geg.market;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;


public final class MarketRules {
    public static final GameRules.Key<GameRules.BooleanValue> ENABLED = GameRules.register(
            "gegMarketEnabled", GameRules.Category.MISC,
            GameRules.BooleanValue.create(true, (server, value) -> MarketNetwork.syncViewers(server)));

    private MarketRules() {
    }

    public static void register() {
        // Loading this class registers the game rule before a world is opened.
    }

    public static boolean enabled(MinecraftServer server) {
        return server.overworld().getGameRules().getBoolean(ENABLED);
    }
}
