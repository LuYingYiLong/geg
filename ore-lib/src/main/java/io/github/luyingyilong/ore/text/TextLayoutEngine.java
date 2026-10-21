package io.github.luyingyilong.ore.text;

import com.ibm.icu.text.Bidi;
import com.ibm.icu.text.BreakIterator;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** One layout cache for measurement, painting and editing. No UI objects are retained. */
public final class TextLayoutEngine {
    public enum Wrap { NONE, WORD, CHARACTER }
    public record Request(StyledText text, ResourceLocation font, float size, float width, Wrap wrap,
                          int maxLines, boolean ellipsis, FontMetricsPolicy policy, Locale locale) {
        public Request {
            Objects.requireNonNull(text); Objects.requireNonNull(font); Objects.requireNonNull(wrap);
            Objects.requireNonNull(policy); Objects.requireNonNull(locale);
            if (!Float.isFinite(size) || size <= 0 || Float.isNaN(width) || width < 0 || maxLines < 1)
                throw new IllegalArgumentException("Invalid text layout dimensions");
        }
    }
    private record Key(Request request, Font bootstrapFont) { }
    private final FontRepository fonts;
    private final TextBackend backend;
    private final Map<Key, TextLayout> cache = new LinkedHashMap<>(128, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, TextLayout> entry) { return size() > 512; }
    };
    public TextLayoutEngine(FontRepository fonts, TextBackend backend) { this.fonts = fonts; this.backend = backend; }
    public FontRepository getFonts() { return fonts; }
    public TextBackend getBackend() { return backend; }
    public TextLayout layout(Request request, Font bootstrapFont) {
        Key key = new Key(request, fonts.isLoaded() ? null : bootstrapFont);
        if (request.text.text().length() > 8192) return create(request, bootstrapFont);
        return cache.computeIfAbsent(key, ignored -> create(request, bootstrapFont));
    }
    private TextLayout create(Request request, Font bootstrapFont) {
        var metrics = fonts.getMetrics(request.font, request.size, request.policy);
        for (var span : request.text.spans()) {
            var m = fonts.getMetrics(font(span.style(), request.font), request.size, request.policy);
            float ascent = Math.max(metrics.ascent(), m.ascent()), descent = Math.max(metrics.descent(), m.descent());
            metrics = new FontRepository.Metrics(ascent, descent, metrics.opticalTop(), metrics.opticalBottom(),
                    (float)Math.ceil((ascent + descent) * request.policy.lineSpacing()));
        }
        List<TextLayout.Line> lines = new ArrayList<>();
        String text = request.text.text(); int start = 0;
        while (start <= text.length()) {
            int newline = text.indexOf('\n', start), end = newline < 0 ? text.length() : newline;
            int at = start;
            do {
                int limit = end;
                if (request.wrap != Wrap.NONE && request.width < Integer.MAX_VALUE && at < end) {
                    int[] boundaries = TextBoundaries.graphemes(text.substring(at, end));
                    int low = 1, high = boundaries.length - 1, best = 1;
                    while (low <= high) {
                        int middle = (low + high) >>> 1;
                        float width = shape(request, at, at + boundaries[middle], bootstrapFont).width();
                        if (width <= request.width || middle == 1) { best = middle; low = middle + 1; } else high = middle - 1;
                    }
                    limit = at + boundaries[best];
                    if (request.wrap == Wrap.WORD && limit < end) {
                        BreakIterator breaks = BreakIterator.getLineInstance(request.locale);
                        breaks.setText(text.substring(at, end));
                        int word = breaks.preceding(limit - at + 1);
                        if (word > 0) limit = at + word;
                    }
                }
                lines.add(shape(request, at, limit, bootstrapFont));
                at = limit;
            } while (at < end && lines.size() <= request.maxLines);
            if (newline < 0 || lines.size() > request.maxLines) break;
            start = end + 1;
        }
        int count = Math.min(lines.size(), request.maxLines);
        List<TextLayout.Line> visible = new ArrayList<>(lines.subList(0, count));
        if (request.ellipsis) for (int i = 0; i < visible.size(); i++) {
            var original = visible.get(i);
            if (original.width() <= request.width && !(i == count - 1 && lines.size() > count)) continue;
            StyledText part = original.text(); TextLayout.Line replacement;
            while (true) {
                StyledText marked = part.append("…");
                Request shortened = new Request(marked, request.font, request.size, request.width, Wrap.NONE, 1, false, request.policy, request.locale);
                replacement = shape(shortened, 0, marked.text().length(), bootstrapFont);
                if (replacement.width() <= request.width || part.text().isEmpty()) break;
                part = part.slice(0, TextBoundaries.previous(part.text(), part.text().length()));
            }
            int cut = part.text().length();
            java.util.function.IntUnaryOperator map = value -> value <= cut ? original.start() + value : original.end();
            visible.set(i, new TextLayout.Line(original.start(), original.end(), replacement.text(),
                    replacement.glyphs().stream().map(g -> new TextLayout.Glyph(g.face(),g.font(),g.glyphId(),g.bitmap(),g.x(),
                            g.baselineShift(),g.bitmapScale(),original.start()+Math.min(cut,g.source()),g.style())).toList(),
                    replacement.cells().stream().map(c -> new TextLayout.Cell(map.applyAsInt(c.start()),map.applyAsInt(c.end()),
                            c.left(),c.right(),c.rtl(),c.style())).toList(),
                    replacement.stops().stream().map(stop -> new TextLayout.Stop(map.applyAsInt(stop.offset()),stop.x())).toList(),replacement.width()));
        }
        return new TextLayout(request.text, visible, metrics, backend, fonts.getGeneration());
    }
    private record Run(int start, int end, FontRepository.Face face, Style style) { }
    private static ResourceLocation font(Style style, ResourceLocation fallback) {
        return style.getFont();
    }
    private TextLayout.Line shape(Request request, int start, int end, Font bootstrapFont) {
        StyledText part = request.text.slice(start, end);
        List<TextLayout.Glyph> glyphs = new ArrayList<>(); List<TextLayout.Cell> cells = new ArrayList<>();
        List<TextLayout.Stop> stops = new ArrayList<>(); float x = 0;
        if (start == end) return new TextLayout.Line(start, end, part, glyphs, cells, List.of(new TextLayout.Stop(start, 0)), 0);
        int[] boundaries = TextBoundaries.graphemes(part.text());
        String document = request.text.text();
        int paragraphStart = document.lastIndexOf('\n', Math.max(-1, start - 1)) + 1;
        int paragraphEnd = document.indexOf('\n', end);
        if (paragraphEnd < 0) paragraphEnd = document.length();
        Bidi paragraph = new Bidi(document.substring(paragraphStart, paragraphEnd), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        Bidi bidi = paragraph.setLine(start - paragraphStart, end - paragraphStart);
        char[] chars = part.text().toCharArray();
        for (int i = 0; i < bidi.countRuns(); i++) {
            var direction = bidi.getVisualRun(i); boolean rtl = direction.isOddRun();
            List<Run> runs = new ArrayList<>();
            for (int j = 0; j < boundaries.length - 1; j++) {
                int from = boundaries[j], to = boundaries[j + 1];
                if (from < direction.getStart() || from >= direction.getLimit()) continue;
                Style style = part.getStyle(from);
                var face = fonts.resolve(font(style, request.font), part.text().substring(from, to));
                if (!runs.isEmpty()) {
                    Run old = runs.get(runs.size() - 1);
                    if (old.face == face && old.style.equals(style)) { runs.set(runs.size() - 1, new Run(old.start, to, face, style)); continue; }
                }
                runs.add(new Run(from, to, face, style));
            }
            if (rtl) Collections.reverse(runs);
            for (Run run : runs) {
                float scale = run.face.getScale(request.size, request.policy), shift = run.face.getBaselineShift(request.size, request.policy);
                if (run.face.isOutline() && fonts.isLoaded()) {
                    var font = run.face.getFont(request.size, request.policy, run.style.isBold(), run.style.isItalic());
                    var vector = backend.shape(font, chars, run.start, run.end, rtl);
                    TreeMap<Integer, float[]> clusters = new TreeMap<>();
                    for (int g = 0; g < vector.getNumGlyphs(); g++) {
                        int source = TextBoundaries.floor(part.text(), run.start + vector.getGlyphCharIndex(g));
                        float gx = (float)vector.getGlyphPosition(g).getX(), next = (float)vector.getGlyphPosition(g + 1).getX();
                        glyphs.add(new TextLayout.Glyph(run.face, font, vector.getGlyphCode(g), null,
                                x + gx, shift + (float)vector.getGlyphPosition(g).getY(), 1, start + source, part.getStyle(source)));
                        float[] range = clusters.computeIfAbsent(source, ignored -> new float[]{Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY});
                        range[0] = Math.min(range[0], Math.min(gx, next)); range[1] = Math.max(range[1], Math.max(gx, next));
                    }
                    for (var entry : clusters.entrySet()) {
                        int from = entry.getKey(), to = clusters.higherKey(from) == null ? run.end : clusters.higherKey(from);
                        List<Integer> inside = new ArrayList<>();
                        for (int boundary : boundaries) if (boundary >= from && boundary <= to) inside.add(boundary);
                        for (int j = 0; j + 1 < inside.size(); j++) {
                            float left = x + entry.getValue()[0], width = entry.getValue()[1] - entry.getValue()[0];
                            float a = left + width * (rtl ? inside.size() - j - 2 : j) / (inside.size() - 1);
                            addCell(cells, stops, start + inside.get(j), start + inside.get(j + 1), a, a + width / (inside.size() - 1), rtl, part.getStyle(inside.get(j)));
                        }
                    }
                    x += (float)vector.getGlyphPosition(vector.getNumGlyphs()).getX();
                } else {
                    List<Integer> indexes = new ArrayList<>();
                    for (int j = 0; j + 1 < boundaries.length; j++) if (boundaries[j] >= run.start && boundaries[j] < run.end) indexes.add(j);
                    if (rtl) Collections.reverse(indexes);
                    for (int j : indexes) {
                        int from = boundaries[j], to = boundaries[j + 1];
                        var bitmap = fonts.isEmoji(run.face) ? fonts.getEmoji(part.text().substring(from, to)) : run.face.getBitmap(part.text().codePointAt(from));
                        if (bitmap == null) bitmap = run.face.getBitmap(0xFFFD);
                        float advance = bitmap == null ? 0 : bitmap.advance() * scale;
                        if (bitmap != null && !bitmap.colored() && run.style.isBold()) advance += scale;
                        if (!fonts.isLoaded() && bootstrapFont != null)
                            advance = bootstrapFont.width(net.minecraft.network.chat.Component.literal(part.text().substring(from, to)).withStyle(run.style)) * request.size / 8;
                        if (bitmap != null) glyphs.add(new TextLayout.Glyph(run.face, null, part.text().codePointAt(from), bitmap, x, shift, scale, start + from, run.style));
                        addCell(cells, stops, start + from, start + to, x, x + advance, rtl, run.style); x += advance;
                    }
                }
            }
        }
        if (stops.isEmpty()) stops.add(new TextLayout.Stop(start, 0));
        return new TextLayout.Line(start, end, part, glyphs, cells, stops, x);
    }
    private static void addCell(List<TextLayout.Cell> cells, List<TextLayout.Stop> stops, int from, int to, float left, float right, boolean rtl, Style style) {
        cells.add(new TextLayout.Cell(from, to, left, right, rtl, style));
        stops.add(new TextLayout.Stop(from, rtl ? right : left)); stops.add(new TextLayout.Stop(to, rtl ? left : right));
    }
}
