package io.github.luyingyilong.ore.text;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

/** Immutable font generations. CPU resources are independent of windows and GL contexts. */
public final class FontRepository {
    public record BitmapGlyph(BufferedImage image, float advance, float ascent, float height, boolean colored) {
        public BitmapGlyph(BufferedImage image, float advance, float ascent, float height) { this(image, advance, ascent, height, false); }
    }
    public record FaceMetrics(float unitsPerEm, float ascentEm, float descentEm, float leadingEm, float capHeightEm) { }
    public record Metrics(float ascent, float descent, float opticalTop, float opticalBottom, float lineHeight) { }
    public static final class Face {
        private final ResourceLocation id;
        private final Font font;
        private final Map<Integer, BitmapGlyph> bitmap;
        private final Set<Integer> skipped;
        private final float ascent, descent, inkTop, inkBottom;
        private final FaceMetrics declaredMetrics;

        Face(ResourceLocation id, Font font, Map<Integer, BitmapGlyph> bitmap, Set<Integer> skipped,
             float ascent, float descent, float inkTop, float inkBottom) {
            this(id, font, bitmap, skipped, ascent, descent, inkTop, inkBottom,
                    new FaceMetrics(8, ascent / 8, descent / 8, 0, (inkBottom - inkTop) / 8));
        }
        Face(ResourceLocation id, Font font, Map<Integer, BitmapGlyph> bitmap, Set<Integer> skipped,
             float ascent, float descent, float inkTop, float inkBottom, FaceMetrics declaredMetrics) {
            this.declaredMetrics = declaredMetrics;
            this.id = id; this.font = font; this.bitmap = Map.copyOf(bitmap); this.skipped = Set.copyOf(skipped);
            this.ascent = ascent; this.descent = descent; this.inkTop = inkTop; this.inkBottom = inkBottom;
        }
        public ResourceLocation getId() { return id; }
        public FaceMetrics getDeclaredMetrics() { return declaredMetrics; }
        public boolean isOutline() { return font != null; }
        public boolean supports(String cluster) {
            return cluster.codePoints().allMatch(cp -> !skipped.contains(cp) &&
                    (cp == 0x200D || cp >= 0xFE00 && cp <= 0xFE0F || (font != null ? font.canDisplay(cp) : bitmap.containsKey(cp))));
        }
        public float getScale(float size, FontMetricsPolicy policy) {
            float natural = font == null ? size / 8 : size;
            float match = policy.matchMode() == FontMetricsPolicy.MatchMode.AUTO && font != null
                    ? 0.875F / Math.max(0.01F, inkBottom - inkTop) : 1;
            return natural * match * policy.getAdjustment(id).scale();
        }
        public float getBaselineShift(float size, FontMetricsPolicy policy) {
            float shift = policy.matchMode() == FontMetricsPolicy.MatchMode.AUTO && font != null
                    ? -size * 0.4375F - (inkTop + inkBottom) * getScale(size, policy) / 2 : 0;
            return shift + policy.getAdjustment(id).baselineShift() * size;
        }
        public Font getFont(float size, FontMetricsPolicy policy, boolean bold, boolean italic) {
            return font.deriveFont((bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0), getScale(size, policy))
                    .deriveFont(Map.of(java.awt.font.TextAttribute.KERNING, java.awt.font.TextAttribute.KERNING_ON,
                            java.awt.font.TextAttribute.LIGATURES, java.awt.font.TextAttribute.LIGATURES_ON));
        }
        public BitmapGlyph getBitmap(int codepoint) { return bitmap.get(codepoint); }
        Metrics metrics(float size, FontMetricsPolicy policy) {
            float s = getScale(size, policy), shift = getBaselineShift(size, policy);
            float leading = font == null ? 0 : declaredMetrics.leadingEm() * s / 2;
            return new Metrics(Math.max(0, ascent * s - shift) + leading, Math.max(0, descent * s + shift) + leading,
                    inkTop * s + shift, inkBottom * s + shift, 0);
        }
    }

