package io.github.luyingyilong.ore.control.display.avatar.skin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Non-blocking skin requests shared by Ore avatars, separate from the server and its save data.
 */
public final class AvatarSkins {
    private static final AvatarSkins CLIENT = new AvatarSkins(AvatarSkins::loadClient);
    private static final AvatarSkinPrefetcher ONLINE = new AvatarSkinPrefetcher((profile, available) -> {
        // Use the same public entry point as PlayerInfo, allowing skin loader mods to supply their URL.
        Minecraft.getInstance().getSkinManager().registerSkins(profile, (type, location, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) available.accept(AvatarSkinSource.from(texture));
        }, false);
    }, (profile, source) -> {
        Minecraft minecraft = Minecraft.getInstance();
        clientCache(minecraft).loadKnown(profile, source, texture -> CLIENT.rememberOnline(profile.getId(), texture));
    });
    private static AvatarSkinCache persistent;
    private final Cache<Key, Skin> requests = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterAccess(10, TimeUnit.MINUTES).build();
    private final Cache<UUID, GameProfile> knownProfiles = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterAccess(10, TimeUnit.MINUTES).build();
    private final Cache<UUID, ResourceLocation> onlineTextures = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterAccess(10, TimeUnit.MINUTES).build();
    private final Loader loader;
    public AvatarSkins(Loader loader) {
        this.loader = java.util.Objects.requireNonNull(loader);
    }

    public static Skin forPlayer(GameProfile requested) {
        var connection = Minecraft.getInstance().getConnection();
        var online = connection == null ? null : requested.getId() != null
                ? connection.getPlayerInfo(requested.getId()) : connection.getPlayerInfo(requested.getName());
        return CLIENT.request(online == null ? requested : online.getProfile());
    }

    static boolean observeOnline(GameProfile profile) {
        return ONLINE.observe(profile);
    }

    static void clearOnlineObservations() {
        ONLINE.clear();
    }

    private static void loadClient(GameProfile profile, Consumer<ResourceLocation> available) {
        Minecraft minecraft = Minecraft.getInstance();
        clientCache(minecraft).load(profile, available);
    }

    private static synchronized AvatarSkinCache clientCache(Minecraft minecraft) {
        if (persistent == null) {
            persistent = new AvatarSkinCache(io.github.luyingyilong.ore.OreClient.getOptions().avatarCacheDirectory(),
                    Util.backgroundExecutor(), profile -> {
                var service = minecraft.getMinecraftSessionService();
                var textures = service.getTextures(profile, false);
                if (!textures.containsKey(MinecraftProfileTexture.Type.SKIN)) {
                    GameProfile filled = service.fillProfileProperties(profile, false);
                    if (filled != null) textures = service.getTextures(filled, false);
                }
                MinecraftProfileTexture skin = textures.get(MinecraftProfileTexture.Type.SKIN);
                return skin == null ? null : skin.getUrl();
            }, (profile, url, image, available) -> registerTexture(minecraft, profile, url, image, available));
        }
        return persistent;
    }

    private static void registerTexture(Minecraft minecraft, GameProfile profile, String url, Path cache,
                                        Consumer<ResourceLocation> available) {
        minecraft.execute(() -> {
            try {
                ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("ore", "avatars/" + cache.getFileName());
                if (AvatarSkinSource.storedImage(url)) {
                    // CSL snapshots are already normalized; restore them independently of CSL or its URL.
                    NativeImage pixels;
                    try (var input = Files.newInputStream(cache)) {
                        pixels = NativeImage.read(input);
                    }
                    minecraft.getTextureManager().register(texture, new DynamicTexture(pixels));
                    available.accept(texture);
                    return;
                }
                // HttpTexture restores the cached image locally before attempting any download. Its callback
                // runs only after decoding, retaining legacy conversion, size validation and the hat layer.
                HttpTexture image = new HttpTexture(cache.toFile(), url, DefaultPlayerSkin.getDefaultSkin(profile.getId()),
                        true, () -> minecraft.execute(() -> available.accept(texture)));
                minecraft.getTextureManager().register(texture, image);
            } catch (IOException |
                     RuntimeException ignored) { /* Keep the last successful avatar if registration fails. */ }
        });
    }

    /**
     * A missing UUID cannot identify an offline player safely; keep the initials in that case.
     */
    public Skin request(GameProfile profile) {
        if (profile.getId() == null) return new Skin(null);
        GameProfile copy = new GameProfile(profile.getId(), profile.getName());
        copy.getProperties().putAll(profile.getProperties());
        if (copy.getProperties().containsKey("textures")) {
            knownProfiles.put(copy.getId(), copy);
        } else {
            // Offline member/mail records contain only identity, not the tab-list skin properties.
            GameProfile known = knownProfiles.getIfPresent(copy.getId());
            if (known != null) copy.getProperties().putAll(known.getProperties());
        }
        String textures = copy.getProperties().get("textures").stream().map(Property::getValue)
                .sorted().collect(Collectors.joining("\0"));
        return requests.asMap().computeIfAbsent(new Key(copy.getId(), textures), key -> {
            // No custom skin (including an offline-mode UUID) is a valid vanilla default avatar.
            Skin result = new Skin(copy.getId());
            ResourceLocation online = onlineTextures.getIfPresent(copy.getId());
            if (online != null) result.texture = online;
            GameProfile loading = new GameProfile(copy.getId(), copy.getName());
            loading.getProperties().putAll(copy.getProperties());
            try {
                loader.load(loading, texture -> {
                    ResourceLocation preferred = onlineTextures.getIfPresent(copy.getId());
                    if (preferred != null) result.texture = preferred;
                    else if (texture != null) result.texture = texture;
                });
            } catch (RuntimeException ignored) { /* Keep the bundled default if lookup is unavailable. */ }
            return result;
        });
    }

    void rememberOnline(UUID id, ResourceLocation texture) {
        if (texture == null) return;
        onlineTextures.put(id, texture);
        // Existing avatars hold their Skin object, so update them without requiring a UI rebuild.
        requests.asMap().forEach((key, skin) -> {
            if (key.id().equals(id)) skin.texture = texture;
        });
    }

    @FunctionalInterface
    public interface Loader {
        void load(GameProfile profile, Consumer<ResourceLocation> available);
    }

    public static final class Skin {
        private volatile ResourceLocation texture;

        private Skin(UUID id) {
            texture = id == null ? null : DefaultPlayerSkin.getDefaultSkin(id);
        }

        public ResourceLocation texture() {
            return texture;
        }
    }

    private record Key(UUID id, String textures) {
    }
}
