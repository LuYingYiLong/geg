package io.github.luyingyilong.ore.theme;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class OreFontResources {
    private OreFontResources() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            io.github.luyingyilong.ore.OreClient.initializeThemes();
            ThemeDB.getDefault().reload(resources);
            io.github.luyingyilong.ore.text.OreText.reload(resources);
            OreFonts.resourcesReloaded();
            io.github.luyingyilong.ore.gui.UiLoader.resourcesReloaded(resources);
        });
    }
}
