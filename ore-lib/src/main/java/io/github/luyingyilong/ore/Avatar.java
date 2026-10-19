package io.github.luyingyilong.ore;

import brachy.modularui.screen.viewport.ModularGuiContext;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.skin.AvatarSkins;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** A square player face with the hat layer and vanilla default; unidentified sources use initials. */
public final class Avatar extends Container {
    public static final int DEFAULT_SIZE = 24;
    private final Label initial = ownPart("initial", new Label().align(Label.Horizontal.CENTER, Label.Vertical.CENTER));
    private final Function<GameProfile, AvatarSkins.Skin> skins;
    private GameProfile profile;
    private AvatarSkins.Skin skin;
    private boolean requested;
    private String name = "";
    private boolean hatLayer = true;
    private int backgroundColor = 0xFF58585A;

    public Avatar() { this(AvatarSkins::forPlayer); }

    Avatar(Function<GameProfile, AvatarSkins.Skin> skins) {
        this.skins = Objects.requireNonNull(skins);
        addChild(initial);
        sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        size(DEFAULT_SIZE);
        initials("");
    }

    public Avatar size(int pixels) {
        if (pixels < 1) throw new IllegalArgumentException("Avatar size must be positive");
        minimum(pixels, pixels).preferred(pixels, pixels);
        initial.fontSize(Math.max(8.0F, pixels / 2.0F));
        return this;
    }

    public Avatar player(UUID id, String name) {
        String display = Objects.requireNonNullElse(name, "");
        return id == null && display.isBlank() ? initials(display) : player(new GameProfile(id, display));
    }

    public Avatar player(GameProfile player) {
        Objects.requireNonNull(player);
        if (profile != null && profile.equals(player) && profile.getProperties().equals(player.getProperties())) return this;
        profile = new GameProfile(player.getId(), player.getName());
        profile.getProperties().putAll(player.getProperties());
        reset(Objects.requireNonNullElse(player.getName(), ""));
        return this;
    }

    /** Source-only/system identities deliberately do not issue a player-skin lookup. */
    public Avatar initials(String name) {
        profile = null;
        reset(Objects.requireNonNullElse(name, ""));
        return this;
    }

    private void reset(String name) {
        this.name = name;
        requested = false;
        skin = null;
        initial.text(Component.literal(initial(name))).visible(true);
    }

    public String name() { return name; }
    public UUID playerId() { return profile == null ? null : profile.getId(); }
    public Avatar hatLayer(boolean hatLayer) { this.hatLayer = hatLayer; return this; }
    public boolean hatLayer() { return hatLayer; }
    public Avatar backgroundColor(int color) { backgroundColor = color; return this; }
    public int backgroundColor() { return backgroundColor; }

    static String initial(String name) {
        String text = name.strip();
        return text.isEmpty() ? "?" : new String(Character.toChars(Character.toUpperCase(text.codePointAt(0))));
    }

    AvatarSkins.Skin skin() {
        if (!requested && profile != null) {
            requested = true;
            try { skin = skins.apply(profile); }
            catch (RuntimeException ignored) { /* A failed provider never blocks rendering initials. */ }
        }
        return skin;
    }

    @Override public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            fitChild(initial, new Rect(0, 0, bounds.width(), bounds.height()), font);
        }
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        var current = skin();
        var texture = current == null ? null : current.texture();
        initial.visible(texture == null);
        Rect area = screenBounds(screenX, screenY);
        var graphics = context.getGraphics();
        int side = Math.min(area.width(), area.height());
        int x = area.x() + (area.width() - side) / 2, y = area.y() + (area.height() - side) / 2;
        if (texture == null) {
            graphics.fill(x, y, x + side, y + side, backgroundColor);
        } else {
            graphics.flush();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            PlayerFaceRenderer.draw(graphics, texture, x, y, side, hatLayer, false);
            graphics.flush();
            RenderSystem.disableBlend();
        }
    }
}
