package io.github.luyingyilong.ore.support;
import com.google.gson.JsonParser;
import io.github.luyingyilong.ore.theme.ThemeDB;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
/** Shared test-only resource reload fixture. */
public final class ThemeTestResources {
    private ThemeTestResources() { }
    public static ThemeDB.Resources resources(Map<String, Integer> overrides) {
        return new ThemeDB.Resources() {
            @Override public Reader open(ResourceLocation location) throws IOException {
                var stream = ThemeTestResources.class.getResourceAsStream("/assets/" + location.getNamespace() + "/" + location.getPath());
                if (stream == null) throw new FileNotFoundException(location.toString());
                Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                if (!location.getNamespace().equals("ore") || !location.getPath().equals("themes/ore.json")) return reader;
                try (reader) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    overrides.forEach(json::addProperty);
                    return new StringReader(json.toString());
                }
            }
            @Override public int[] imageSize(ResourceLocation location) throws IOException {
                try (var stream = ThemeTestResources.class.getResourceAsStream("/assets/" + location.getNamespace() + "/" + location.getPath())) {
                    if (stream == null) throw new FileNotFoundException(location.toString());
                    var image = javax.imageio.ImageIO.read(stream);
                    return new int[]{image.getWidth(), image.getHeight()};
                }
            }
        };
    }
}
