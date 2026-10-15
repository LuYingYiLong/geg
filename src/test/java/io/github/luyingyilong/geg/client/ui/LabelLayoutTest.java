package io.github.luyingyilong.geg.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import io.github.luyingyilong.geg.client.ui.layout.Size;

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
        check(english.measureSize(font, new Size(12, 100)).height() == 3 * english.lineHeight(font));
        english.setText(Component.literal("AB"));
        check(english.getText().getString().equals("AB"));

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

        Label marquee = new Label(Component.literal("ABCDE"))
                .wrap(Label.Wrap.CHARACTER).overflow(Label.Overflow.MARQUEE)
                .scrollSpeed(6.0F).scrollEndPause(500L);
        check(marquee.displayedLines(font, 12, 10).size() == 1);
        check(marquee.marqueeOffset(font, 12, 1000L, true) == 0);
        check(marquee.marqueeOffset(font, 12, 2000L, true) == 6);
        check(marquee.marqueeOffset(font, 12, 4000L, true) == 18);
        check(marquee.marqueeOffset(font, 12, 4300L, true) == 18);
        check(marquee.marqueeOffset(font, 12, 5000L, true) == 15);
        check(marquee.marqueeOffset(font, 12, 5001L, false) == 0);
        check(marquee.marqueeOffset(font, 12, 6000L, true) == 0);
        marquee.scrollTrigger(Label.ScrollTrigger.ALWAYS);
        check(marquee.marqueeOffset(font, 12, 7000L, false) == 0);
        check(marquee.marqueeOffset(font, 12, 8000L, false) == 6);
        marquee.text(Component.literal("AB"));
        check(marquee.marqueeOffset(font, 12, 9000L, false) == 0);
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
