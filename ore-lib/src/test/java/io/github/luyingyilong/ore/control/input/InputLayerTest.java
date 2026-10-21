package io.github.luyingyilong.ore.control.input;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.window.Window;
import io.github.luyingyilong.ore.render.internal.OreLayers;
import io.github.luyingyilong.ore.text.OreText;

public final class InputLayerTest {
    public static void main(String[] args) {
        Control input = new Control();
        Control popup = new Control() { @Override public float getContentDepth() { return 200; } };
        popup.addChild(input);
        check(InputControl.drawingDepth(input) == 210);
        Control parent = new Control() { @Override public float getContentDepth() { return 300; } };
        parent.addChild(popup);
        check(InputControl.drawingDepth(input) == 510);
        check(OreLayers.INPUT < OreLayers.SCROLLBAR && OreLayers.SCROLLBAR < 200);
        check(InputGeometry.firstLineTop(0, 20, 20, 10) == InputGeometry.firstLineTop(0, 80, 20, 10));
        check(InputGeometry.firstLineTop(4, 72, 20, 10) == 9);
        check(InputGeometry.firstLineTop(0, 5, 20, 10) == 0);
        check(InputGeometry.centeredLineTop(0, 20, 10) == 5);
        check(InputGeometry.centeredLineTop(0, 80, 10) == 35);
        check(InputGeometry.centeredLineTop(4, 72, 10) == 35);
        check(InputGeometry.centeredLineTop(4, 5, 10) == 4);
        check(InputGeometry.centeredLineTop(3, 31, 9.0F) == 14);
        for (float scale : new float[]{0.75F, 1.0F, 1.5F, 2.0F}) {
            InputGeometry.Caret normal = InputGeometry.caret(10, 20, scale, OreText.bodyMetrics(scale).lineHeight());
            InputGeometry.Caret empty = InputGeometry.caret(10, 20, scale, OreText.bodyMetrics(scale).lineHeight());
            check(Math.abs(normal.width() - Math.max(0.6F, 0.6F * scale)) < 0.0001F);
            check(normal.width() == empty.width() && normal.height() == OreText.bodyMetrics(scale).lineHeight());
            check(normal.y() == empty.y() && normal.y() == 20);
            check(Math.abs(empty.x() - normal.x()) < 0.0001F);
        }

        Control field = new Control().setPreferred(80, 200);
        Scroll inner = new Scroll().setEmbedded(true).setContent(field);
        inner.setPosition(0, 100).setRequestedSize(80, 60);
        var childContainer25 = new Control().setPreferred(100, 300);
        childContainer25.addChild(inner);
        Scroll outer = new Scroll().setEmbedded(true).setContent(childContainer25);
        Window window = new Window(true).setContent(outer);
        window.layoutViewport(100, 60, null);
        outer.scrollBy(0, 80);
        check(inner.getVerticalVisible());
        check(!InputControl.pointerInteractive(field, 75, 25));
        check(InputControl.pointerInteractive(field, 20, 25));
        inner.setVertical(Scroll.BarMode.NEVER);
        window.layoutViewport(100, 60, null);
        check(InputControl.pointerInteractive(field, 75, 25));
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Input/scroll layer or inset regression"); }
}
