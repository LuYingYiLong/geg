package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.util.InputGeometry;
import io.github.luyingyilong.geg.client.ui.util.OreLayers;

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
