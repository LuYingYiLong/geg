package io.github.luyingyilong.geg.client.ui.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Font families scoped to Ore text, resolved by Minecraft's normal font pipeline. */
public final class OreFonts {
    /** Vanilla bitmap glyphs, then bundled Noto Sans, then Minecraft's final fallback. */
    public static final ResourceLocation BODY = ResourceLocation.fromNamespaceAndPath("geg", "ore");
    public static final ResourceLocation HERO = ResourceLocation.fromNamespaceAndPath("geg", "ore_hero");
    private static int revision;

    private OreFonts() { }

    public static Component text(Component text) { return text(text, false); }

    public static Component text(Component text, boolean hero) {
        // A child with its own font keeps that font through Component style inheritance.
        if (!text.getStyle().getFont().equals(Style.DEFAULT_FONT)) return text;
        return text.copy().withStyle(style -> style.withFont(hero ? HERO : BODY));
    }

    public static int revision() { return revision; }
    public static void resourcesReloaded() { revision++; }
}
