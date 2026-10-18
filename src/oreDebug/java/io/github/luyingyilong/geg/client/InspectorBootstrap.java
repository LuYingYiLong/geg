package io.github.luyingyilong.geg.client;

import io.github.luyingyilong.ore.inspector.OreInspector;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only the GEG development client source set contains this bridge. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class InspectorBootstrap {
    private InspectorBootstrap() { }
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        // The key-registration event is already in flight, so register its key directly this time.
        OreInspector.initialize(MinecraftForge.EVENT_BUS);
        OreInspector.Registration.keys(event);
    }
}
