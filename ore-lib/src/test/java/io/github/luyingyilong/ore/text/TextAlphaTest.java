package io.github.luyingyilong.ore.text;
import java.awt.Font;
import net.minecraft.network.chat.Style;
public final class TextAlphaTest {
    public static void main(String[] args) throws Exception {
        for (int coverage = 0; coverage <= 255; coverage++) for (float alpha : new float[]{0, .25F, .5F, 1}) {
            int p = TextAlpha.premultiply(coverage << 24 | 0xFFFFFF);
            check((p & 255) == coverage, "white premultiplication");
            float a = TextAlpha.coverage(coverage / 255F, alpha, .5F);
            check(a == coverage / 255F * alpha * .5F, "alpha once");
            check(Math.abs(TextAlpha.over(a, a, 1) - 1) < .00001F, "white over white cannot darken");
            if (coverage > 0 && alpha > 0) check(a > 0, "no 0.1 alpha cutout");
        }
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 17);
        var vector = font.createGlyphVector(TextBackend.CONTEXT, "S");
        var image = GlyphRasterizer.rasterize(new TextLayout.Glyph(null, font, vector.getGlyphCode(0), null, 0, 0, 1, 0, Style.EMPTY), 1).pixels();
        boolean low = false;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getRGB(x, y), a = pixel >>> 24;
            if (a > 0 && a < 255) check((pixel & 0xFFFFFF) == 0xFFFFFF, "no black matte");
            if (a > 0 && a < 26) low = true;
        }
        check(low, "gray coverage retained below vanilla cutoff");
        Font large = font.deriveFont(8192F);
        var largeImage = GlyphRasterizer.rasterize(new TextLayout.Glyph(null, large, vector.getGlyphCode(0), null, 0, 0, 1, 0, Style.EMPTY), 4);
        check(largeImage.pixels().getWidth() <= 1020 && largeImage.pixels().getHeight() <= 1020,
                "large font raster allocations are bounded before allocation");
        check(largeImage.width() > 1020, "raster limit preserves the layout's GUI dimensions");
        String shader = java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("ore.resourceRoot"), "assets/ore/shaders/core/text.fsh"));
        check(!shader.contains("discard"), "own shader must not discard antialias edges");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
