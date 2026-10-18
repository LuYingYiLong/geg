package io.github.luyingyilong.ore;

import brachy.modularui.screen.viewport.ModularGuiContext;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.theme.OreIcons;
import net.minecraft.client.gui.GuiGraphics;

/** A 16-frame loading indicator from a vertical 7x7 sprite strip. */
public final class Spin extends Control {
    private static final int FRAME_SIZE = 7;
    private static final int FRAME_COUNT = 16;
    private static final int TEXTURE_HEIGHT = FRAME_SIZE * FRAME_COUNT;

    private long frameDurationMs = 75L;

    public Spin() {
        minimum(FRAME_SIZE, FRAME_SIZE);
        preferred(FRAME_SIZE, FRAME_SIZE);
    }

    public Spin frameDuration(long milliseconds) {
        if (milliseconds <= 0L) throw new IllegalArgumentException("Frame duration must be positive");
        frameDurationMs = milliseconds;
        return this;
    }

    public long frameDuration() { return frameDurationMs; }

    int frameAt(long milliseconds) {
        return (int) Math.floorMod(milliseconds / frameDurationMs, FRAME_COUNT);
    }

    @Override
    protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = screenBounds(screenX, screenY);
        if (area.width() < FRAME_SIZE || area.height() < FRAME_SIZE) return;
        int frame = frameAt(System.nanoTime() / 1_000_000L);
        int x = area.x() + (area.width() - FRAME_SIZE) / 2;
        int y = area.y() + (area.height() - FRAME_SIZE) / 2;
        GuiGraphics graphics = context.getGraphics();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(OreIcons.LOADING, x, y, 0.0F, (float) (frame * FRAME_SIZE),
                FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, TEXTURE_HEIGHT);
        RenderSystem.disableBlend();
    }
}
