package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.theme.OreFonts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
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

        Label chinese = new Label(Component.literal("橡木台阶")).setWrap(Label.Wrap.CHARACTER);
        check(chinese.lines(font, 12).size() == 2);
        check(asString(chinese.lines(font, 12).get(0)).equals("橡木"));
        Label english = new Label(Component.literal("ABCDE")).setWrap(Label.Wrap.CHARACTER);
        check(english.lines(font, 12).size() == 3);
        check(english.measureSize(font, new Size(12, 100)).height() == 3 * english.lineHeight(font));
        english.setText(Component.literal("AB"));
        check(english.getText().getString().equals("AB"));
        Label scaled = new Label(Component.literal("ABCD")).setScale(2.0F).setWrap(Label.Wrap.CHARACTER);
        check(scaled.lines(font, 24).size() == 2);
        check(scaled.measureSize(font, new Size(24, 100)).height() == 2 * scaled.lineHeight(font));
        Box labelParent = new Box().add(english);
        labelParent.layout(new Rect(0, 0, 20, 20), font);
        english.setText(Component.literal("Longer label"));
        check(labelParent.getLayoutDirty());

        Component styled = Component.literal("红字").withStyle(ChatFormatting.RED)
                .append(Component.literal("普通"));
        Label styledLabel = new Label(styled).setWrap(Label.Wrap.CHARACTER);
        FormattedCharSequence first = styledLabel.lines(font, 12).get(0);
        boolean[] red = {false};
        first.accept((index, style, codepoint) -> {
            if (index == 0) red[0] = style.getColor() != null;
            return true;
        });
        check(red[0]);

        Label clipped = new Label(Component.literal("ABCDE"))
                .setWrap(Label.Wrap.CHARACTER).setOverflow(Label.Overflow.CLIP);
        check(clipped.displayedLines(font, 12, 10).size() == 1);
        Label ellipsis = new Label(Component.literal("ABCDE"))
                .setWrap(Label.Wrap.CHARACTER).setOverflow(Label.Overflow.ELLIPSIS);
        check(asString(ellipsis.displayedLines(font, 12, 10).get(0)).equals("A…"));
        Label visible = new Label(Component.literal("ABCDE"));
        check(asString(visible.displayedLines(font, 12, 10).get(0)).equals("ABCDE"));

        Label marquee = new Label(Component.literal("ABCDE"))
                .setWrap(Label.Wrap.CHARACTER).setOverflow(Label.Overflow.MARQUEE)
                .setScrollSpeed(6.0F).setScrollEndPauseMs(500L);
        check(marquee.displayedLines(font, 12, 10).size() == 1);
        check(marquee.marqueeOffset(font, 12, 1000L, true) == 0);
        check(marquee.marqueeOffset(font, 12, 2000L, true) == 6);
        check(marquee.marqueeOffset(font, 12, 4000L, true) == 18);
        check(marquee.marqueeOffset(font, 12, 4300L, true) == 18);
        check(marquee.marqueeOffset(font, 12, 5000L, true) == 15);
        check(marquee.marqueeOffset(font, 12, 5001L, false) == 0);
        check(marquee.marqueeOffset(font, 12, 6000L, true) == 0);
        marquee.setScrollTrigger(Label.ScrollTrigger.ALWAYS);
        check(marquee.marqueeOffset(font, 12, 7000L, false) == 0);
        check(marquee.marqueeOffset(font, 12, 8000L, false) == 6);
        marquee.setText(Component.literal("AB"));
        check(marquee.marqueeOffset(font, 12, 9000L, false) == 0);
        Label configuredEachFrame = new Label(Component.literal("ABCDE"));
        for (int frame = 0; frame <= 10; frame++) {
            configuredEachFrame.setOverflow(Label.Overflow.MARQUEE).setScrollTrigger(Label.ScrollTrigger.HOVER)
                    .setScrollSpeed(6).setScrollEndPauseMs(500).setScale(1);
            check(configuredEachFrame.marqueeOffset(font, 12, 1000 + frame * 100L, true) == frame * 6 / 10);
        }
        configuredEachFrame.setScrollSpeed(12);
        check(configuredEachFrame.marqueeOffset(font, 12, 2100, true) == 0);
        fontFamiliesAndStyles();
        fontSizeLayout(font);
        stableBaselines(font);
    }

    private static void stableBaselines(Font font) {
        Label label = new Label().setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        for (boolean optical : new boolean[]{false, true}) {
            label.setOpticalCenter(optical);
            float baseline = label.firstLineOrigin(22, 1);
            for (String text : new String[]{"", "abc", "中文", "abc中文", "abc中文gjpq", "ÉÅ"}) {
                label.setText(Component.literal(text));
                check(label.firstLineOrigin(22, 1) == baseline);
                check(label.lineHeight(font) == 10);
            }
        }
        label.setAlignment(Label.Horizontal.LEFT, Label.Vertical.TOP);
        check(label.firstLineOrigin(10, 1) > 0); // Keep the TTF's slight negative top inside the row.
    }

    private static void fontSizeLayout(Font font) {
        Label label = new Label(Component.literal("ABCDE"));
        check(label.getFontSize() == 8.0F && label.measureWidth(font, 100) == 30);
        Box parent = new Box().add(label);
        parent.layout(new Rect(0, 0, 100, 30), font);
        label.setFontSize(16.0F).setWrap(Label.Wrap.CHARACTER);
        check(parent.getLayoutDirty());
        check(label.measureWidth(font, 100) == 60 && label.lineHeight(font) == 20);
        check(label.lines(font, 24).size() == 3);
        check(label.measureSize(font, new Size(24, 100)).height() == 60);
        label.setOverflow(Label.Overflow.ELLIPSIS);
        check(label.displayedLines(font, 24, 40).size() == 2);
        check(asString(label.displayedLines(font, 24, 40).get(1)).equals("C…"));
        label.setScale(0.5F);
        check(label.measureWidth(font, 100) == 30 && label.lineHeight(font) == 10);
        Label marquee = new Label(Component.literal("ABCDE")).setFontSize(16.0F)
                .setOverflow(Label.Overflow.MARQUEE).setScrollSpeed(12.0F);
        check(marquee.marqueeOffset(font, 24, 1000, true) == 0);
        check(marquee.marqueeOffset(font, 24, 2000, true) == 12);
        marquee.setFontSize(8.0F);
        check(marquee.marqueeOffset(font, 24, 3000, true) == 0);
        for (float invalid : new float[]{0.0F, -1.0F, Float.NaN, Float.POSITIVE_INFINITY}) {
            try {
                label.setFontSize(invalid);
                throw new AssertionError("Invalid font size accepted");
            } catch (IllegalArgumentException expected) {
                // The previous valid size remains usable after validation fails.
                check(label.getFontSize() == 16.0F);
            }
        }
    }

    private static void fontFamiliesAndStyles() {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) {
                int[] width = {0};
                text.visit((style, value) -> {
                    width[0] += value.codePointCount(0, value.length())
                            * (style.getFont().equals(OreFonts.HERO) ? 4 : 6);
                    return java.util.Optional.empty();
                }, Style.EMPTY);
                return width[0];
            }
            @Override public int width(FormattedCharSequence text) {
                int[] width = {0};
                text.accept((index, style, codepoint) -> {
                    width[0] += style.getFont().equals(OreFonts.HERO) ? 4 : 6;
                    return true;
                });
                return width[0];
            }
        };
        Component original = Component.literal("AB").withStyle(ChatFormatting.RED);
        Label label = new Label(original);
        check(!label.getHero() && label.measureWidth(font, 100) == 12);
        check(label.getStyledText().getStyle().getFont().equals(OreFonts.BODY));
        Box parent = new Box().add(label);
        parent.layout(new Rect(0, 0, 100, 20), font);
        label.setHero(true);
        check(parent.getLayoutDirty() && label.measureWidth(font, 100) == 8);
        check(label.getStyledText().getStyle().getFont().equals(OreFonts.HERO));
        check(original.getStyle().getFont().equals(Style.DEFAULT_FONT));
        check(label.getStyledText().getStyle().getColor().equals(original.getStyle().getColor()));
        ResourceLocation custom = ResourceLocation.fromNamespaceAndPath("test", "symbols");
        label.setText(Component.literal("A").append(Component.literal("B")
                .withStyle(style -> style.withFont(custom))));
        java.util.List<ResourceLocation> fonts = new java.util.ArrayList<>();
        label.lines(font, 100).get(0).accept((index, style, codepoint) -> {
            fonts.add(style.getFont());
            return true;
        });
        check(fonts.equals(java.util.List.of(OreFonts.HERO, custom)));
        label.setText(Component.literal("ABCD")).setOverflow(Label.Overflow.ELLIPSIS);
        check(asString(label.displayedLines(font, 8, 10).get(0)).equals("A…"));
        label.displayedLines(font, 8, 10).get(0).accept((index, style, codepoint) -> {
            check(style.getFont().equals(OreFonts.HERO));
            return true;
        });
        Button button = new Button(Component.literal("AB")).setHero(true);
        check(button.getHero() && button.measureWidth(font, 100) == 8 + 2 * Button.PADDING_X);
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
