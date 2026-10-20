package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.theme.OreSounds;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Host-provided integrations; creating options never opens a window or loads native libraries.
 */
public record OreClientOptions(String hostModId, String defaultTheme, ResourceLocation bodyFont,
                               ResourceLocation heroFont, ResourceLocation fallbackFont,
                               Consumer<OreSounds.Cue> soundPlayer, Path avatarCacheDirectory) {
    public OreClientOptions {
        Objects.requireNonNull(hostModId);
        Objects.requireNonNull(defaultTheme);
        Objects.requireNonNull(bodyFont);
        Objects.requireNonNull(heroFont);
        Objects.requireNonNull(fallbackFont);
        Objects.requireNonNull(soundPlayer);
        Objects.requireNonNull(avatarCacheDirectory);
    }

    public static OreClientOptions defaults(Path cacheDirectory) {
        return defaults("ore", cacheDirectory);
    }

    public static OreClientOptions defaults(String hostModId, Path cacheDirectory) {
        return new OreClientOptions(hostModId, "ore:ore", font("ore"), font("ore_hero"), font("ore_fallback"),
                OreSounds::playDefault, cacheDirectory);
    }

    private static ResourceLocation font(String path) {
        return ResourceLocation.fromNamespaceAndPath("ore", path);
    }
}
