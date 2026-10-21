package io.github.luyingyilong.ore.control.overlay;

import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.layout.Padding;
import io.github.luyingyilong.ore.control.base.Rect;

/** Pure layout checks; a live client is required for Forge theme and focus behavior. */
public final class DialogLayoutTest {
    private DialogLayoutTest() { }

    public static void main(String[] args) {
        for (int width : new int[]{127, 128, 129, 427}) {
            Rect panel = Dialog.fittedBounds(340, 224, 0, width, 100);
            check(panel.x() >= 0 && panel.right() <= width);
            check(panel.y() >= 0 && panel.bottom() <= 100);
        }

        Box header = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
        Control title = new Control().setMinimum(30, 18)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        Control close = new Control().setMinimum(18, 18);
        header.addChild(title);
        header.addChild(close);
        header.layout(new Rect(0, 0, 100, 18), null);
        check(title.getBounds().width() == 78);
        check(close.getBounds().x() == 82);

        Box footer = new Box().setGap(2);
        Control first = new Control().setMinimum(0, 20)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Control second = new Control().setMinimum(0, 20)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footer.addChild(first);
        footer.addChild(second);
        footer.layout(new Rect(0, 0, 100, 42), null);
        check(first.getBounds().width() == 100 && first.getBounds().y() == 0);
        check(second.getBounds().width() == 100 && second.getBounds().y() == 22);

        Box column = new Box();
        Control row = new Control().setMinimum(0, 18);
        Control body = new Control()
                .setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Padding headerInset = new Padding(new Padding.Insets(2, 8, 4, 8)).setChild(row);
        Padding bodyInset = new Padding(new Padding.Insets(0, 8, 5, 8)).setChild(body);
        Padding footerInset = new Padding(new Padding.Insets(4, 8, 2, 8)).setChild(footer);
        headerInset.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        bodyInset.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        footerInset.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        column.addChild(headerInset);
        column.addChild(bodyInset);
        column.addChild(footerInset);
        column.layout(new Rect(0, 0, 100, 96), null);
        check(row.getAbsoluteBounds().x() == 8 && row.getAbsoluteBounds().y() == 2);
        check(body.getAbsoluteBounds().x() == 8 && body.getAbsoluteBounds().y() == 24);
        check(body.getBounds().width() == 84 && body.getBounds().height() == 19);
        check(footer.getAbsoluteBounds().x() == 8 && footer.getAbsoluteBounds().y() == 52);
        check(footer.getBounds().width() == 84);

        column.layout(new Rect(0, 0, 100, 80), null);
        check(body.getBounds().height() == 3);
        check(footerInset.getAbsoluteBounds().bottom() == 80);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected Ore dialog layout result");
    }
}
