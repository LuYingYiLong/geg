package io.github.luyingyilong.ore.theme;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Font families scoped to Ore text, resolved by Minecraft's normal font pipeline. */
public final class OreFonts {
    /** Vanilla bitmap glyphs, then bundled Noto Sans, then Minecraft's final fallback. */
    public static final ResourceLocation BODY = ResourceLocation.fromNamespaceAndPath("ore", "ore");
    public static final ResourceLocation HERO = ResourceLocation.fromNamespaceAndPath("ore", "ore_hero");
    private static int revision;

    private OreFonts() { }

    public static Component text(Component text) { return text(text, false); }

    public static Component text(Component text, boolean hero) {
        // A child with its own font keeps that font through Component style inheritance.
        if (!text.getStyle().getFont().equals(Style.DEFAULT_FONT)) return text;
        return text.copy().withStyle(style -> style.withFont(hero ? io.github.luyingyilong.ore.OreClient.getOptions().heroFont() : io.github.luyingyilong.ore.OreClient.getOptions().bodyFont()));
    }

    /** Alpha is explicit ARGB, including 0; vanilla treats alpha 0..3 as opaque RGB colors. */
    public static float alpha(int argb) { return (argb >>> 24) / 255.0F; }

    public static void draw(GuiGraphics graphics, Font font, FormattedCharSequence text,
                            int x, int y, int color, boolean shadow) {
        float opacity = alpha(color);
        if (opacity == 0.0F) return;
        if (opacity == 1.0F) { graphics.drawString(font, text, x, y, color, shadow); return; }
        draw(color, new AlphaCanvas() {
            public float[] color() { return RenderSystem.getShaderColor(); }
            public void color(float r, float g, float b, float a) { graphics.setColor(r, g, b, a); }
            public void draw(int opaque) { graphics.drawString(font, text, x, y, opaque, shadow); }
            public void flush() { graphics.flush(); }
        });
    }

    interface AlphaCanvas {
        float[] color();
        void color(float r, float g, float b, float a);
        void draw(int color);
        void flush();
    }

    static void draw(int color, AlphaCanvas canvas) {
        float opacity = alpha(color);
        if (opacity == 0.0F) return;
        if (opacity == 1.0F) { canvas.draw(color); return; }
        canvas.flush();
        float[] previous = canvas.color().clone();
        try {
            // Font.adjustColor interprets alpha 0..3 as RGB; keep vertices opaque and modulate instead.
            canvas.color(previous[0], previous[1], previous[2], previous[3] * opacity);
            canvas.draw(color | 0xFF000000);
        } finally {
            try { canvas.flush(); }
            finally { canvas.color(previous[0], previous[1], previous[2], previous[3]); }
        }
    }

    public static int revision() { return revision; }
    public static void resourcesReloaded() { revision++; }
}
