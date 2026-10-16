package io.github.luyingyilong.geg.client.ui.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

/** Soft lines retain their UTF-16 source columns; wrapping never changes the submitted text. */
public final class WrappedText {
    public record Row(int line, int start, int end, String text, float width) { }
    public record Position(int row, float x) { }
    public record Cursor(int column, int line) { }

    private final List<Row> rows;
    private final IntToDoubleFunction advance;

    public WrappedText(List<String> lines, float width, IntToDoubleFunction advance) {
        this.advance = advance;
        List<Row> result = new ArrayList<>();
        for (int line = 0; line < Math.max(1, lines.size()); line++) {
            String text = lines.isEmpty() ? "" : lines.get(line);
            int start = 0;
            do {
                int end = start, wordBreak = start;
                double used = 0;
                while (end < text.length()) {
                    int cp = text.codePointAt(end);
                    double next = advance.applyAsDouble(cp);
                    if (end > start && used + next > Math.max(1, width)) break;
                    used += next;
                    end += Character.charCount(cp);
                    if (Character.isWhitespace(cp) || cp == '-') wordBreak = end;
                }
                if (end < text.length() && wordBreak > start) end = wordBreak;
                String part = text.substring(start, end);
                result.add(new Row(line, start, end, part, (float) part.codePoints().mapToDouble(advance).sum()));
                start = end;
            } while (start < text.length());
        }
        rows = List.copyOf(result);
    }

    public List<Row> rows() { return rows; }

    public float xAt(Row row, int column) {
        int end = Math.max(0, Math.min(row.text().length(), column - row.start()));
        if (end > 0 && end < row.text().length() && Character.isLowSurrogate(row.text().charAt(end))) end--;
        return (float) row.text().substring(0, end).codePoints().mapToDouble(advance).sum();
    }

    public Position locate(int line, int column) {
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            boolean last = index == rows.size() - 1 || rows.get(index + 1).line() != row.line();
            if (row.line() >= line && (column < row.end() || last))
                return new Position(index, xAt(row, column));
        }
        Row last = rows.get(rows.size() - 1);
        return new Position(rows.size() - 1, last.width());
    }

    public Cursor hit(int rowIndex, float x) {
        Row row = rows.get(Math.max(0, Math.min(rows.size() - 1, rowIndex)));
        double used = 0;
        int column = 0;
        while (column < row.text().length()) {
            int cp = row.text().codePointAt(column);
            double next = advance.applyAsDouble(cp);
            if (x < used + next / 2) break;
            used += next;
            column += Character.charCount(cp);
        }
        return new Cursor(row.start() + column, row.line());
    }
}
