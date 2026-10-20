package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.input.OreInputMethod;
import io.github.luyingyilong.ore.skin.AvatarSkinEvents;
import io.github.luyingyilong.ore.theme.OreFontResources;
import io.github.luyingyilong.ore.util.OreToasts;
import io.github.luyingyilong.ore.window.WindowManager;
import net.minecraftforge.eventbus.api.IEventBus;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Explicit client-only bootstrap for an embedded library, not a Forge mod entry point.
 */
public final class OreClient {
    private static OreClientOptions options = OreClientOptions.defaults(Path.of("ore", "avatars"));
    private static boolean initialized, themesInitialized;
    private static java.util.List<OreClientExtension> extensions = java.util.List.of();

    private OreClient() {
    }

    public static OreClientOptions getOptions() {
        return options;
    }

    public static synchronized void initialize(OreClientOptions supplied, IEventBus modBus, IEventBus forgeBus,
                                               OreClientExtension... hostExtensions) {
        Objects.requireNonNull(supplied);
        Objects.requireNonNull(modBus);
        Objects.requireNonNull(forgeBus);
        if (initialized) return;
        options = supplied;
        // A Forge mod may merge several output directories and shadow identically named service files.
        // Host wiring is explicit; optional development integrations can still be discovered.
        var registered = new java.util.LinkedHashMap<Class<?>, OreClientExtension>();
        for (var extension : Objects.requireNonNull(hostExtensions))
            registered.putIfAbsent(Objects.requireNonNull(extension).getClass(), extension);
        java.util.ServiceLoader.load(OreClientExtension.class, OreClientExtension.class.getClassLoader())
                .forEach(extension -> registered.putIfAbsent(extension.getClass(), extension));
        extensions = java.util.List.copyOf(registered.values());
        modBus.addListener(OreFontResources::registerReloadListeners);
        forgeBus.register(OreInputMethod.class);
        forgeBus.register(io.github.luyingyilong.ore.compat.recipeviewer.RecipeViewerInputGuard.class);
        forgeBus.register(AvatarSkinEvents.class);
        forgeBus.register(WindowManager.class);
        forgeBus.register(io.github.luyingyilong.ore.window.ScreenHostEvents.class);
        forgeBus.register(io.github.luyingyilong.ore.window.ScreenInputBridge.class);
        forgeBus.register(OreToasts.class);
        initialized = true;
        extensions.forEach(extension -> extension.initialize(options, modBus, forgeBus));
    }

    /**
     * Called by the reload apply phase, never by parallel mod construction.
     */
    public static void initializeThemes() {
        if (themesInitialized) return;
        var themes = io.github.luyingyilong.ore.theme.ThemeDB.getDefault();
        io.github.luyingyilong.ore.input.Input.registerTheme(themes);
        io.github.luyingyilong.ore.input.NumberInput.registerTheme(themes);
        io.github.luyingyilong.ore.input.TextArea.registerTheme(themes);
        extensions.forEach(extension -> extension.registerThemes(themes));
        themes.getTheme(io.github.luyingyilong.ore.theme.OreTheme.DEFAULT_ID)
                .set(io.github.luyingyilong.ore.theme.OreTheme.TEXT_FONT, options.bodyFont())
                .set(io.github.luyingyilong.ore.theme.OreTheme.HERO_FONT, options.heroFont())
                .set(io.github.luyingyilong.ore.theme.OreTheme.INPUT_FONT, options.bodyFont());
        themes.setDefaultTheme(net.minecraft.resources.ResourceLocation.parse(options.defaultTheme()));
        themesInitialized = true;
    }

}
