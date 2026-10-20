package io.github.luyingyilong.ore;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Host-provided or discovered client integration; implementations belong to their own modules.
 */
public interface OreClientExtension {
    default void registerThemes(io.github.luyingyilong.ore.theme.ThemeDB themes) {
    }

    void initialize(OreClientOptions options, IEventBus modBus, IEventBus forgeBus);
}
