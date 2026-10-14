package io.github.luyingyilong.geg.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Headless text layout checks; the live client still verifies actual glyph rendering. */
public final class LabelLayoutTest {
    private LabelLayoutTest() {
    }

    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(String value) { return value.codePointCount(0, value.length()) * 6; }
            @Override public int width(FormattedText value) { return width(value.getString()); }
            @Override public int width(FormattedCharSequence value) {
                int[] count = {0};
                value.accept((index, style, codepoint) -> { count[0]++; return true; });
                return count[0] * 6;
            }
        };

        Label chinese = new Label(Component.literal("橡木台阶")).wrap(Label.Wrap.CHARACTER);
        check(chinese.lines(font, 12).size() == 2);
        check(asString(chinese.lines(font, 12).get(0)).equals("橡木"));
        Label english = new Label(Component.literal("ABCDE")).wrap(Label.Wrap.CHARACTER);
        check(english.lines(font, 12).size() == 3);

        Component styled = Component.literal("红字").withStyle(ChatFormatting.RED)
                .append(Component.literal("普通"));
        Label styledLabel = new Label(styled).wrap(Label.Wrap.CHARACTER);
        FormattedCharSequence first = styledLabel.lines(font, 12).get(0);
        boolean[] red = {false};
        first.accept((index, style, codepoint) -> {
            if (index == 0) red[0] = style.getColor() != null;
            return true;
        });
        check(red[0]);

        Label clipped = new Label(Component.literal("ABCDE"))
                .wrap(Label.Wrap.CHARACTER).overflow(Label.Overflow.CLIP);
        check(clipped.displayedLines(font, 12, 10).size() == 1);
        Label ellipsis = new Label(Component.literal("ABCDE"))
                .wrap(Label.Wrap.CHARACTER).overflow(Label.Overflow.ELLIPSIS);
        check(asString(ellipsis.displayedLines(font, 12, 10).get(0)).equals("A…"));
        Label visible = new Label(Component.literal("ABCDE"));
        check(asString(visible.displayedLines(font, 12, 10).get(0)).equals("ABCDE"));
    }

    private static String asString(FormattedCharSequence sequence) {
        StringBuilder value = new StringBuilder();
        sequence.accept((index, style, codepoint) -> {
            value.appendCodePoint(codepoint);
            return true;
        });
        return value.toString();
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected Label layout result");
    }
}
