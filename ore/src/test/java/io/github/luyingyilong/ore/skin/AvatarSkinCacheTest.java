package io.github.luyingyilong.ore.skin;

import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Disk-backed restart and asynchronous refresh contracts; texture decoding remains Minecraft's responsibility. */
public final class AvatarSkinCacheTest {
    private static final String FIRST = "https://textures.minecraft.net/texture/first";
    private static final String SECOND = "https://textures.minecraft.net/texture/second";
    private record Download(String url, Path image, Consumer<ResourceLocation> available) {
        void succeed() throws IOException {
            Files.createDirectories(image.getParent());
            Files.write(image, new byte[]{1, 2, 3});
            available.accept(texture());
        }
        ResourceLocation texture() { return ResourceLocation.fromNamespaceAndPath("geg", "avatars/" + image.getFileName()); }
    }
    private static final class Tasks implements Executor {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        @Override public void execute(Runnable command) { queue.add(command); }
        void next() { queue.remove().run(); }
        void drain() { while (!queue.isEmpty()) next(); }
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(Path.of("build/tmp"));
        Path root = Files.createTempDirectory(Path.of("build/tmp"), "ore-avatar-cache-test-");
        UUID id = UUID.randomUUID();
        GameProfile player = new GameProfile(id, "OfflineMember");
        Tasks tasks = new Tasks();
        List<Download> downloads = new ArrayList<>();
        List<ResourceLocation> published = new ArrayList<>();
        AvatarSkinCache firstSession = cache(root, tasks, profile -> FIRST, downloads);
        firstSession.load(player, published::add);
        check(downloads.isEmpty(), "Neither disk access nor profile lookup runs in the caller's thread");
        tasks.drain();
        check(downloads.size() == 1 && !Files.exists(root.resolve(id + ".json")),
                "Resolving a URL alone does not persist an unsuccessful download");
        Download first = downloads.get(0);
        first.succeed();
        tasks.drain();
        check(published.equals(List.of(first.texture())) && Files.exists(root.resolve(id + ".json")),
                "A decoded image persists its UUID mapping");

        List<Download> restored = new ArrayList<>();
        AvatarSkinCache restarted = cache(root, tasks, profile -> {
            check(restored.size() == 1 && Files.exists(restored.get(0).image()),
                    "Restore the local image before contacting the unavailable profile service");
            throw new IllegalStateException("offline");
        }, restored);
        restarted.load(new GameProfile(id, "RenamedMember"), published::add);
        tasks.drain();
        check(restored.size() == 1 && restored.get(0).url().equals(FIRST),
                "A fresh client instance restores the skin by UUID without online properties or the old name");
        restored.get(0).succeed();
        tasks.drain();
        check(published.get(published.size() - 1).equals(first.texture()), "The restored custom avatar is published");

        downloads.clear();
        published.clear();
        AvatarSkinCache refreshing = cache(root, tasks, profile -> SECOND, downloads);
        refreshing.load(player, published::add);
        tasks.drain();
        check(downloads.size() == 2 && downloads.get(0).url().equals(FIRST) && downloads.get(1).url().equals(SECOND),
                "Use the cached image and asynchronously request the changed skin");
        Download old = downloads.get(0), fresh = downloads.get(1);
        fresh.succeed();
        old.succeed();
        tasks.drain();
        check(published.equals(List.of(fresh.texture())), "A late cached result cannot overwrite the updated avatar");
        check(Files.readString(root.resolve(id + ".json")).contains(SECOND), "Persist the new successful skin");

        downloads.clear();
        AvatarSkinCache failedRefresh = cache(root, tasks, profile -> FIRST, downloads);
        failedRefresh.load(player, published::add);
        tasks.drain();
        downloads.get(0).succeed();
        downloads.get(1).available().accept(null);
        tasks.drain();
        check(Files.readString(root.resolve(id + ".json")).contains(SECOND),
                "A failed refresh never replaces the previous successful disk entry");

