package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.OreClientExtension;
import io.github.luyingyilong.ore.OreClientOptions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Loaded only when the development client includes this module's service descriptor. */
public final class InspectorClientExtension implements OreClientExtension {
    @Override public void initialize(OreClientOptions options, IEventBus modBus, IEventBus forgeBus) {
        if (FMLEnvironment.production) return;
        OreInspector.initialize(forgeBus);
        modBus.addListener(OreInspector.Registration::keys);
    }
}
