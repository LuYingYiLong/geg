package io.github.luyingyilong.ore;

import net.minecraftforge.eventbus.api.IEventBus;

/** Optional client integration discovered once by OreClient; providers belong to their own modules. */
public interface OreClientExtension {
    default void registerThemes(io.github.luyingyilong.ore.theme.ThemeDB themes) { }
    void initialize(OreClientOptions options, IEventBus modBus, IEventBus forgeBus);
}
