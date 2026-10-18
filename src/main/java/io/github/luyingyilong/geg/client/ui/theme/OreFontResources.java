package io.github.luyingyilong.geg.client.ui.theme;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class OreFontResources {
    private OreFontResources() { }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            OreTextMetrics.reload(resources);
            OreFonts.resourcesReloaded();
        });
    }
}
