package io.github.luyingyilong.geg.client.ui.input;

import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Scroll;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import io.github.luyingyilong.geg.client.ui.util.OreLayers;
import io.github.luyingyilong.geg.client.ui.theme.OreTextMetrics;

public final class InputLayerTest {
    public static void main(String[] args) {
        Control input = new Control();
        Control popup = new Control() { @Override public float contentDepth() { return 200; } };
        popup.addChild(input);
        check(InputControl.drawingDepth(input) == 210);
        Control parent = new Control() { @Override public float contentDepth() { return 300; } };
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
            InputGeometry.Caret normal = InputGeometry.caret(10, 20, scale, false);
            InputGeometry.Caret empty = InputGeometry.caret(10, 20, scale, true);
            check(Math.abs(normal.width() - 0.6F * scale) < 0.0001F);
            check(normal.width() == empty.width() && normal.height() == OreTextMetrics.body().lineHeight(scale));
            check(normal.y() == empty.y() && normal.y() == 20);
            check(Math.abs(empty.x() - normal.x() - 0.7F) < 0.0001F);
        }

        Control field = new Control().preferred(80, 200);
        Scroll inner = new Scroll().embedded(true).content(field);
        inner.position(0, 100).requestedSize(80, 60);
        Scroll outer = new Scroll().embedded(true).content(new Control().preferred(100, 300).addChild(inner));
        Window window = new Window(true).content(outer);
        window.layoutViewport(100, 60, null);
        outer.scrollBy(0, 80);
        check(inner.verticalVisible());
        check(!InputControl.pointerInteractive(field, 75, 25));
        check(InputControl.pointerInteractive(field, 20, 25));
        inner.vertical(Scroll.BarMode.NEVER);
        window.layoutViewport(100, 60, null);
        check(InputControl.pointerInteractive(field, 75, 25));
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Input/scroll layer or inset regression"); }
}
