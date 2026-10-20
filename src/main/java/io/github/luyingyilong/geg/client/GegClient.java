package io.github.luyingyilong.geg.client;

import io.github.luyingyilong.ore.OreClient;
import io.github.luyingyilong.ore.OreClientOptions;
import io.github.luyingyilong.ore.theme.OreFonts;
import io.github.luyingyilong.ore.theme.OreSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class GegClient {
    private GegClient() { }
    public static void initialize(IEventBus modBus) {
        OreClient.initialize(new OreClientOptions("geg", "ore:ore", OreFonts.BODY, OreFonts.HERO,
                ResourceLocation.fromNamespaceAndPath("ore", "ore_fallback"), OreSounds::playDefault,
                Minecraft.getInstance().gameDirectory.toPath().resolve("geg/avatars")), modBus, MinecraftForge.EVENT_BUS);
    }
}
