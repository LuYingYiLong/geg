package io.github.luyingyilong.ore.theme;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 * Correct the duplicated horizontal side bearing in Minecraft 1.20.1's TTF glyphs.
 */
public final class OreTrueTypeGlyphProvider extends TrueTypeGlyphProvider {
    private final STBTTFontinfo fontInfo;
    private final float pointScale;
    private final float sampling;

    public OreTrueTypeGlyphProvider(ByteBuffer memory, STBTTFontinfo font, float size, float oversample,
                                    float shiftX, float shiftY, String skip) {
        super(memory, font, size, oversample, shiftX, shiftY, skip);
        fontInfo = font;
        pointScale = STBTruetype.stbtt_ScaleForPixelHeight(font, size * oversample);
        sampling = oversample;
    }

    /**
     * Other font namespaces continue to use the original provider without modification.
     */
    public static TrueTypeGlyphProvider create(ResourceLocation file, ByteBuffer memory, STBTTFontinfo font,
                                               float size, float oversample, float shiftX, float shiftY, String skip) {
        return file.getNamespace().equals("ore")
                ? new OreTrueTypeGlyphProvider(memory, font, size, oversample, shiftX, shiftY, skip)
                : new TrueTypeGlyphProvider(memory, font, size, oversample, shiftX, shiftY, skip);
    }

    @Override
    public GlyphInfo getGlyph(int codepoint) {
        GlyphInfo glyph = super.getGlyph(codepoint);
        if (glyph == null) return null;
        float duplicatedBearing;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var advance = stack.mallocInt(1);
            var bearing = stack.mallocInt(1);
            STBTruetype.stbtt_GetCodepointHMetrics(fontInfo, codepoint, advance, bearing);
            duplicatedBearing = bearing.get(0) * pointScale / sampling;
        }
        return duplicatedBearing == 0 ? glyph : new CorrectedGlyph(glyph, duplicatedBearing);
    }

    private record CorrectedGlyph(GlyphInfo source, float bearing) implements GlyphInfo {
        @Override
        public float getAdvance() {
            return source.getAdvance();
        }

        @Override
        public float getBoldOffset() {
            return source.getBoldOffset();
        }

        @Override
        public float getShadowOffset() {
            return source.getShadowOffset();
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> baker) {
            // STB's bitmap xMin already includes the side bearing. Remove its second addition,
            // keeping the original advance, vertical metrics, rasterization and atlas upload.
            return source.bake(sheet -> baker.apply(new SheetGlyphInfo() {
                @Override
                public int getPixelWidth() {
                    return sheet.getPixelWidth();
                }

                @Override
                public int getPixelHeight() {
                    return sheet.getPixelHeight();
                }

                @Override
                public float getOversample() {
                    return sheet.getOversample();
                }

                @Override
                public boolean isColored() {
                    return sheet.isColored();
                }

                @Override
                public void upload(int x, int y) {
                    sheet.upload(x, y);
                }

                @Override
                public float getBearingX() {
                    return sheet.getBearingX() - bearing;
                }

                @Override
                public float getBearingY() {
                    return sheet.getBearingY();
                }

                @Override
                public float getLeft() {
                    return sheet.getLeft() - bearing;
                }

                @Override
                public float getRight() {
                    return sheet.getRight() - bearing;
                }

                @Override
                public float getUp() {
                    return sheet.getUp();
                }

                @Override
                public float getDown() {
                    return sheet.getDown();
                }
            }));
        }
    }
}
