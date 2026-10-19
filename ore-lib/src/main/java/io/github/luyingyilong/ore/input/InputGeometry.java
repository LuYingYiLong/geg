package io.github.luyingyilong.ore.input;

import io.github.luyingyilong.ore.theme.OreTextMetrics;

/**
 * Shared first-line inset for Input hints and TextArea text/caret.
 */
public final class InputGeometry {
    private InputGeometry() {
    }

    /**
     * Cover the same stable row box as selection and IME, independent of the current characters.
     */
    static Caret caret(float x, float y, float scale, boolean emptyLine) {
        return new Caret(x - 0.8F + (emptyLine ? 0.7F : 0.0F), y, 0.6F * scale,
                OreTextMetrics.body().lineHeight(scale));
    }

    public static int centeredLineTop(int paddingTop, int paddedHeight, float fontHeight) {
        return paddingTop + Math.max(0, (int) Math.floor((paddedHeight - fontHeight) / 2.0F));
    }

    public static int firstLineTop(int paddingTop, int paddedHeight, int rowHeight, float fontHeight) {
        return centeredLineTop(paddingTop, Math.min(paddedHeight, rowHeight), fontHeight);
    }

    record Caret(float x, float y, float width, float height) {
    }
}
