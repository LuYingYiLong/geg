package io.github.luyingyilong.geg.client.theme;

import io.github.luyingyilong.ore.OreClientExtension;
import io.github.luyingyilong.ore.OreClientOptions;
import io.github.luyingyilong.ore.theme.ThemeDB;
import net.minecraftforge.eventbus.api.IEventBus;

/** Explicit host integration, applied on the client thread before theme resources are parsed. */
public final class GegThemeExtension implements OreClientExtension {
    @Override public void registerThemes(ThemeDB themes) { GegThemes.register(themes); }
    @Override public void initialize(OreClientOptions options, IEventBus modBus, IEventBus forgeBus) { }
}
