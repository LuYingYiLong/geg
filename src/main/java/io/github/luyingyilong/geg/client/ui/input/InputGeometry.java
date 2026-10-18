package io.github.luyingyilong.geg.client.ui.input;

/** Shared first-line inset for Input hints and TextArea text/caret. */
public final class InputGeometry {
    private InputGeometry() { }
    record Caret(float x, float y, float width, float height) { }

    /** Match MUI's single-line caret, including its empty-line correction and text scale. */
    static Caret caret(float x, float y, float scale, boolean emptyLine) {
        return new Caret(x - 0.8F + (emptyLine ? 0.7F : 0.0F), y - 1.0F, 0.6F * scale, 9.0F * scale);
    }
    public static int centeredLineTop(int paddingTop, int paddedHeight, float fontHeight) {
        return paddingTop + Math.max(0, (int) Math.floor((paddedHeight - fontHeight) / 2.0F));
    }
    public static int firstLineTop(int paddingTop, int paddedHeight, int rowHeight, float fontHeight) {
        return centeredLineTop(paddingTop, Math.min(paddedHeight, rowHeight), fontHeight);
    }
}