        downloads.clear();
        cache(root, tasks, profile -> null, downloads).load(new GameProfile(UUID.randomUUID(), "Other"), published::add);
        tasks.drain();
        check(downloads.isEmpty(), "Never reuse another player's cached skin");

        Files.writeString(root.resolve(id + ".json"), "broken json");
        cache(root, tasks, profile -> SECOND, downloads).load(player, published::add);
        tasks.drain();
        check(downloads.size() == 1 && downloads.get(0).url().equals(SECOND), "A corrupt index falls back to a fresh lookup");
        downloads.get(0).succeed();
        tasks.drain();
        Files.delete(downloads.get(0).image());
        downloads.clear();
        cache(root, tasks, profile -> null, downloads).load(player, published::add);
        tasks.drain();
        check(downloads.isEmpty(), "An index without its image is not mistaken for a ready local avatar");

        olderRequestCannotOverwrite(root.resolve("requests"), player);
        onlineOfflineModeSkinSurvivesRestart(root.resolve("online"), player);
    }

    private static void onlineOfflineModeSkinSurvivesRestart(Path root, GameProfile player) throws IOException {
        Tasks tasks = new Tasks();
        List<Download> downloads = new ArrayList<>();
        List<ResourceLocation> published = new ArrayList<>();
        AvatarSkinCache cache = cache(root, tasks, profile -> { throw new IllegalStateException("offline UUID"); }, downloads);
        cache.loadKnown(player, FIRST, published::add);
        cache.load(player, texture -> { });
        tasks.drain();
        check(downloads.size() == 1 && downloads.get(0).url().equals(FIRST),
                "An online provider URL bypasses the Mojang lookup for an offline-mode UUID");
        downloads.get(0).succeed();
        tasks.drain();
        check(Files.readString(root.resolve(player.getId() + ".json")).contains(FIRST),
                "A simultaneous unresolved offline lookup cannot prevent the online skin from being persisted");

        downloads.clear();
        cache(root, tasks, profile -> { throw new IllegalStateException("client restarted offline"); }, downloads)
                .load(new GameProfile(player.getId(), player.getName()), published::add);
        tasks.drain();
        check(downloads.size() == 1 && downloads.get(0).url().equals(FIRST),
                "A new client instance restores the online skin while its player is offline");
        downloads.get(0).succeed();
        tasks.drain();

        downloads.clear();
        published.clear();
        cache.loadKnown(player, SECOND, published::add);
        tasks.drain();
        cache.loadKnown(player, FIRST, published::add);
        tasks.drain();
        // The second observation reuses FIRST from disk; SECOND finishes after the newer online skin.
        downloads.get(2).succeed();
        downloads.get(1).succeed();
        tasks.drain();
        check(published.equals(List.of(downloads.get(2).texture())), "A superseded online download cannot change the avatar");
        check(Files.readString(root.resolve(player.getId() + ".json")).contains(FIRST),
                "A superseded online download cannot change the persistent skin");
    }

    private static void olderRequestCannotOverwrite(Path root, GameProfile player) throws IOException {
        Tasks tasks = new Tasks();
        List<Download> downloads = new ArrayList<>();
        AvatarSkinCache cache = cache(root, tasks, profile -> downloads.isEmpty() ? FIRST : SECOND, downloads);
        cache.load(player, texture -> { });
        tasks.drain();
        cache.load(player, texture -> { });
        tasks.drain();
        downloads.get(1).succeed();
        tasks.drain();
        downloads.get(0).succeed();
        tasks.drain();
        check(Files.readString(root.resolve(player.getId() + ".json")).contains(SECOND),
                "An older profile request cannot replace the newest successful index");
    }

    private static AvatarSkinCache cache(Path root, Tasks tasks,
                                         java.util.function.Function<GameProfile, String> resolver, List<Download> downloads) {
        return new AvatarSkinCache(root, tasks, resolver,
                (profile, url, image, available) -> downloads.add(new Download(url, image, available)));
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
