package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.OreClientExtension;
import io.github.luyingyilong.ore.OreClientOptions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Loaded only when the development client includes this module's service descriptor. */
public final class InspectorClientExtension implements OreClientExtension {
    @Override public void initialize(OreClientOptions options, IEventBus modBus, IEventBus forgeBus) {
        if (FMLEnvironment.production) return;
        java.util.List<io.github.luyingyilong.ore.gui.dev.UiSourceWatcher> xmlWatchers = new java.util.ArrayList<>();
        for (String root : System.getProperty("ore.gui.sourceRoots", "").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (root.isBlank()) continue;
            try { xmlWatchers.add(new io.github.luyingyilong.ore.gui.dev.UiSourceWatcher(java.nio.file.Path.of(root), io.github.luyingyilong.ore.gui.UiLoader.getDefault())); }
            catch (java.io.IOException failure) { org.slf4j.LoggerFactory.getLogger(InspectorClientExtension.class).warn("Cannot watch XML resources in {}", root, failure); }
        }
        if (xmlWatchers.isEmpty()) org.slf4j.LoggerFactory.getLogger(InspectorClientExtension.class)
                .info("XML source watching is disabled; configure ore.gui.sourceRoots to edit source XML without rebuilding");
        forgeBus.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent event) -> {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END)
                io.github.luyingyilong.ore.gui.dev.UiReloadStatus.tick();
        });
        forgeBus.addListener((net.minecraftforge.event.GameShuttingDownEvent event) -> xmlWatchers.forEach(io.github.luyingyilong.ore.gui.dev.UiSourceWatcher::close));

        io.github.luyingyilong.ore.gallery.OreGallery.initialize(forgeBus);
        modBus.addListener(io.github.luyingyilong.ore.gallery.OreGallery::registerKeys);
        OreInspector.initialize(forgeBus);
        modBus.addListener(OreInspector.Registration::keys);
    }
}
