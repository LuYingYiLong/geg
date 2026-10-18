package io.github.luyingyilong.ore.skin;

import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Converts provider PNGs, including HD/legacy skins, to an independent 64x64 Ore avatar texture. */
final class AvatarSkinImages {
    private AvatarSkinImages() { }

    static byte[] read(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 16 * 1024 * 1024) throw new IOException("Missing/oversized skin");
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw new IOException("Unreadable skin");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid skin image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!reader.getFormatName().equalsIgnoreCase("PNG") || width < 64 || width > 4096 || width % 64 != 0
                        || (height != width && height * 2 != width)) throw new IOException("Unsupported skin dimensions");
                boolean legacy = height != width;
                BufferedImage source = reader.read(0);
                BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try {
                    graphics.setComposite(AlphaComposite.Src);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    graphics.drawImage(source, 0, 0, 64, legacy ? 32 : 64, null);
                } finally { graphics.dispose(); source.flush(); }
                // Match vanilla's head opacity and legacy hat transparency rules; keep modern hat alpha.
                if (legacy) {
                    boolean opaque = true;
                    for (int y = 0; y < 32 && opaque; y++) {
                        for (int x = 32; x < 64; x++) {
                            if ((image.getRGB(x, y) >>> 24) < 128) { opaque = false; break; }
                        }
                    }
                    if (opaque) for (int y = 0; y < 32; y++) for (int x = 32; x < 64; x++) {
                        image.setRGB(x, y, image.getRGB(x, y) & 0x00FFFFFF);
                    }
                }
                for (int y = 0; y < 16; y++) for (int x = 0; x < 32; x++) {
                    image.setRGB(x, y, image.getRGB(x, y) | 0xFF000000);
                }
                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "PNG", output);
                    return output.toByteArray();
                } finally { image.flush(); }
            } finally { reader.dispose(); }
        }
    }
}
