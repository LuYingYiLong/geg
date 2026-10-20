package io.github.luyingyilong.ore;

import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native textures preserve source alpha without a separate opt-in. */
public final class OreTextureResourceTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("ore.resourceRoot"), "assets/ore");
        var theme = JsonParser.parseString(Files.readString(root.resolve("themes/ore.json"))).getAsJsonObject();
        for (String key : new String[]{"Navbar:panel", "Scroll:vertical_grabber", "Scroll:horizontal_grabber"}) {
            var texture = theme.getAsJsonObject(key);
            Path path = root.resolve("textures/" + texture.get("location").getAsString().substring("ore:".length()) + ".png");
            var image = ImageIO.read(path.toFile());
            if (image.getWidth() != texture.get("imageWidth").getAsInt()
                    || image.getHeight() != texture.get("imageHeight").getAsInt())
                throw new AssertionError("Theme texture dimensions differ from PNG: " + key);
            boolean partialAlpha = false;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                partialAlpha |= alpha > 0 && alpha < 255;
            }
            if (!texture.get("style").getAsString().equals("texture")) throw new AssertionError("Expected native texture style");
            if(texture.has("nonOpaque")) throw new AssertionError("Legacy MUI alpha flag must not return");
        }
    }
}
