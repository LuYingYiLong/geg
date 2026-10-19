package io.github.luyingyilong.ore.theme;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.file.Files;
import java.nio.file.Path;

/** Real STB glyph boxes: punctuation stays inside its advance without moving caret geometry. */
public final class OreGlyphBearingTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("ore.resourceRoot"), "assets/ore/font");
        for (String definition : new String[]{"ore_fallback.json", "ore_hero.json"}) {
            var settings = JsonParser.parseString(Files.readString(root.resolve(definition)))
                    .getAsJsonObject().getAsJsonArray("providers").get(0).getAsJsonObject();
            String file = settings.get("file").getAsString().substring("ore:".length());
            for (float oversample : new float[]{1, 4}) for (float shiftX : new float[]{0, 0.25F}) {
                float size = settings.get("size").getAsFloat();
                try (Sample fixed = load(root, file, "ore", size, oversample, shiftX, "");
                     Sample vanilla = load(root, file, "other_mod", size, oversample, shiftX, "")) {
                    check(fixed.provider instanceof OreTrueTypeGlyphProvider, "Ore uses corrected TTF glyphs");
                    check(vanilla.provider.getClass() == TrueTypeGlyphProvider.class, "Other mods' fonts are unchanged");
                    for (int cp : "（）()【】「」，。；：！？中文ABC012gj".codePoints().toArray()) {
                        GlyphInfo correctedGlyph = fixed.provider.getGlyph(cp), originalGlyph = vanilla.provider.getGlyph(cp);
                        if (originalGlyph == null) { check(correctedGlyph == null, "Missing glyphs still fall back"); continue; }
                        near(correctedGlyph.getAdvance(), originalGlyph.getAdvance());
                        near(correctedGlyph.getBoldOffset(), originalGlyph.getBoldOffset());
                        near(correctedGlyph.getShadowOffset(), originalGlyph.getShadowOffset());
                        SheetGlyphInfo corrected = sheet(correctedGlyph), original = sheet(originalGlyph);
                        if (original == null) { check(corrected == null, "Whitespace stays whitespace"); continue; }
                        near(corrected.getUp(), original.getUp()); near(corrected.getDown(), original.getDown());
                        check(corrected.getPixelWidth() == original.getPixelWidth()
                                && corrected.getPixelHeight() == original.getPixelHeight(), "Raster size is unchanged");
                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            var x0 = stack.mallocInt(1); var y0 = stack.mallocInt(1);
                            var x1 = stack.mallocInt(1); var y1 = stack.mallocInt(1);
                            float scale = STBTruetype.stbtt_ScaleForPixelHeight(fixed.info, size * oversample);
                            STBTruetype.stbtt_GetCodepointBitmapBoxSubpixel(fixed.info, cp, scale, scale,
                                    shiftX * oversample, 0, x0, y0, x1, y1);
                            near(corrected.getLeft(), x0.get(0) / oversample + shiftX);
                            near(corrected.getRight() - corrected.getLeft(), original.getRight() - original.getLeft());
                        }
                        if (cp == '（' && oversample == 4 && shiftX == 0) {
                            check(original.getLeft() - corrected.getLeft() > 3, "Opening bracket exposes the large duplicated bearing");
                            check(corrected.getRight() <= correctedGlyph.getAdvance() + 0.25F, "Opening bracket no longer spills into the following character");
                            System.out.printf("ORE_BRACKET_BEARING original=%.3f corrected=%.3f advance=%.3f%n",
                                    original.getLeft(), corrected.getLeft(), correctedGlyph.getAdvance());
                        }
                    }
                    near(fixed.provider.getGlyph(0x20).getAdvance(), vanilla.provider.getGlyph(0x20).getAdvance());
                    check(fixed.provider.getGlyph(0x10FFFF) == null, "Unsupported codepoints remain absent");
                }
            }
            try (Sample skipped = load(root, file, "ore", 12, 4, 0, "（")) {
                check(skipped.provider.getGlyph('（') == null, "Resource-defined skip is honored");
            }
        }
        System.out.println("ORE_GLYPH_BEARING_PASS");
    }

    private static SheetGlyphInfo sheet(GlyphInfo glyph) {
        SheetGlyphInfo[] result = {null};
        glyph.bake(value -> { result[0] = value; return null; });
        return result[0];
    }

    private static Sample load(Path root, String file, String namespace, float size, float oversample,
                               float shiftX, String skip) throws Exception {
        byte[] bytes = Files.readAllBytes(root.resolve(file));
        var memory = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        STBTTFontinfo info = STBTTFontinfo.malloc();
        if (!STBTruetype.stbtt_InitFont(info, memory)) {
            info.free(); MemoryUtil.memFree(memory); throw new AssertionError("Invalid font");
        }
        return new Sample(OreTrueTypeGlyphProvider.create(ResourceLocation.fromNamespaceAndPath(namespace, file),
                memory, info, size, oversample, shiftX, 0, skip), info);
    }

    private record Sample(TrueTypeGlyphProvider provider, STBTTFontinfo info) implements AutoCloseable {
        @Override public void close() { provider.close(); }
    }

    private static void near(float actual, float expected) {
        check(Math.abs(actual - expected) < 0.001F, actual + " != " + expected);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
