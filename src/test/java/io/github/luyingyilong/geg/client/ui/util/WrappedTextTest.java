package io.github.luyingyilong.geg.client.ui.util;

import java.util.List;
import io.github.luyingyilong.geg.client.ui.InputPreedit;

public final class WrappedTextTest {
    public static void main(String[] args) {
        WrappedText chinese = wrap(List.of("一二三四五六七八九十"), 4);
        check(chinese.rows().stream().map(WrappedText.Row::text).toList().equals(List.of("一二三四", "五六七八", "九十")));
        check(chinese.locate(0, 4).equals(new WrappedText.Position(1, 0)));
        check(chinese.locate(0, 9).equals(new WrappedText.Position(2, 1)));
        check(chinese.hit(1, 2.1F).equals(new WrappedText.Cursor(6, 0)));

        List<String> source = List.of("hello world", "", "abcdefg", "");
        WrappedText words = wrap(source, 6);
        check(words.rows().stream().map(WrappedText.Row::text).toList().equals(
                List.of("hello ", "world", "", "abcdef", "g", "")));
        for (int line = 0; line < source.size(); line++) {
            int logicalLine = line;
            String restored = words.rows().stream().filter(row -> row.line() == logicalLine)
                    .map(WrappedText.Row::text).reduce("", String::concat);
            check(restored.equals(source.get(line)));
        }
        check(words.hit(3, 3).equals(new WrappedText.Cursor(3, 2)));
        check(words.locate(3, 0).row() == 5);
        check(wrap(source, 100).rows().size() == source.size());

        WrappedText emoji = new WrappedText(List.of("A😀BC😀D"), 3, cp -> cp > 0xFFFF ? 2 : 1);
        check(emoji.rows().stream().map(WrappedText.Row::text).toList().equals(List.of("A😀", "BC", "😀D")));
        check(emoji.hit(0, 1.4F).column() == 1);
        check(emoji.hit(0, 2.4F).column() == 3);
        check(emoji.locate(0, 3).row() == 1);
        check(emoji.locate(0, 5).row() == 2);
        check(emoji.xAt(emoji.rows().get(0), 2) == 1); // Never split a surrogate at a caret.
        check(wrap(List.of("中文"), 0).rows().size() == 2);
        check(wrap(List.of(), 20).rows().get(0).text().isEmpty());
        check(wrap(List.of("  a  b   "), 3).rows().stream().map(WrappedText.Row::text)
                .reduce("", String::concat).equals("  a  b   "));

        var preedit = new InputPreedit("中文", 2).present("abcdEF", 2, 4);
        WrappedText composed = wrap(TextLines.split(preedit.text()), 3);
        check(preedit.text().equals("ab中文EF"));
        check(composed.locate(0, preedit.cursor()).equals(new WrappedText.Position(1, 1)));
        check(composed.hit(1, 1).equals(new WrappedText.Cursor(preedit.cursor(), 0)));
        WrappedText resized = wrap(List.of("abcdEF"), 2);
        check(resized.locate(0, 5).equals(new WrappedText.Position(2, 1)));
        WrappedText scaled = new WrappedText(List.of("ab中cd"), 6, cp -> cp > 127 ? 3 : 1.5);
        check(scaled.rows().stream().map(WrappedText.Row::text).toList().equals(List.of("ab中", "cd")));

        // Every visual glyph boundary maps back to the same logical cursor, including hard lines.
        for (int index = 0; index < words.rows().size(); index++) {
            var row = words.rows().get(index);
            for (int column = row.start(); column <= row.end(); column++) {
                var cursor = words.hit(index, words.xAt(row, column));
                check(cursor.line() == row.line() && cursor.column() == column);
            }
        }
    }

    private static WrappedText wrap(List<String> text, int width) { return new WrappedText(text, width, cp -> 1); }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Soft-wrap text, caret or source preservation regression");
    }
}
