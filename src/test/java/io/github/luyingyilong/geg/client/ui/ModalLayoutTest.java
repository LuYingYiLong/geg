package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.Rect;

/** Placement checks independent of a live Minecraft client. */
public final class ModalLayoutTest {
    private ModalLayoutTest() {
    }

    public static void main(String[] args) {
        Rect centered = Modal.centeredBounds(200, 120, 427, 240);
        check(centered.equals(new Rect(113, 60, 200, 120)));

        Rect rightHalf = Modal.positionedBounds(213, 240, 214, 0, 427, 240);
        check(rightHalf.equals(new Rect(214, 0, 213, 240)));

        Rect clamped = Modal.positionedBounds(300, 300, 400, -10, 127, 128);
        check(clamped.equals(new Rect(0, 0, 127, 128)));

    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected Ore modal placement");
    }
}
