package io.github.luyingyilong.geg.client.ui;

import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

/** MUI needs nonOpaque for partial-alpha pixels, including nine-slice edges. */
public final class OreTextureResourceTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("src/main/resources/assets/geg");
        var theme = JsonParser.parseString(Files.readString(root.resolve("themes/ore.json"))).getAsJsonObject();
        for (String key : new String[]{"panel:headerPanel", "panel:VGrabber", "panel:HGrabber"}) {
            var texture = theme.getAsJsonObject(key).getAsJsonObject("background");
            Path path = root.resolve("textures/" + texture.get("location").getAsString().substring("geg:".length()) + ".png");
            var image = ImageIO.read(path.toFile());
            if (image.getWidth() != texture.get("imageWidth").getAsInt()
                    || image.getHeight() != texture.get("imageHeight").getAsInt())
                throw new AssertionError("Theme texture dimensions differ from PNG: " + key);
            boolean partialAlpha = false;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                partialAlpha |= alpha > 0 && alpha < 255;
            }
            if (partialAlpha && (!texture.has("nonOpaque") || !texture.get("nonOpaque").getAsBoolean()))
                throw new AssertionError("Translucent texture must enable nonOpaque: " + key);
        }
    }
}
