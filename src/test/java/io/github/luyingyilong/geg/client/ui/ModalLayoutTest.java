package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;

/** Pure layout checks; a live client is required for Forge theme and focus behavior. */
public final class ModalLayoutTest {
    private ModalLayoutTest() { }

    public static void main(String[] args) {
        for (int width : new int[]{127, 128, 129, 427}) {
            Rect panel = Modal.fittedBounds(340, 224, 0, width, 100);
            check(panel.x() >= 0 && panel.right() <= width);
            check(panel.y() >= 0 && panel.bottom() <= 100);
        }

        Box header = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4);
        Control title = new Control().minimum(30, 18)
                .policy(Control.SizePolicy.EXPAND, Control.SizePolicy.FILL);
        Control close = new Control().minimum(18, 18);
        header.add(title).add(close);
        header.layout(new Rect(0, 0, 100, 18), null);
        check(title.bounds().width() == 78);
        check(close.bounds().x() == 82);

        Box footer = new Box().gap(2);
        Control first = new Control().minimum(0, 20)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Control second = new Control().minimum(0, 20)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        footer.add(first).add(second);
        footer.layout(new Rect(0, 0, 100, 42), null);
        check(first.bounds().width() == 100 && first.bounds().y() == 0);
        check(second.bounds().width() == 100 && second.bounds().y() == 22);

        Box column = new Box().gap(4);
        Control row = new Control().minimum(0, 18);
        Control body = new Control().minimum(0, 24)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        column.add(row).add(body).add(footer);
        column.layout(new Rect(0, 0, 100, 92), null);
        check(row.bounds().y() == 0 && body.bounds().y() == 22);
        check(footer.bounds().y() == 50 && footer.bounds().bottom() == 92);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected Ore modal layout result");
    }
}
