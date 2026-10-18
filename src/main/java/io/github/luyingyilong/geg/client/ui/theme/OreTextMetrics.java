package io.github.luyingyilong.geg.client.ui.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Stable font-family metrics, never measured from the text currently being edited. */
public final class OreTextMetrics {
    private static final String OPTICAL_SAMPLE = "H0市国邮量";
    private static final String LINE_SAMPLE = OPTICAL_SAMPLE + "gjpqÅÉé";
    private static final Profile DEFAULT = new Profile(10, 1, 0, 8);
    private static volatile Families families = new Families(DEFAULT, DEFAULT);

    private OreTextMetrics() { }

    /** Coordinates are relative to Minecraft's drawString origin; its alphabetic baseline is y + 7. */
    public record Profile(int advance, int origin, float inkTop, float inkBottom) {
        public int lineHeight(float scale) { return Math.max(1, (int) Math.ceil(advance * scale)); }
        public float baseline(float scale) { return (origin + 7) * scale; }
        public float drawOrigin(float scale) { return origin * scale; }
        public float blockHeight(int lines, float scale) { return lines * lineHeight(scale); }
        public float opticalHeight(int lines, float scale) {
            return Math.max(0, lines - 1) * lineHeight(scale) + (inkBottom - inkTop) * scale;
        }
        public float centeredOrigin(float height, int lines, float scale, boolean optical) {
            return optical ? Math.round((height - opticalHeight(lines, scale)) / 2 - inkTop * scale)
                    : (float) Math.floor((height - blockHeight(lines, scale)) / 2) + drawOrigin(scale);
        }
    }

    private record Families(Profile body, Profile hero) { }
    private record Bounds(float top, float bottom, float opticalTop, float opticalBottom) {
        Bounds include(Bounds other) {
            return new Bounds(Math.min(top, other.top), Math.max(bottom, other.bottom),
                    Math.min(opticalTop, other.opticalTop), Math.max(opticalBottom, other.opticalBottom));
        }
        Profile profile() {
            int origin = Math.max(1, (int) Math.ceil(-top));
            return new Profile(Math.max(10, (int) Math.ceil(origin + bottom)), origin, opticalTop, opticalBottom);
        }
    }

    public static Profile body() { return families.body; }
    public static Profile family(boolean hero) { return hero ? families.hero : families.body; }

    /** Sample fixed reference glyphs once per reload, without baking a GPU atlas or replacing Font. */
    static void reload(ResourceManager resources) {
        // Include the vanilla bitmap capital box even when the sample TTF also contains Latin glyphs.
        Bounds bitmap = new Bounds(0, 8, 0, 7);
        try {
            Bounds fallback = bitmap.include(load(resources, "ore_fallback.json"));
            Bounds hero = fallback.include(load(resources, "ore_hero.json"));
            families = new Families(fallback.profile(), hero.profile());
        } catch (IOException | RuntimeException exception) {
            families = new Families(DEFAULT, DEFAULT);
            LogUtils.getLogger().warn("Could not measure Ore fonts; using stable default line metrics", exception);
        }
    }

    private static Bounds load(ResourceManager resources, String definition) throws IOException {
        JsonObject settings;
        try (var reader = new InputStreamReader(resources.open(ResourceLocation.fromNamespaceAndPath(
                "geg", "font/" + definition)), StandardCharsets.UTF_8)) {
            settings = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("providers")
                    .get(0).getAsJsonObject();
        }
        ResourceLocation file = ResourceLocation.parse(settings.get("file").getAsString()).withPrefix("font/");
        byte[] bytes;
        try (var input = resources.open(file)) { bytes = input.readAllBytes(); }
        ByteBuffer memory = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        STBTTFontinfo info = STBTTFontinfo.malloc();
        if (!STBTruetype.stbtt_InitFont(info, memory)) {
            info.free(); MemoryUtil.memFree(memory);
            throw new IOException("Invalid Ore font: " + file);
        }
        var shift = settings.getAsJsonArray("shift");
        try (var provider = new TrueTypeGlyphProvider(memory, info,
                settings.has("size") ? settings.get("size").getAsFloat() : 11,
                settings.has("oversample") ? settings.get("oversample").getAsFloat() : 1,
                shift == null ? 0 : shift.get(0).getAsFloat(), shift == null ? 0 : shift.get(1).getAsFloat(), "")) {
            return measure(provider);
        }
    }

    private static Bounds measure(TrueTypeGlyphProvider provider) {
        float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        LINE_SAMPLE.codePoints().forEach(codepoint -> {
            var glyph = provider.getGlyph(codepoint);
            if (glyph == null) return;
            glyph.bake(sheet -> {
                float top = sheet.getUp() - 3, bottom = sheet.getDown() - 3;
                bounds[0] = Math.min(bounds[0], top); bounds[1] = Math.max(bounds[1], bottom);
                if (OPTICAL_SAMPLE.indexOf(codepoint) >= 0) {
                    bounds[2] = Math.min(bounds[2], top); bounds[3] = Math.max(bounds[3], bottom);
                }
                return null;
            });
        });
        if (!Float.isFinite(bounds[0]) || !Float.isFinite(bounds[2]))
            throw new IllegalArgumentException("Ore font has no reference glyphs");
        return new Bounds(bounds[0], bounds[1], bounds[2], bounds[3]);
    }
}
