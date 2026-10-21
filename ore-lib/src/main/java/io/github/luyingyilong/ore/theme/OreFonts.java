package io.github.luyingyilong.ore.theme;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Ore font resource identifiers and Component styling; TextLayout owns shaping and rendering.
 */
public final class OreFonts {
    /**
     * Vanilla bitmap glyphs, then bundled Noto Sans, then Minecraft's final fallback.
     */
    public static final ResourceLocation BODY = ResourceLocation.fromNamespaceAndPath("ore", "ore");
    public static final ResourceLocation HERO = ResourceLocation.fromNamespaceAndPath("ore", "ore_hero");
    private static int revision;

    private OreFonts() {
    }

    public static Component text(Component text) {
        return text(text, false);
    }

    public static Component text(Component text, ResourceLocation font) {
        if (!text.getStyle().getFont().equals(Style.DEFAULT_FONT)) return text;
        return Component.empty().withStyle(text.getStyle().withFont(font)).append(text);
    }

    public static Component text(Component text, boolean hero) {
        // A child with its own font keeps that font through Component style inheritance.
        if (!text.getStyle().getFont().equals(Style.DEFAULT_FONT)) return text;
        return text(text, hero ? io.github.luyingyilong.ore.OreClient.getOptions().heroFont() : io.github.luyingyilong.ore.OreClient.getOptions().bodyFont());
    }

    /**
     * Alpha is explicit ARGB, including 0; vanilla treats alpha 0..3 as opaque RGB colors.
     */
    public static float alpha(int argb) {
        return (argb >>> 24) / 255.0F;
    }

    public static int revision() {
        return revision;
    }

    public static void resourcesReloaded() {
        revision++;
    }

}
