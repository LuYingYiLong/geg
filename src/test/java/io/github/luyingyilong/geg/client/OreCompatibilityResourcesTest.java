package io.github.luyingyilong.geg.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;

/** Check thin legacy aliases through the same merged resource classpath used by GEG. */
public final class OreCompatibilityResourcesTest {
    public static void main(String[] args) throws Exception {
        for (String font : new String[]{"ore", "ore_hero", "ore_fallback"}) {
            var providers = json("/assets/geg/font/" + font + ".json").getAsJsonArray("providers");
            check(providers.size() == 1, "Legacy font must be reference-only");
            check(providers.get(0).getAsJsonObject().get("id").getAsString().equals("ore:" + font), "Font alias target");
            json("/assets/ore/font/" + font + ".json");
        }
        check(json("/assets/geg/themes.json").get("geg_ore").getAsString().equals("ore:ore"), "Legacy theme alias");
        check(json("/assets/ore/themes.json").get("ore").getAsString().equals("ore:ore"), "Default theme");
        for (var entry : json("/assets/ore/themes/ore.json").entrySet())
            check(!entry.getValue().toString().contains("geg:"), "Theme must only reference Ore assets");
        var legacySounds = json("/assets/geg/sounds.json");
        for (String cue : new String[]{"click", "button", "drawer_open", "drawer_close", "toast"}) {
            check(legacySounds.getAsJsonObject(cue).toString().contains("ore:" + cue), "Legacy sound file alias");
            check(OreCompatibilityResourcesTest.class.getResource("/assets/ore/sounds/" + cue + ".ogg") != null, "UI sound exists");
            check(OreCompatibilityResourcesTest.class.getResource("/assets/geg/sounds/" + cue + ".ogg") == null, "Do not duplicate audio");
        }
        check(OreCompatibilityResourcesTest.class.getResource("/assets/geg/font/minecraft_ten.ttf") == null, "Do not duplicate fonts");
    }
    private static JsonObject json(String path) throws Exception {
        try (var input = OreCompatibilityResourcesTest.class.getResourceAsStream(path)) {
            check(input != null, "Missing resource: " + path);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
