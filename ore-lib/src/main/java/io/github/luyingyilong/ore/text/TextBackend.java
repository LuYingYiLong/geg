package io.github.luyingyilong.ore.text;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;

/** CPU shaping and GPU-image providers must describe the same glyph coordinates. */
public interface TextBackend {
    FontRenderContext CONTEXT = new FontRenderContext(new AffineTransform(), true, true);
    record GpuGlyph(int texture, float left, float top, float width, float height,
                    float u0, float v0, float u1, float v1) { }
    final class Unavailable extends RuntimeException {
        public Unavailable(String message, Throwable cause) { super(message, cause); }
    }
    String getId();
    /** Public backend cache, straight white RGB plus gray alpha; never owned or deleted by Ore. */
    default GpuGlyph getGlyphImage(TextLayout.Glyph glyph, float density) { return null; }
    GlyphVector shape(Font font, char[] text, int start, int end, boolean rtl);
    default FontRenderContext getFontRenderContext() { return CONTEXT; }
    static TextBackend builtin() { return Builtin.INSTANCE; }
    enum Builtin implements TextBackend {
        INSTANCE;
        public String getId() { return "builtin-java2d"; }
        public GlyphVector shape(Font font, char[] text, int start, int end, boolean rtl) {
            return font.layoutGlyphVector(CONTEXT, text, start, end, rtl ? Font.LAYOUT_RIGHT_TO_LEFT : Font.LAYOUT_LEFT_TO_RIGHT);
        }
    }
}
