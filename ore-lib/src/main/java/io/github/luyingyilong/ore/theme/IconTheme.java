package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.render.TextureRegion;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Shared symbols, not a Control type. Stateful textures belong to their consuming controls. */
public final class IconTheme {
    public static final String TYPE = "Icons";
    public static final ThemeKey<TextureRegion> SMALL_CLOSE = texture(TYPE, "small_close", "small_close", 8, 8);
    public static final ThemeKey<TextureRegion> CLOSE = texture(TYPE, "close", "close", 7, 7);
    public static final ThemeKey<TextureRegion> CHECKED = texture(TYPE, "checked", "checked", 8, 8);
    public static final ThemeKey<TextureRegion> ARROW_DOWN = texture(TYPE, "arrow_down", "arrow_down", 7, 7);
    public static final ThemeKey<TextureRegion> ARROW_DOWN_LIGHT = texture(TYPE, "arrow_down_light", "arrow_down_light", 7, 7);
    public static final ThemeKey<TextureRegion> ARROW_RIGHT_LIGHT = texture(TYPE, "arrow_right_light", "arrow_right_light", 7, 7);
    public static final ThemeKey<TextureRegion> ARROW_LEFT = texture(TYPE, "arrow_left", "arrow_left", 7, 7);
    public static final ThemeKey<TextureRegion> FILTER = texture(TYPE, "filter", "filter", 16, 16);
    private static final List<ThemeKey<TextureRegion>> KEYS = List.of(SMALL_CLOSE, CLOSE, CHECKED,
            ARROW_DOWN, ARROW_DOWN_LIGHT, ARROW_RIGHT_LIGHT, ARROW_LEFT, FILTER);

    private IconTheme() { }

    public static void registerTheme(ThemeDB db) {
        db.registerType(TYPE, null);
        KEYS.forEach(db::register);
    }

    public static ThemeKey<TextureRegion> texture(String type, String name, String file, int width, int height) {
        return ThemeKey.of(type, name, TextureRegion.class, ThemeKey.Kind.TEXTURE,
                TextureRegion.full(ResourceLocation.fromNamespaceAndPath("ore", "textures/icons/" + file + ".png"), width, height), true);
    }

    /** The consuming control owns the item; a shared symbol is its resource fallback. */
    public static ThemeKey<TextureRegion> item(String type, String name, ThemeKey<TextureRegion> symbol) {
        return ThemeKey.of(type, name, TextureRegion.class, ThemeKey.Kind.TEXTURE, symbol.defaultValue(), true);
    }
}
