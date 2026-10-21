package io.github.luyingyilong.ore.input;

import io.github.luyingyilong.ore.util.TextLines;

import java.util.List;

/** The multiline editor's real offset/normalization helpers run without a Forge client. */
public final class TextAreaTest {
    public static void main(String[] args) {
        check(TextLines.split("你好\r\nSecond line\n").equals(List.of("你好", "Second line", "")));
        check(TextLines.split("").equals(List.of("")));
        String committed = "A😀\n中B\nTail";
        check(TextLines.position(committed, 3).equals(new TextLines.Position(3, 0)));
        String clusters = "第一行\n👩🏽‍💻é中文";
        for (int offset : io.github.luyingyilong.ore.text.TextBoundaries.graphemes(clusters)) {
            var point = TextLines.position(clusters, offset);
            check(TextLines.offset(TextLines.split(clusters), point.column(), point.line()) == offset);
        }
        int start = TextLines.offset(TextLines.split(committed), 1, 1);
        check(start == 5 && TextLines.position(committed, start).equals(new TextLines.Position(1, 1)));
        var display = new InputPreedit("拼音", 1).present(committed, start, start + 1);
        check(display.text().equals("A😀\n中拼音\nTail"));
        check(TextLines.position(display.text(), display.cursor()).equals(new TextLines.Position(2, 1)));
        var selection = new InputPreedit("文", 1).present(committed, 1,
                TextLines.offset(TextLines.split(committed), 1, 2));
        check(selection.text().equals("A文ail"));
        check(NumericExpressionFixture.coin("1-1", true).equals("0.00"));
        try { NumericExpressionFixture.coin("0"); throw new AssertionError("Market price accepted zero"); }
        catch (IllegalArgumentException expected) { }
        try { NumericExpressionFixture.coin("-1", true); throw new AssertionError("Negative attachment accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("TextArea regression"); }
}
