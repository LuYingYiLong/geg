package io.github.luyingyilong.geg.client.ui.skin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Observes server identities without replacing an offline-mode UUID with a skin provider's UUID. */
final class AvatarSkinPrefetcher {
    @FunctionalInterface interface Source {
        void load(GameProfile profile, Consumer<AvatarSkinSource> available);
    }
    private record Observation(String textures) { }
    private final Cache<UUID, Observation> observed = CacheBuilder.newBuilder()
            .maximumSize(1024).expireAfterWrite(5, TimeUnit.MINUTES).build();
    private final Source source;
    private final BiConsumer<GameProfile, AvatarSkinSource> available;

    AvatarSkinPrefetcher(Source source, BiConsumer<GameProfile, AvatarSkinSource> available) {
        this.source = source;
        this.available = available;
    }

    /** Returns whether a new lookup was started, allowing the tick bridge to limit its work. */
    synchronized boolean observe(GameProfile profile) {
        if (profile.getId() == null) return false;
        String textures = profile.getProperties().get("textures").stream().map(Property::getValue)
                .sorted().collect(Collectors.joining("\0"));
        Observation previous = observed.getIfPresent(profile.getId());
        if (previous != null && previous.textures().equals(textures)) return false;
        Observation observation = new Observation(textures);
        observed.put(profile.getId(), observation);
        GameProfile snapshot = new GameProfile(profile.getId(), profile.getName());
        snapshot.getProperties().putAll(profile.getProperties());
        try {
            source.load(snapshot, skin -> {
                if (skin == null || !skin.valid()) return;
                synchronized (AvatarSkinPrefetcher.this) {
                    if (observed.getIfPresent(snapshot.getId()) == observation) available.accept(snapshot, skin);
                }
            });
        } catch (RuntimeException ignored) { /* Retry on a later observation interval, without blocking the tick. */ }
        return true;
    }

    synchronized void clear() { observed.invalidateAll(); }
}
