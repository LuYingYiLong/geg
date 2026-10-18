package io.github.luyingyilong.geg.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;

/** Ore resources are available once; obsolete GEG aliases must not return in the merged artifact. */
public final class OreResourceNamespaceTest {
    public static void main(String[] args) throws Exception {
        for (String font : new String[]{"ore", "ore_hero", "ore_fallback"}) {
            json("/assets/ore/font/" + font + ".json");
            absent("/assets/geg/font/" + font + ".json");
        }
        check(json("/assets/ore/themes.json").get("ore").getAsString().equals("ore:ore"), "Default theme");
        absent("/assets/geg/themes.json");
        absent("/assets/geg/sounds.json");
        absent("/io/github/luyingyilong/geg/sound/GegSounds.class");
        for (var entry : json("/assets/ore/themes/ore.json").entrySet())
            check(!entry.getValue().toString().contains("geg:"), "Theme must only reference Ore assets");
        var sounds = json("/assets/ore/sounds.json");
        for (String cue : new String[]{"click", "button", "drawer_open", "drawer_close", "toast"}) {
            check(sounds.getAsJsonObject(cue).toString().contains("ore:" + cue), "Ore sound file");
            check(OreResourceNamespaceTest.class.getResource("/assets/ore/sounds/" + cue + ".ogg") != null, "UI sound exists");
            absent("/assets/geg/sounds/" + cue + ".ogg");
        }
        absent("/assets/geg/font/minecraft_ten.ttf");
        absent("/META-INF/services/io.github.luyingyilong.ore.OreClientExtension");
        check(java.util.ServiceLoader.load(io.github.luyingyilong.ore.OreClientExtension.class).findFirst().isEmpty(),
                "Ordinary GEG/Ore runtime must not load Inspector");
    }
    private static void absent(String path) { check(OreResourceNamespaceTest.class.getResource(path) == null, "Removed entry returned: " + path); }
    private static JsonObject json(String path) throws Exception {
        try (var input = OreResourceNamespaceTest.class.getResourceAsStream(path)) {
            check(input != null, "Missing resource: " + path);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
