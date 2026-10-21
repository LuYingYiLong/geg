package io.github.luyingyilong.ore.control.input;



/**
 * Shared first-line inset for Input hints and TextArea text/caret.
 */
public final class InputGeometry {
    private InputGeometry() {
    }

    /**
     * Cover the same stable row box as selection and IME, independent of the current characters.
     */
    static Caret caret(float x, float y, float scale, float lineHeight) {
        return new Caret(x, y, Math.max(0.6F, 0.6F * scale), lineHeight);
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
