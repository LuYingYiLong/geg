package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.Button;

/** Shared drawing and hit bounds for the two actions in an offer table cell. */
final class OfferActionLayout {
    private static final int BUTTON_HEIGHT = Button.Size.SMALL.height();

    private OfferActionLayout() {
    }

    static Rect[] bounds(Rect area, int primaryWidth, int favoriteWidth) {
        int requiredWidth = primaryWidth + 3 + favoriteWidth;
        if (area.width() >= requiredWidth) {
            int top = area.y() + Math.max(0, (area.height() - BUTTON_HEIGHT) / 2);
            return new Rect[]{new Rect(area.x(), top, primaryWidth, BUTTON_HEIGHT),
                    new Rect(area.x() + primaryWidth + 3, top, favoriteWidth, BUTTON_HEIGHT)};
        }
        return new Rect[]{new Rect(area.x(), area.y(), area.width(), BUTTON_HEIGHT),
                new Rect(area.x(), area.y() + BUTTON_HEIGHT + 2, area.width(), BUTTON_HEIGHT)};
    }
}
