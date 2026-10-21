package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.ore.control.base.Rect;

/** The action rectangles used for drawing must also delimit clicks in both layouts. */
public final class MarketTableActionTest {
    private MarketTableActionTest() {
    }

    public static void main(String[] args) {
        Rect[] normal = OfferActionLayout.bounds(new Rect(216, 100, 104, 23), 50, 51);
        check(normal[0].equals(new Rect(216, 102, 50, 18)));
        check(normal[1].equals(new Rect(269, 102, 51, 18)));
        check(normal[0].height() == normal[1].height());
        check(!normal[0].contains(268, 110) && !normal[1].contains(268, 110));

        Rect[] compact = OfferActionLayout.bounds(new Rect(16, 64, 87, 38), 50, 51);
        check(compact[0].equals(new Rect(16, 64, 87, 18)));
        check(compact[1].equals(new Rect(16, 84, 87, 18)));
        check(compact[0].height() == compact[1].height());
        check(compact[0].contains(20, 70) && compact[1].contains(20, 90));
        check(!compact[0].contains(20, 83) && !compact[1].contains(20, 83));
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected market table action bounds");
    }
}
