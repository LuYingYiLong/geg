package io.github.luyingyilong.geg.client.ui.util;

/** Shared first-line inset for Input hints and TextArea text/caret. */
public final class InputGeometry {
    private InputGeometry() { }
    public static int firstLineTop(int paddingTop, int paddedHeight, int rowHeight, float fontHeight) {
        return paddingTop + Math.max(0, (int) Math.floor((Math.min(paddedHeight, rowHeight) - fontHeight) / 2.0F));
    }
}
