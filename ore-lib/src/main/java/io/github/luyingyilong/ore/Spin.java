package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.DrawContext;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.theme.OreIcons;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A 16-frame loading indicator from a vertical 7x7 sprite strip.
 */
public final class Spin extends Control {
    private static final int FRAME_SIZE = 7;
    private static final int FRAME_COUNT = 16;
    private static final int TEXTURE_HEIGHT = FRAME_SIZE * FRAME_COUNT;

    private long frameDurationMs = 75L;

    public Spin() {
        setMinimum(FRAME_SIZE, FRAME_SIZE);
        setPreferred(FRAME_SIZE, FRAME_SIZE);
    }

    public long getFrameDuration() {
        return frameDurationMs;
    }

    public Spin setFrameDuration(long milliseconds) {
        if (milliseconds <= 0L) throw new IllegalArgumentException("Frame duration must be positive");
        frameDurationMs = milliseconds;
        return this;
    }

    int frameAt(long milliseconds) {
        return (int) Math.floorMod(milliseconds / frameDurationMs, FRAME_COUNT);
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        if (area.width() < FRAME_SIZE || area.height() < FRAME_SIZE) return;
        int frame = frameAt(System.nanoTime() / 1_000_000L);
        int x = area.x() + (area.width() - FRAME_SIZE) / 2;
        int y = area.y() + (area.height() - FRAME_SIZE) / 2;
        getThemeItem(io.github.luyingyilong.ore.theme.OreTheme.ICON_LOADING).drawFrame(context,new Rect(x,y,FRAME_SIZE,FRAME_SIZE),frame,FRAME_SIZE);
    }
}
