package io.github.luyingyilong.ore.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** The online bridge uses the server identity, coalesces observations, and updates already-bound avatars. */
public final class AvatarSkinPrefetcherTest {
    public static void main(String[] args) {
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Member".getBytes(StandardCharsets.UTF_8));
        GameProfile player = new GameProfile(offline, "Member");
        player.getProperties().put("textures", new Property("textures", "unsigned-server-skin"));
        List<GameProfile> queried = new ArrayList<>();
        List<Consumer<AvatarSkinSource>> replies = new ArrayList<>();
        List<GameProfile> received = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        AvatarSkinPrefetcher prefetch = new AvatarSkinPrefetcher((profile, available) -> {
            queried.add(profile);
            replies.add(available);
        }, (profile, source) -> { received.add(profile); urls.add(source.url()); });
        check(prefetch.observe(player) && !prefetch.observe(player), "Observe an online player without querying every tick");
        queried.get(0).getProperties().clear();
        check(player.getProperties().containsKey("textures"), "Skin loaders must not mutate the live server profile");
        replies.get(0).accept(remote("https://skins.example.test/first.png"));
        check(received.get(0).getId().equals(offline), "Cache a provider's skin under the offline server UUID");
        player.getProperties().clear();
        player.getProperties().put("textures", new Property("textures", "new-server-skin"));
        check(prefetch.observe(player), "Changed online skin properties refresh immediately");
        replies.get(0).accept(remote("https://skins.example.test/stale.png"));
        check(urls.size() == 1, "An older online observation cannot publish a stale URL");
        replies.get(1).accept(remote("https://skins.example.test/second.png"));
        check(urls.size() == 2, "A refreshed online source can publish its URL");
        replies.get(1).accept(remote("file:///not-a-download"));
        check(urls.size() == 2, "Unsupported provider references are not persisted as HTTP downloads");
        prefetch.clear();
        replies.get(1).accept(remote("https://skins.example.test/disconnected.png"));
        check(urls.size() == 2 && prefetch.observe(player), "Reconnect permits a new observation and ignores old callbacks");
        GameProfile noProperties = new GameProfile(UUID.randomUUID(), "PatchedClientSkin");
        check(prefetch.observe(noProperties), "A skin loader can supply a skin even without server texture properties");
        replies.get(replies.size() - 1).accept(new AvatarSkinSource(null, java.nio.file.Path.of("provider-cache/skin.png")));
        check(urls.size() == 3 && urls.get(2) == null, "A local CSL image can be prefetched without any HTTP URL");
        check(!prefetch.observe(new GameProfile(null, "Unknown")), "An unknown identity cannot own a persistent cache entry");

        List<Consumer<ResourceLocation>> completed = new ArrayList<>();
        AvatarSkins skins = new AvatarSkins((profile, available) -> completed.add(available));
        var bound = skins.request(new GameProfile(offline, "Member"));
        check(bound.texture().equals(DefaultPlayerSkin.getDefaultSkin(offline)), "Start with a vanilla fallback");
        ResourceLocation custom = ResourceLocation.fromNamespaceAndPath("geg", "test/online-skin");
        skins.rememberOnline(offline, custom);
        check(bound.texture().equals(custom), "Online prefetch updates avatars that already hold a Skin object");
        completed.get(0).accept(ResourceLocation.fromNamespaceAndPath("geg", "test/stale-cached-skin"));
        check(bound.texture().equals(custom), "A slower offline lookup cannot overwrite the online skin");
        var next = skins.request(player);
        check(next.texture().equals(custom), "A new binding immediately reuses the known online skin");
        check(!skins.request(new GameProfile(UUID.randomUUID(), "Other")).texture().equals(custom),
                "Another UUID cannot inherit the cached online skin");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static AvatarSkinSource remote(String url) { return new AvatarSkinSource(url, null); }
}
