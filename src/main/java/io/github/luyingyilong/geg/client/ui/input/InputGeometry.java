package io.github.luyingyilong.geg.client.ui.input;

/** Shared first-line inset for Input hints and TextArea text/caret. */
public final class InputGeometry {
    private InputGeometry() { }
    public static int centeredLineTop(int paddingTop, int paddedHeight, float fontHeight) {
        return paddingTop + Math.max(0, (int) Math.floor((paddedHeight - fontHeight) / 2.0F));
    }
    public static int firstLineTop(int paddingTop, int paddedHeight, int rowHeight, float fontHeight) {
        return centeredLineTop(paddingTop, Math.min(paddedHeight, rowHeight), fontHeight);
    }
}
