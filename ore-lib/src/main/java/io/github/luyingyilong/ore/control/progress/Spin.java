package io.github.luyingyilong.ore.control.progress;

import io.github.luyingyilong.ore.control.display.TextureRect;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.theme.IconTheme;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;

import java.util.ArrayList;
import java.util.List;

/**
 * A 16-frame loading indicator from a vertical 7x7 sprite strip.
 */
public final class Spin extends Control {

    public static final ThemeKey<TextureRegion> TEXTURE = IconTheme.texture("Spin", "texture", "loading", 7, 112);
    private static final int FRAME_SIZE = 7;
    private static final int FRAME_COUNT = 16;
    private final TextureRect image = ownPart("image", new TextureRect().setStretchMode(TextureRect.StretchMode.SCALE).setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE));
    private TextureRegion cachedStrip;
    private List<TextureSource> frames = List.of();
    private long frameDurationMs = 75L;

    public Spin() {
        setMinimum(FRAME_SIZE, FRAME_SIZE);
        setPreferred(FRAME_SIZE, FRAME_SIZE);
    }

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "Spin");
        db.register(TEXTURE);
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
        TextureRegion strip = getThemeItem(TEXTURE);
        if (!strip.equals(cachedStrip)) {
            var next = new ArrayList<TextureSource>();
            for (int offset = 0; offset < strip.getHeight(); offset += FRAME_SIZE)
                next.add(TextureSource.region(strip.getSubRegion(0, offset, strip.getWidth(), Math.min(FRAME_SIZE, strip.getHeight() - offset))));
            cachedStrip = strip;
            frames = List.copyOf(next);
        }
        image.setTexture(frames.get(Math.floorMod(frame, frames.size())));
        image.layout(new Rect(x, y, FRAME_SIZE, FRAME_SIZE), context.getFont());
        image.draw(context, 0, 0);
    }
}
