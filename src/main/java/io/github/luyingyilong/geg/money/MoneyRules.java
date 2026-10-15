package io.github.luyingyilong.geg.money;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;


public final class MoneyRules {
    public static final GameRules.Key<GameRules.BooleanValue> LIGHTMANS_MIGRATION_ENABLED = GameRules.register(
            "gegLightmansMigrationEnabled", GameRules.Category.MISC, GameRules.BooleanValue.create(true));

    private MoneyRules() {
    }

    public static void register() {
        // Loading this class registers the game rule before a world is opened.
    }

    public static boolean lightmansMigrationEnabled(MinecraftServer server) {
        return server.overworld().getGameRules().getBoolean(LIGHTMANS_MIGRATION_ENABLED);
    }
}
