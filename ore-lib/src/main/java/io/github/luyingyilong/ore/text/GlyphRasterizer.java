package io.github.luyingyilong.ore.text;

import java.awt.*;
import java.awt.image.BufferedImage;

/** CPU rasterization contains no black matte and no alpha threshold. */
public final class GlyphRasterizer {
    public record Image(BufferedImage pixels, float left, float top, float width, float height, boolean smooth) { }
    private GlyphRasterizer() { }
    public static Image rasterize(TextLayout.Glyph glyph, float density) {
        if (glyph.bitmap() != null) {
            var bitmap = glyph.bitmap();
            if (bitmap.image() == null) return null;
            float unit = bitmap.height() * glyph.bitmapScale() / bitmap.image().getHeight();
            return new Image(bitmap.image(), 0, -bitmap.ascent() * glyph.bitmapScale(),
                    bitmap.image().getWidth() * unit, bitmap.height() * glyph.bitmapScale(), bitmap.colored());
        }
        // Bound the allocation before creating an image, including unusually large theme sizes.
        // Retain GUI dimensions by reducing only raster density, never the layout's font size.
        float pixels = Math.min(1020, glyph.font().getSize2D() * density);
        density = pixels / glyph.font().getSize2D();
        Font font = glyph.font().deriveFont(pixels);
        var vector = font.createGlyphVector(TextBackend.CONTEXT, new int[]{glyph.glyphId()});
        Rectangle bounds = vector.getPixelBounds(TextBackend.CONTEXT, 0, 0);
        while (bounds.width > 1020 || bounds.height > 1020) {
            density *= .5F;
            font = glyph.font().deriveFont(glyph.font().getSize2D() * density);
            vector = font.createGlyphVector(TextBackend.CONTEXT, new int[]{glyph.glyphId()});
            bounds = vector.getPixelBounds(TextBackend.CONTEXT, 0, 0);
        }
        if (bounds.width <= 0 || bounds.height <= 0) return null;
        BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setColor(Color.WHITE);
            graphics.drawGlyphVector(vector, -bounds.x, -bounds.y);
        } finally { graphics.dispose(); }
        return new Image(image, bounds.x / density, bounds.y / density, bounds.width / density, bounds.height / density, true);
    }
}
