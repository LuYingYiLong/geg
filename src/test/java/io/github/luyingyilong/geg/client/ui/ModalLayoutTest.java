package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Padding;
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

        Box column = new Box();
        Control row = new Control().minimum(0, 18);
        Control body = new Control()
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        Padding headerInset = new Padding(new Padding.Insets(2, 8, 4, 8)).child(row);
        Padding bodyInset = new Padding(new Padding.Insets(0, 8, 5, 8)).child(body);
        Padding footerInset = new Padding(new Padding.Insets(4, 8, 2, 8)).child(footer);
        headerInset.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        bodyInset.policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        footerInset.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        column.add(headerInset).add(bodyInset).add(footerInset);
        column.layout(new Rect(0, 0, 100, 96), null);
        check(row.absoluteBounds().x() == 8 && row.absoluteBounds().y() == 2);
        check(body.absoluteBounds().x() == 8 && body.absoluteBounds().y() == 24);
        check(body.bounds().width() == 84 && body.bounds().height() == 19);
        check(footer.absoluteBounds().x() == 8 && footer.absoluteBounds().y() == 52);
        check(footer.bounds().width() == 84);

        column.layout(new Rect(0, 0, 100, 80), null);
        check(body.bounds().height() == 3);
        check(footerInset.absoluteBounds().bottom() == 80);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected Ore modal layout result");
    }
}
