package io.github.luyingyilong.ore.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawn last, above Ore popups, without taking mouse or keyboard input.
 */
public final class FocusOverlay extends Control {
    private static final int WHITE = 0xFFFFFFFF;
    private final FocusManager focus;

    FocusOverlay(FocusManager focus) {
        this.focus = focus;
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            Rect bounds = focus.getOutlineBounds();
            if (bounds == null || bounds.width() < 2 || bounds.height() < 2) return;
            int x = screenX + bounds.x();
            int y = screenY + bounds.y();
            int right = x + bounds.width();
            int bottom = y + bounds.height();
            GuiGraphics graphics = context.getGraphics();
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 500.0F);
            try {
                graphics.fill(x, y, right, y + 1, WHITE);
                graphics.fill(x, bottom - 1, right, bottom, WHITE);
                graphics.fill(x, y + 1, x + 1, bottom - 1, WHITE);
                graphics.fill(right - 1, y + 1, right, bottom - 1, WHITE);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    @Override
    public Control hitTop(int rootX, int rootY) {
        return null;
    }

}
