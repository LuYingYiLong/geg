package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.input.OreInputMethod;
import io.github.luyingyilong.ore.skin.AvatarSkinEvents;
import io.github.luyingyilong.ore.theme.OreFontResources;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.util.OreToasts;
import io.github.luyingyilong.ore.window.WindowManager;
import net.minecraftforge.eventbus.api.IEventBus;
import java.nio.file.Path;
import java.util.Objects;

/** Explicit client-only bootstrap for an embedded library, not a Forge mod entry point. */
public final class OreClient {
    private static OreClientOptions options = OreClientOptions.defaults(Path.of("ore", "avatars"));
    private static boolean initialized;
    private OreClient() { }
    public static OreClientOptions getOptions() { return options; }
    public static synchronized void initialize(OreClientOptions supplied, IEventBus modBus, IEventBus forgeBus) {
        Objects.requireNonNull(supplied); Objects.requireNonNull(modBus); Objects.requireNonNull(forgeBus);
        if (initialized) return;
        options = supplied;
        OreTheme.register();
        modBus.addListener(OreFontResources::registerReloadListeners);
        forgeBus.register(OreInputMethod.class);
        forgeBus.register(io.github.luyingyilong.ore.compat.recipeviewer.RecipeViewerInputGuard.class);
        forgeBus.register(AvatarSkinEvents.class);
        forgeBus.register(WindowManager.class);
        forgeBus.register(io.github.luyingyilong.ore.window.ScreenHostEvents.class);
        forgeBus.register(io.github.luyingyilong.ore.window.ScreenInputBridge.class);
        forgeBus.register(OreToasts.class);
        initialized = true;
        java.util.ServiceLoader.load(OreClientExtension.class, OreClientExtension.class.getClassLoader())
                .forEach(extension -> extension.initialize(options, modBus, forgeBus));
    }
}
