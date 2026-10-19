package io.github.luyingyilong.ore.skin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

/**
 * A skin can be backed by a provider's local PNG, without having a downloadable URL.
 */
record AvatarSkinSource(String url, Path image) {
    static AvatarSkinSource from(MinecraftProfileTexture texture) {
        Path image = null;
        try {
            // CSL 15.1 FakeMinecraftProfileTexture exposes this for remote, LOCAL and BASE64 skins.
            // Reflection keeps CSL optional and avoids linking its version-specific Minecraft classes.
            Object cached = texture.getClass().getMethod("getCacheFile").invoke(texture);
            if (cached instanceof File file) image = file.toPath();
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError ignored) {
            // Vanilla profile textures only expose a URL.
        }
        return new AvatarSkinSource(texture.getUrl(), image);
    }

    static boolean downloadable(String url) {
        return url != null && url.length() <= 4096 && (url.startsWith("https://") || url.startsWith("http://"));
    }

    static boolean storedImage(String key) {
        return key != null && key.matches("csl:[0-9a-f]{64}");
    }

    boolean valid() {
        return image != null || downloadable(url);
    }
}
