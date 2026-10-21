package io.github.luyingyilong.ore.control.display.avatar;

import io.github.luyingyilong.ore.control.display.Label;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.display.avatar.skin.AvatarSkins;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Async identity changes and fallback layout without an OpenGL or authentication session. */
public final class AvatarTest {
    public static void main(String[] args) {
        check(Avatar.initial("  alice ").equals("A"), "Latin initial");
        check(Avatar.initial("橡木").equals("橡"), "Chinese initial");
        check(Avatar.initial("😀name").equals("😀"), "Keep the complete Unicode code point");
        check(Avatar.initial("  ").equals("?"), "Missing name fallback");
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().codePointCount(0, text.getString().length()) * 6; }
        };
        List<Consumer<ResourceLocation>> callbacks = new ArrayList<>();
        List<GameProfile> loaded = new ArrayList<>();
        AvatarSkins cache = new AvatarSkins((profile, available) -> { loaded.add(profile); callbacks.add(available); });
        Avatar avatar = new Avatar(cache::request);
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        avatar.setPlayer(alice, "alice");
        var first = avatar.skin();
        check(first.texture().equals(DefaultPlayerSkin.getDefaultSkin(alice)) && loaded.size() == 1,
                "A known UUID displays its vanilla default during download");
        check(avatar.skin() == first && loaded.size() == 1, "No per-frame request");
        avatar.setPlayer(alice, "alice");
        check(avatar.skin() == first && loaded.size() == 1, "Rebinding the same sender keeps its request");
        avatar.setPlayer(bob, "bob");
        var second = avatar.skin();
        ResourceLocation a = ResourceLocation.fromNamespaceAndPath("ore", "test/alice");
        callbacks.get(0).accept(a);
        check(first.texture().equals(a) && avatar.skin() == second
                        && second.texture().equals(DefaultPlayerSkin.getDefaultSkin(bob)),
                "An old callback cannot change the new sender");
        ResourceLocation b = ResourceLocation.fromNamespaceAndPath("ore", "test/bob");
        callbacks.get(1).accept(b);
        check(avatar.skin().texture().equals(b), "Current sender receives its texture");
        avatar.setPlayer(alice, "alice");
        check(avatar.skin() == first && loaded.size() == 2, "Reuse resolved skins across senders");
        avatar.setInitials("组织");
        check(avatar.skin() == null && avatar.getPlayerId() == null && avatar.getName().equals("组织"), "System sources do not query players");
        check(((Label) avatar.getChildren().get(0)).getText().getString().equals("组"), "Label renders the source initial");
        avatar.setPlayer(null, "OfflineLegacy");
        check(avatar.skin().texture() == null && loaded.size() == 2, "Do not guess an offline UUID from a name");
        Avatar failed = new Avatar(new AvatarSkins((profile, available) -> { throw new IllegalStateException("offline"); })::request);
        failed.setPlayer(alice, "alice");
        check(failed.skin().texture().equals(DefaultPlayerSkin.getDefaultSkin(alice)),
                "An unavailable authentication service keeps the bundled default for offline players");
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:OfflineLegacy".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var defaultSkin = cache.request(new GameProfile(offline, "OfflineLegacy"));
        check(defaultSkin.texture().equals(DefaultPlayerSkin.getDefaultSkin(offline)),
                "A player without a custom texture displays the UUID-selected default");
        callbacks.get(callbacks.size() - 1).accept(null);
        check(defaultSkin.texture().equals(DefaultPlayerSkin.getDefaultSkin(offline)), "A missing skin callback keeps the default");
        GameProfile profile = new GameProfile(alice, "alice");
        profile.getProperties().put("textures", new Property("textures", "old"));
        var oldSkin = cache.request(profile);
        loaded.get(loaded.size() - 1).getProperties().clear();
        check(profile.getProperties().containsKey("textures"), "Loading cannot mutate a live player profile");
        check(cache.request(new GameProfile(alice, "alice")) == oldSkin,
                "An offline identity reuses the last known online texture properties");
        profile.getProperties().clear();
        profile.getProperties().put("textures", new Property("textures", "updated"));
        var updatedSkin = cache.request(profile);
        check(callbacks.size() == 5, "A changed skin property has a new cache key");
        check(cache.request(new GameProfile(alice, "alice")) == updatedSkin,
                "Offline identities use the latest known skin after a property change");

        avatar.setInitials("a");
        Label name = new Label(Component.literal("Player name"));
        Label date = new Label(Component.literal("2026-10-17"));
        name.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        date.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        Box info = new Box().setGap(2);
        info.addChild(name);
        info.addChild(date);
        info.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Box row = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(6);
        row.addChild(avatar);
        row.addChild(info);
        for (int width : new int[]{80, 127, 128, 129, 400}) {
            row.layout(new Rect(0, 0, width, 32), font);
            check(avatar.getSize().equals(new Size(24, 24)) && avatar.getBounds().y() == 4, "Avatar stays square and centered");
            check(info.getSize().equals(new Size(width - 30, 32)) && info.getBounds().x() == 30, "Sender info fills remaining width and height");
        }
        avatar.setSize(32).setHatLayer(false);
        check(avatar.getMinimumSize().equals(new Size(32, 32)) && !avatar.getHatLayer(), "Reusable size and overlay configuration");
        check(new Avatar(cache::request).getHatLayer(), "Both skin layers enabled by default");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
