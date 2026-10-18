package io.github.luyingyilong.ore;

import net.minecraftforge.eventbus.api.IEventBus;

/** Optional client integration discovered once by OreClient; providers belong to their own modules. */
public interface OreClientExtension {
    void initialize(OreClientOptions options, IEventBus modBus, IEventBus forgeBus);
}
