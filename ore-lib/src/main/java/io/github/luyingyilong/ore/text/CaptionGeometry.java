package io.github.luyingyilong.ore.text;

import io.github.luyingyilong.ore.control.base.Rect;

/** Shared optical caption placement for button-like, single-line controls. */
public final class CaptionGeometry {
    private CaptionGeometry() { }

    public static Rect getLabelBounds(Rect contentArea) {
        return contentArea.translated(0, -2);
    }

    /** Leading images share the caption's optical offset, including themed pressed-state offsets. */
    public static Rect getIconBounds(Rect contentArea, int width, int height) {
        Rect aligned = getLabelBounds(contentArea);
        return new Rect(aligned.x(), aligned.y() + (aligned.height() - height) / 2, width, height);
    }
}
