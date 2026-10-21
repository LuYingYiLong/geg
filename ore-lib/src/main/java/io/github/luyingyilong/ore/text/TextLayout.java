package io.github.luyingyilong.ore.text;

import net.minecraft.network.chat.Style;
import java.awt.Font;
import java.util.*;

/** Source offsets are UTF-16. Coordinates are GUI pixels relative to the layout's top left. */
public final class TextLayout {
    public record Glyph(FontRepository.Face face, Font font, int glyphId, FontRepository.BitmapGlyph bitmap,
                        float x, float baselineShift, float bitmapScale, int source, Style style) { }
    public record Cell(int start, int end, float left, float right, boolean rtl, Style style) { }
    public record Stop(int offset, float x) { }
    public record Line(int start, int end, StyledText text, List<Glyph> glyphs, List<Cell> cells,
                       List<Stop> stops, float width) {
        public Line { glyphs = List.copyOf(glyphs); cells = List.copyOf(cells); stops = List.copyOf(stops); }
        public float getX(int offset, boolean trailing) {
            Stop chosen = stops.get(0);
            for (Stop stop : stops) {
                if (stop.offset == offset) { chosen = stop; if (!trailing) break; }
                else if (Math.abs(stop.offset - offset) < Math.abs(chosen.offset - offset)) chosen = stop;
            }
            return chosen.x;
        }
        public int hit(float x) { return hitStop(x).offset(); }
        public Stop hitStop(float x) {
            Stop first = stops.stream().min(Comparator.comparing(Stop::x)).orElseThrow();
            Stop last = stops.stream().max(Comparator.comparing(Stop::x)).orElseThrow();
            if (x <= first.x) return first;
            if (x >= last.x) return last;
            Stop best = stops.get(0);
            for (Stop stop : stops) if (Math.abs(stop.x - x) < Math.abs(best.x - x)) best = stop;
            return best;
        }
    }
    public record Position(int offset, int line, float x, float y) { }
    public record Selection(float x, float y, float width, float height) { }
    private final StyledText source;
    private final List<Line> lines;
    private final FontRepository.Metrics metrics;
    private final TextBackend backend;
    private final long generation;
    TextLayout(StyledText source, List<Line> lines, FontRepository.Metrics metrics, TextBackend backend, long generation) {
        this.source = source; this.lines = List.copyOf(lines); this.metrics = metrics; this.backend = backend; this.generation = generation;
    }
    public StyledText getSource() { return source; }
    public List<Line> getLines() { return lines; }
    public TextLayout getLineLayout(int index) { return new TextLayout(source, List.of(lines.get(index)), metrics, backend, generation); }
    public FontRepository.Metrics getMetrics() { return metrics; }
    public String getBackend() { return backend.getId(); }
    public TextBackend getTextBackend() { return backend; }
    public long getGeneration() { return generation; }
    public float getWidth() { return lines.stream().map(Line::width).max(Float::compare).orElse(0F); }
    public float getHeight() { return lines.size() * metrics.lineHeight(); }
    public Position hitTest(float x, float y) {
        int row = Math.max(0, Math.min(lines.size() - 1, (int)Math.floor(y / metrics.lineHeight())));
        Stop stop = lines.get(row).hitStop(x);
        return new Position(stop.offset(), row, stop.x(), row * metrics.lineHeight());
    }
    public Position getCaret(int offset) { return getCaret(offset, -1); }
    public Position getCaret(int offset, int preferredLine) { return getCaret(offset, preferredLine, Float.NaN); }
    public Position getCaret(int offset, int preferredLine, float preferredX) {
        offset = TextBoundaries.floor(source.text(), offset);
        if (preferredLine >= 0 && preferredLine < lines.size()) {
            Line line = lines.get(preferredLine);
            if (offset >= line.start && offset <= line.end)
                return new Position(offset, preferredLine, caretX(line, offset, preferredX), preferredLine * metrics.lineHeight());
        }
        int row = lines.size() - 1;
        for (int i = 0; i < lines.size(); i++) if (offset >= lines.get(i).start && offset <= lines.get(i).end) {
            row = i; if (i == preferredLine || offset < lines.get(i).end || preferredLine < 0) break;
        }
        return new Position(offset, row, lines.get(row).getX(offset, false), row * metrics.lineHeight());
    }
    private static float caretX(Line line, int offset, float preferredX) {
        float best=line.getX(offset,false);
        if (Float.isFinite(preferredX)) for(Stop stop:line.stops())
            if(stop.offset()==offset && Math.abs(stop.x()-preferredX)<Math.abs(best-preferredX))best=stop.x();
        return best;
    }
    public List<Selection> getSelection(int start, int end) {
        int from = Math.min(start, end), to = Math.max(start, end);
        List<Selection> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            List<Cell> cells = new ArrayList<>(lines.get(i).cells); cells.sort(Comparator.comparing(Cell::left));
            float left = -1, right = -1;
            for (Cell cell : cells) if (cell.end > from && cell.start < to) {
                if (left >= 0 && cell.left > right + 0.01F) { result.add(new Selection(left, i * metrics.lineHeight(), right - left, metrics.lineHeight())); left = -1; }
                if (left < 0) left = cell.left; right = Math.max(right, cell.right);
            }
            if (left >= 0) result.add(new Selection(left, i * metrics.lineHeight(), right - left, metrics.lineHeight()));
            if (lines.get(i).end < to && lines.get(i).end >= from && lines.get(i).end < source.text().length()
                    && source.text().charAt(lines.get(i).end) == '\n')
                result.add(new Selection(lines.get(i).width, i * metrics.lineHeight(), 1, metrics.lineHeight()));
        }
        return List.copyOf(result);
    }
    public Style getStyleAt(float x, float y) {
        int row = Math.max(0, Math.min(lines.size() - 1, (int)(y / metrics.lineHeight())));
        for (Cell cell : lines.get(row).cells) if (x >= cell.left && x < cell.right) return cell.style;
        return null;
    }
    public String getDiagnostics() {
        Set<String> faces = new LinkedHashSet<>();
        for (Line line : lines) for (Glyph glyph : line.glyphs) faces.add(glyph.face.getId() + " " + (glyph.font() == null ? "bitmap" : glyph.font().getSize2D() + "px " + glyph.face.getDeclaredMetrics()));
        return backend.getId() + " / generation " + generation + " / faces " + faces + " / metrics " + metrics + " / coverage → premultiplied source-over";
    }
}