    private final ResourceManager resources;
    private final Map<ResourceLocation, List<Face>> families = new HashMap<>();
    private final Map<ResourceLocation, Font> outlines = new HashMap<>();
    private final Set<String> reported = new HashSet<>();
    private final Face missing;
    private final EmojiRepository emojis;
    private final Face emojiFace = new Face(ResourceLocation.parse("ore:noto_emoji"), null, Map.of(), Set.of(), 7.5F, 1.5F, -7.5F, 1.5F);
    private final long generation;
    public FontRepository(ResourceManager resources, long generation) {
        this.resources = resources; this.generation = generation; emojis = new EmojiRepository(resources);
        BufferedImage image = new BufferedImage(5, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 5; x++)
            if (x == 0 || x == 4 || y == 0 || y == 7) image.setRGB(x, y, -1);
        missing = new Face(ResourceLocation.parse("ore:missing"), null,
                Map.of(0xFFFD, new BitmapGlyph(image, 6, 7, 8)), Set.of(), 8, 2, -7, 1);
    }
    public long getGeneration() { return generation; }
    public boolean isLoaded() { return resources != null; }
    public List<Face> getFamily(ResourceLocation id) { return loadFamily(id, new LinkedHashSet<>()); }
    public Face resolve(ResourceLocation id, String cluster) {
        if (emojis.key(cluster) != null) return emojiFace;
        for (Face face : getFamily(id)) if (face.supports(cluster)) return face;
        return missing;
    }
    boolean isEmoji(Face face) { return face == emojiFace; }
    BitmapGlyph getEmoji(String cluster) { return emojis.get(emojis.key(cluster)); }
    public Metrics getMetrics(ResourceLocation id, float size, FontMetricsPolicy policy) {
        float above = 8 * size / 8, below = 2 * size / 8;
        List<Face> family = getFamily(id);
        for (Face face : family) {
            Metrics m = face.metrics(size, policy);
            above = Math.max(above, m.ascent); below = Math.max(below, m.descent);
        }
        // Optical region is family-stable, independent of which characters are currently typed.
        float top = -size * 0.875F, bottom = 0;
        return new Metrics(above, below, top, bottom, (float)Math.ceil((above + below) * policy.lineSpacing()));
    }
    private List<Face> loadFamily(ResourceLocation id, Set<ResourceLocation> pending) {
        List<Face> cached = families.get(id);
        if (cached != null) return cached;
        if (resources == null || !pending.add(id)) return List.of();
        List<Face> result = new ArrayList<>();
        var file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "font/" + id.getPath() + ".json");
        var stack = new ArrayList<>(resources.getResourceStack(file)); Collections.reverse(stack);
        for (var resource : stack) try (Reader reader = resource.openAsReader()) {
            var providers = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("providers");
            for (JsonElement value : providers) try {
                JsonObject provider = value.getAsJsonObject();
                switch (provider.get("type").getAsString().replace("minecraft:", "")) {
                    case "reference" -> result.addAll(loadFamily(ResourceLocation.parse(provider.get("id").getAsString()), pending));
                    case "ttf" -> result.add(outline(provider));
                    case "bitmap" -> result.add(bitmap(provider));
                    case "space" -> result.add(spaces(id, provider));
                    case "unihex" -> result.add(unihex(provider));
                    default -> report(file + ": unsupported provider " + provider.get("type"));
                }
            } catch (Exception error) { report(file + ": " + error); }
        } catch (IOException | RuntimeException error) { report(file + ": " + error); }
        pending.remove(id);
        List<Face> immutable = List.copyOf(new LinkedHashSet<>(result)); families.put(id, immutable);
        return immutable;
    }
    private Face outline(JsonObject json) throws Exception {
        ResourceLocation id = ResourceLocation.parse(json.get("file").getAsString());
        Font font = outlines.get(id); byte[] bytes;
        try (InputStream stream = resources.open(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "font/" + id.getPath()))) { bytes = stream.readAllBytes(); }
        if (font == null) { font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes)).deriveFont(1.0F); outlines.put(id, font); }
        FaceMetrics metrics = sfntMetrics(bytes, font);
        String reference = font.canDisplay('国') ? "国" : font.canDisplay('H') ? "H" : "0";
        var bounds = font.createGlyphVector(TextBackend.CONTEXT, reference).getVisualBounds();
        Set<Integer> skipped = new HashSet<>();
        if (json.has("skip")) {
            JsonElement skip = json.get("skip");
            if (skip.isJsonArray()) {
                for (JsonElement entry : skip.getAsJsonArray()) entry.getAsString().codePoints().forEach(skipped::add);
            } else skip.getAsString().codePoints().forEach(skipped::add);
        }
        return new Face(id, font, Map.of(), skipped, metrics.ascentEm(), metrics.descentEm(),
                (float)bounds.getMinY(), bounds.isEmpty() ? 0.125F : (float)bounds.getMaxY(), metrics);
    }
    private static FaceMetrics sfntMetrics(byte[] bytes, Font font) {
        var fallback = font.getLineMetrics("Hg", TextBackend.CONTEXT);
        try {
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            int count = Short.toUnsignedInt(b.getShort(4)), head = -1, os2 = -1;
            for (int i = 0; i < count; i++) { int at = 12 + i * 16, tag = b.getInt(at), offset = b.getInt(at + 8);
                if (tag == 0x68656164) head = offset; if (tag == 0x4F532F32) os2 = offset;
            }
            if (head >= 0 && os2 >= 0) {
                float em = Short.toUnsignedInt(b.getShort(head + 18));
                float a = b.getShort(os2 + 68) / em, d = -b.getShort(os2 + 70) / em;
                float leading = Math.max(0, b.getShort(os2 + 72) / em);
                float cap = Short.toUnsignedInt(b.getShort(os2)) >= 2 ? b.getShort(os2 + 88) / em : 0;
                if (cap <= 0) cap = (float) font.createGlyphVector(TextBackend.CONTEXT, "H").getVisualBounds().getHeight();
                if (a > 0 && d >= 0 && em > 0) return new FaceMetrics(em, a, d, leading, cap);
            }
        } catch (RuntimeException ignored) { /* Malformed optional metadata must not invent metrics. */ }
        return new FaceMetrics(1, fallback.getAscent(), fallback.getDescent(), fallback.getLeading(),
                (float)font.createGlyphVector(TextBackend.CONTEXT, "H").getVisualBounds().getHeight());
    }
    private Face spaces(ResourceLocation id, JsonObject json) {
        Map<Integer, BitmapGlyph> glyphs = new HashMap<>();
        json.getAsJsonObject("advances").entrySet().forEach(e -> glyphs.put(e.getKey().codePointAt(0), new BitmapGlyph(null, e.getValue().getAsFloat(), 0, 0)));
        return new Face(id, null, glyphs, Set.of(), 0, 0, -7, 0);
    }
    private Face bitmap(JsonObject json) throws IOException {
        ResourceLocation id = ResourceLocation.parse(json.get("file").getAsString());
        BufferedImage image;
        try (InputStream stream = resources.open(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath()))) { image = ImageIO.read(stream); }
        if (image == null) throw new IOException("Invalid bitmap font: " + id);
        JsonArray rows = json.getAsJsonArray("chars");
        int columns = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
        int cellW = image.getWidth() / columns, cellH = image.getHeight() / rows.size();
        float height = json.has("height") ? json.get("height").getAsFloat() : 8;
        float ascent = json.get("ascent").getAsFloat(), scale = height / cellH;
        Map<Integer, BitmapGlyph> glyphs = new HashMap<>();
        for (int y = 0; y < rows.size(); y++) {
            int[] cps = rows.get(y).getAsString().codePoints().toArray();
            for (int x = 0; x < cps.length; x++) if (cps[x] != 0) {
                int right = 0;
                for (int yy = 0; yy < cellH; yy++) for (int xx = 0; xx < cellW; xx++)
                    if ((image.getRGB(x * cellW + xx, y * cellH + yy) >>> 24) != 0) right = Math.max(right, xx + 1);
                glyphs.put(cps[x], new BitmapGlyph(image.getSubimage(x * cellW, y * cellH, Math.max(1, right), cellH),
                        Math.round(right * scale) + 1, ascent, height));
            }
        }
        return new Face(id, null, glyphs, Set.of(), ascent, Math.max(0, height - ascent), -ascent, height - ascent);
    }
    private Face unihex(JsonObject json) throws IOException {
        ResourceLocation id = ResourceLocation.parse(json.get("hex_file").getAsString());
        Map<Integer, BitmapGlyph> glyphs = new HashMap<>();
        try (var zip = new ZipInputStream(resources.open(id))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) if (entry.getName().endsWith(".hex")) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(zip, java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    int colon = line.indexOf(':'); if (colon < 0) continue;
                    int cp = Integer.parseInt(line.substring(0, colon), 16);
                    String bits = line.substring(colon + 1); int width = bits.length() / 4;
                    if (width != 8 && width != 16 && width != 24 && width != 32) continue;
                    BufferedImage image = new BufferedImage(width, 16, BufferedImage.TYPE_INT_ARGB);
                    int left = width, right = 0;
                    for (int y = 0; y < 16; y++) {
                        long row = Long.parseUnsignedLong(bits.substring(y * width / 4, (y + 1) * width / 4), 16);
                        for (int x = 0; x < width; x++) if ((row & (1L << (width - x - 1))) != 0) {
                            image.setRGB(x, y, -1); left = Math.min(left, x); right = Math.max(right, x + 1);
                        }
                    }
                    if (json.has("size_overrides")) for (JsonElement item : json.getAsJsonArray("size_overrides")) {
                        var o = item.getAsJsonObject();
                        if (cp >= o.get("from").getAsString().codePointAt(0) && cp <= o.get("to").getAsString().codePointAt(0)) {
                            left = o.get("left").getAsInt(); right = o.get("right").getAsInt() + 1;
                        }
                    }
                    if (right <= left) { left = 0; right = width; }
                    if (left < 0 || right > 32) throw new IOException("Invalid unihex size override");
                    // Vanilla size overrides may extend beyond an 8px source cell. Pad those
                    // columns instead of rejecting the entire final fallback provider.
                    BufferedImage cell = new BufferedImage(right - left, 16, BufferedImage.TYPE_INT_ARGB);
                    for (int yy = 0; yy < 16; yy++) for (int xx = left; xx < Math.min(right, width); xx++)
                        cell.setRGB(xx - left, yy, image.getRGB(xx, yy));
                    glyphs.put(cp, new BitmapGlyph(cell, (right - left) / 2.0F + 1, 7, 8));
                }
            }
        }
        return new Face(id, null, glyphs, Set.of(), 7, 1, -7, 1);
    }
    private void report(String message) {
        if (reported.add(message)) System.getLogger(FontRepository.class.getName()).log(System.Logger.Level.WARNING, message);
    }
}
