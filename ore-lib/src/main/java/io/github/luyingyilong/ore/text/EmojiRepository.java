package io.github.luyingyilong.ore.text;

import com.google.gson.JsonParser;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Versioned offline artwork; a resource pack can replace individual text/emoji/*.png files. */
final class EmojiRepository {
    private final ResourceManager resources;
    private Map<String, String> sequences;
    private final Map<String, byte[]> archive = new HashMap<>();
    private final Map<String, FontRepository.BitmapGlyph> decoded = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, FontRepository.BitmapGlyph> entry) { return size() > 256; }
    };
    EmojiRepository(ResourceManager resources) { this.resources = resources; }
    private void load() {
        if (sequences != null) return;
        sequences = new HashMap<>();
        if (resources == null) return;
        try (var reader = resources.openAsReader(ResourceLocation.parse("ore:text/emoji.json"))) {
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("sequences").entrySet()
                    .forEach(e -> sequences.put(e.getKey(), e.getValue().getAsString()));
            try (var zip = new ZipInputStream(resources.open(ResourceLocation.parse("ore:text/emoji.zip")))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) if (!entry.isDirectory()) archive.put(entry.getName(), zip.readAllBytes());
            }
        } catch (IOException | RuntimeException error) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Cannot load Ore emoji resources", error);
        }
    }
    String key(String cluster) {
        if (cluster.indexOf('\uFE0E') >= 0) return null;
        boolean presentation = cluster.indexOf('\uFE0F') >= 0 || cluster.indexOf('\u200D') >= 0
                || cluster.codePoints().anyMatch(cp -> UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_PRESENTATION) || cp == 0x20E3);
        if (!presentation) return null;
        load();
        String key = cluster.codePoints().filter(cp -> cp != 0xFE0F)
                .mapToObj(cp -> String.format(Locale.ROOT, "%04x", cp)).collect(java.util.stream.Collectors.joining("_"));
        return sequences.containsKey(key) ? key : null;
    }
    FontRepository.BitmapGlyph get(String key) {
        if (key == null) return null;
        if (decoded.containsKey(key)) return decoded.get(key);
        String name = sequences.get(key);
        try {
            var override = resources.getResource(ResourceLocation.parse("ore:text/emoji/" + name));
            byte[] bytes = archive.get(name);
            try (InputStream stream = override.isPresent() ? override.get().open() : new ByteArrayInputStream(bytes)) {
                var image = ImageIO.read(stream);
                if (image == null) return null;
                var glyph = new FontRepository.BitmapGlyph(image, 9F * image.getWidth() / image.getHeight(), 7.5F, 9, true);
                decoded.put(key, glyph); return glyph;
            }
        } catch (IOException | RuntimeException error) { return null; }
    }
}
