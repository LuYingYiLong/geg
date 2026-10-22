package io.github.luyingyilong.ore.control.display.avatar;

import io.github.luyingyilong.ore.control.display.Label;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.control.display.avatar.skin.AvatarSkins;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * A square player face with the hat layer and vanilla default; unidentified sources use initials.
 */
public final class Avatar extends Container {
    public static final int DEFAULT_SIZE = 24;
    private final Label initial = ownPart("initial", new Label().setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER));
    private final Function<GameProfile, AvatarSkins.Skin> skins;
    private GameProfile profile;
    private AvatarSkins.Skin skin;
    private boolean requested;
    private String name = "";
    private boolean hatLayer = true;
    private int backgroundColor = 0xFF58585A;
    public Avatar() {
        this(AvatarSkins::forPlayer);
    }

    Avatar(Function<GameProfile, AvatarSkins.Skin> skins) {
        this.skins = Objects.requireNonNull(skins);
        updateChildren(java.util.List.of(initial));
        setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        setSize(DEFAULT_SIZE);
        setInitials("");
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Avatar");
    }

    static String initial(String name) {
        String text = name.strip();
        return text.isEmpty() ? "?" : new String(Character.toChars(Character.toUpperCase(text.codePointAt(0))));
    }

    @Override
    protected void validateChildren(java.util.List<io.github.luyingyilong.ore.control.base.Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Avatar has a fixed skeleton; configure its properties");
    }

    @io.github.luyingyilong.ore.gui.UiProperty(value = "size", setter = "setSize")
    public int getAvatarSize() { return getMinimumWidth(); }

    public Avatar setSize(int pixels) {
        if (pixels < 1) throw new IllegalArgumentException("Avatar size must be positive");
        setMinimum(pixels, pixels).setPreferred(pixels, pixels);
        initial.setThemeOverride(Label.FONT_SIZE, Math.max(8.0F, pixels / 2.0F));
        return this;
    }

    public Avatar setPlayer(UUID id, String name) {
        String display = Objects.requireNonNullElse(name, "");
        return id == null && display.isBlank() ? setInitials(display) : setPlayer(new GameProfile(id, display));
    }

    public GameProfile getPlayer() {
        if (profile == null) return null;
        GameProfile copy = new GameProfile(profile.getId(),profile.getName());
        copy.getProperties().putAll(profile.getProperties());
        return copy;
    }

    public Avatar setPlayer(GameProfile player) {
        Objects.requireNonNull(player);
        if (profile != null && profile.equals(player) && profile.getProperties().equals(player.getProperties()))
            return this;
        profile = new GameProfile(player.getId(), player.getName());
        profile.getProperties().putAll(player.getProperties());
        reset(Objects.requireNonNullElse(player.getName(), ""));
        return this;
    }

    /**
     * Source-only/system identities deliberately do not issue a player-skin lookup.
     */
    public Avatar setInitials(String name) {
        profile = null;
        reset(Objects.requireNonNullElse(name, ""));
        return this;
    }

    private void reset(String name) {
        this.name = name;
        requested = false;
        skin = null;
        initial.setText(Component.literal(initial(name))).setVisible(true);
    }

    public String getName() {
        return name;
    }

    public UUID getPlayerId() {
        return profile == null ? null : profile.getId();
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public boolean getHatLayer() {
        return hatLayer;
    }

    public Avatar setHatLayer(boolean hatLayer) {
        this.hatLayer = hatLayer;
        return this;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public int getBackgroundColor() {
        return getThemeItem(io.github.luyingyilong.ore.theme.OreTheme.AVATAR_COLOR);
    }

    public Avatar setBackgroundColor(int color) {
        backgroundColor = color;
        setThemeProperty(io.github.luyingyilong.ore.theme.OreTheme.AVATAR_COLOR, color);
        return this;
    }

    AvatarSkins.Skin skin() {
        if (!requested && profile != null) {
            requested = true;
            try {
                skin = skins.apply(profile);
            } catch (RuntimeException ignored) { /* A failed provider never blocks rendering initials. */ }
        }
        return skin;
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            fitChild(initial, new Rect(0, 0, bounds.width(), bounds.height()), font);
        }
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        var current = skin();
        var texture = current == null ? null : current.texture();
        initial.setVisible(texture == null);
        Rect area = getScreenBounds(screenX, screenY);
        var graphics = context.getGraphics();
        int side = Math.min(area.width(), area.height());
        int x = area.x() + (area.width() - side) / 2, y = area.y() + (area.height() - side) / 2;
        if (texture == null) {
            graphics.fill(x, y, x + side, y + side, getBackgroundColor());
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
