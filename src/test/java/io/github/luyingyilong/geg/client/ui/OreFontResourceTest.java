package io.github.luyingyilong.geg.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Decode both bundled fonts with the same STB implementation used by vanilla 1.20.1. */
public final class OreFontResourceTest {
    private OreFontResourceTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("src/main/resources/assets/geg/font");
        var providers = JsonParser.parseString(Files.readString(root.resolve("ore.json")))
                .getAsJsonObject().getAsJsonArray("providers");
        check(providers.get(0).getAsJsonObject().get("id").getAsString().equals("minecraft:include/space"));
        check(providers.get(1).getAsJsonObject().get("id").getAsString().equals("minecraft:include/default"));
        check(providers.get(2).getAsJsonObject().get("id").getAsString().equals("geg:ore_fallback"));
        JsonObject body = definition(root, "ore_fallback.json", "minecraft:default", 12);
        JsonObject hero = definition(root, "ore_hero.json", "geg:ore_fallback", 11);
        try (TrueTypeGlyphProvider fallbackFont = load(root, body);
             TrueTypeGlyphProvider heroFont = load(root, hero)) {
            check(fallbackFont.getGlyph('A') != null && fallbackFont.getGlyph('市') != null);
            check(fallbackFont.getGlyph('金') != null && fallbackFont.getGlyph('组') != null);
            check(heroFont.getGlyph('A') != null && heroFont.getGlyph('0') != null);
            check(heroFont.getGlyph('市') == null);
            check(fallbackFont.getGlyph('A').getAdvance() > 0);
            fallbackFont.getGlyph('市').bake(sheet -> {
                check(sheet.getOversample() == 4);
                check(sheet.getPixelWidth() > 20 && sheet.getPixelHeight() > 20);
                return null;
            });
            // Custom size and shift can move glyphs beyond the old 10px line box.
            // Validate the actual glyph metrics without imposing the former 11px configuration.
            for (int codepoint : new int[]{'A', 'g', '市', '金', '组', '木'})
                fallbackFont.getGlyph(codepoint).bake(sheet -> {
                    check(Float.isFinite(sheet.getUp()) && Float.isFinite(sheet.getDown()));
                    check(sheet.getDown() > sheet.getUp());
                    check(sheet.getDown() - sheet.getUp() <= body.get("size").getAsFloat());
                    return null;
                });
        }
    }

    private static JsonObject definition(Path root, String name, String fallback, int size) throws Exception {
        var providers = JsonParser.parseString(Files.readString(root.resolve(name)))
                .getAsJsonObject().getAsJsonArray("providers");
        check(providers.get(1).getAsJsonObject().get("id").getAsString().equals(fallback));
        JsonObject provider = providers.get(0).getAsJsonObject();
        check(provider.get("type").getAsString().equals("ttf"));
        check(provider.get("size").getAsInt() == size && provider.get("oversample").getAsInt() == 4);
        return provider;
    }

    private static TrueTypeGlyphProvider load(Path root, JsonObject definition) throws Exception {
        String name = definition.get("file").getAsString().substring("geg:".length());
        check(name.equals(name.toLowerCase(java.util.Locale.ROOT)));
        byte[] bytes = Files.readAllBytes(root.resolve(name));
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        STBTTFontinfo info = STBTTFontinfo.malloc();
        if (!STBTruetype.stbtt_InitFont(info, buffer)) {
            info.free();
            MemoryUtil.memFree(buffer);
            throw new AssertionError("Invalid font: " + name);
        }
        var shift = definition.getAsJsonArray("shift");
        float shiftX = shift == null ? 0 : shift.get(0).getAsFloat();
        float shiftY = shift == null ? 0 : shift.get(1).getAsFloat();
        return new TrueTypeGlyphProvider(buffer, info, definition.get("size").getAsFloat(),
                definition.get("oversample").getAsFloat(), shiftX, shiftY, "");
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Ore font resources or fallback chain are invalid");
    }
}
