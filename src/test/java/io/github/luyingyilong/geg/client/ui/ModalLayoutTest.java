package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.Rect;

/** Pure layout checks; a live client is required for Forge theme and focus behavior. */
public final class ModalLayoutTest {
    private ModalLayoutTest() { }

    public static void main(String[] args) {
        for (int width : new int[]{127, 128, 129, 427}) {
            Rect panel = Modal.fittedBounds(340, 224, 0, width, 100);
            check(panel.x() >= 0 && panel.right() <= width);
            check(panel.y() >= 0 && panel.bottom() <= 100);
            int header = Modal.headerHeight(panel.height());
            int footer = Modal.footerHeight(panel.height());
            check(header == 20 && footer == 22);
            int contentHeight = Math.max(0, panel.height() - header - footer);
            check(header + contentHeight + footer <= panel.height());
        }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected Ore modal layout result");
    }
}
