package io.github.luyingyilong.ore;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.awt.Font;
/** Real font decoding and fallback order, with visual calibration kept out of provider JSON. */
public final class OreFontResourceTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("ore.resourceRoot"), "assets/ore/font");
        var providers = JsonParser.parseString(Files.readString(root.resolve("ore.json"))).getAsJsonObject().getAsJsonArray("providers");
        check(providers.get(0).getAsJsonObject().get("id").getAsString().equals("minecraft:include/space"));
        check(providers.get(1).getAsJsonObject().get("id").getAsString().equals("minecraft:include/default"));
        check(providers.get(2).getAsJsonObject().get("id").getAsString().equals("ore:ore_fallback"));
        for (String file : new String[]{"ore_fallback.json", "ore_hero.json"}) {
            var items = JsonParser.parseString(Files.readString(root.resolve(file))).getAsJsonObject().getAsJsonArray("providers");
            var provider = items.get(0).getAsJsonObject();
            check(!provider.has("shift") && !provider.has("oversample") && !provider.has("size"));
            var fontFile = provider.get("file").getAsString().substring(4);
            check(fontFile.equals(fontFile.toLowerCase(java.util.Locale.ROOT)));
            Font font;
            try (var stream = Files.newInputStream(root.resolve(fontFile))) { font = Font.createFont(Font.TRUETYPE_FONT, stream); }
            check(font.canDisplay('A') && font.canDisplay('0'));
            check(font.canDisplay('市') == file.equals("ore_fallback.json"));
            check(items.get(1).getAsJsonObject().get("id").getAsString().equals(file.equals("ore_hero.json") ? "ore:ore_fallback" : "minecraft:default"));
        }
    }
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Font resource contract"); }
}
