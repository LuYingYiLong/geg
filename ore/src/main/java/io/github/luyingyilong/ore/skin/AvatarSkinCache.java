package io.github.luyingyilong.ore.skin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/** Restores successful skin downloads before contacting the profile service. All disk IO is asynchronous. */
final class AvatarSkinCache implements AvatarSkins.Loader {
    @FunctionalInterface interface TextureLoader {
        /** Publish only after the image has been decoded successfully, not for its default fallback. */
        void load(GameProfile profile, String url, Path image, Consumer<ResourceLocation> available);
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path directory;
    private final Executor executor;
    private final Function<GameProfile, String> resolve;
    private final TextureLoader textures;
    private final Object storeLock = new Object();
    private record Request(boolean online) { }
    private final Cache<UUID, Request> latest = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterAccess(10, TimeUnit.MINUTES).build();

    AvatarSkinCache(Path directory, Executor executor, Function<GameProfile, String> resolve, TextureLoader textures) {
        this.directory = directory;
        this.executor = executor;
        this.resolve = resolve;
        this.textures = textures;
    }

    @Override public void load(GameProfile profile, Consumer<ResourceLocation> available) {
        load(profile, available, resolve, false);
    }

    /** The online skin manager may know a custom skin that the Mojang session service cannot resolve. */
    void loadKnown(GameProfile profile, String url, Consumer<ResourceLocation> available) {
        load(profile, available, ignored -> url, true);
    }

    void loadKnown(GameProfile profile, AvatarSkinSource source, Consumer<ResourceLocation> available) {
        load(profile, available, ignored -> importSource(source), true);
    }

    private String importSource(AvatarSkinSource source) {
        if (source.image() != null) {
            try {
                byte[] png = AvatarSkinImages.read(source.image());
                String key = "csl:" + Hashing.sha256().hashBytes(png).toString();
                Files.createDirectories(directory);
                Path target = image(key);
                if (!Files.isRegularFile(target)) {
                    Path temporary = Files.createTempFile(directory, "skin-", ".tmp");
                    try {
                        Files.write(temporary, png);
                        replace(temporary, target);
                    } finally { Files.deleteIfExists(temporary); }
                }
                return key;
            } catch (IOException | RuntimeException ignored) { /* An unfinished provider download may still have an HTTP URL. */ }
        }
        return AvatarSkinSource.downloadable(source.url()) ? source.url() : null;
    }

    private void load(GameProfile profile, Consumer<ResourceLocation> available,
                      Function<GameProfile, String> resolver, boolean online) {
        if (profile.getId() == null) return;
        Request request = new Request(online);
        // An unresolved offline UUID lookup must not supersede a skin supplied by the online client.
        latest.asMap().compute(profile.getId(), (id, previous) ->
                !online && previous != null && previous.online() ? previous : request);
        executor.execute(() -> {
            AtomicBoolean refreshed = new AtomicBoolean();
            String cached = read(profile.getId());
            if (cached != null) loadTexture(profile, cached, false, request, refreshed, available);
            try {
                String current = resolver.apply(profile);
                if (current != null && !current.equals(cached)) {
                    loadTexture(profile, current, true, request, refreshed, available);
                }
            } catch (RuntimeException ignored) { /* An offline profile service must not discard a cached skin. */ }
        });
    }

    private void loadTexture(GameProfile profile, String url, boolean fresh, Request request,
                             AtomicBoolean refreshed, Consumer<ResourceLocation> available) {
        try {
            textures.load(profile, url, image(url), texture -> {
                if (texture == null || (!fresh && refreshed.get())
                        || (request.online() && latest.getIfPresent(profile.getId()) != request)) return;
                if (fresh) refreshed.set(true);
                available.accept(texture);
                executor.execute(() -> {
                    synchronized (storeLock) {
                        // A slow cached download or older profile request cannot replace a newer skin index.
                        if (latest.getIfPresent(profile.getId()) != request || (!fresh && refreshed.get())) return;
                        save(profile.getId(), url);
                    }
                });
            });
        } catch (RuntimeException ignored) { /* Keep the last successful avatar if registration fails. */ }
    }

    private String read(UUID id) {
        Path index = directory.resolve(id + ".json");
        try {
            if (!Files.isRegularFile(index) || Files.size(index) > 8192) return null;
            JsonObject json = JsonParser.parseString(Files.readString(index, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("version").getAsInt() != 1) return null;
            String url = json.get("url").getAsString();
            if (!AvatarSkinSource.downloadable(url) && !AvatarSkinSource.storedImage(url)) return null;
            Path image = image(url);
            return Files.isRegularFile(image) && Files.size(image) > 0 ? url : null;
        } catch (IOException | RuntimeException ignored) { return null; }
    }

    private Path image(String url) {
        // Keep the existing URL-hashed image paths, so downloads made before this index remain reusable.
        return directory.resolve(Hashing.sha256().hashString(url, StandardCharsets.UTF_8).toString());
    }

    private void save(UUID id, String url) {
        Path temporary = null;
        try {
            if (!Files.isRegularFile(image(url)) || Files.size(image(url)) == 0) return;
            Files.createDirectories(directory);
            JsonObject json = new JsonObject();
            json.addProperty("version", 1);
            json.addProperty("url", url);
            temporary = Files.createTempFile(directory, id + "-", ".tmp");
            Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8);
            Path target = directory.resolve(id + ".json");
            replace(temporary, target);
        } catch (IOException | RuntimeException failure) {
            LOGGER.debug("Unable to persist Ore avatar for {}", id, failure);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { /* A leftover temporary file is never read as an index. */ }
            }
        }
    }

    private static void replace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
