package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.ControlNotification;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TexturePainter;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.ThemeDB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Retained, non-interactive image. Source metadata is resolved independently of its presentation.
 */
public final class TextureRect extends Control {
    private TextureSource texture;
    private StretchMode stretchMode = StretchMode.KEEP_ASPECT_CENTERED;
    private ExpandMode expandMode = ExpandMode.KEEP_SIZE;
    private boolean flipHorizontal, flipVertical;
    private float opacity = 1F;
    private int modulate = 0xFFFFFFFF;
    private Rect cachedArea;
    private TextureRegion cachedRegion;
    private StretchMode cachedMode;
    private boolean cachedFlipH, cachedFlipV;
    private List<TexturePainter.Quad> cachedQuads = List.of();

    public static void registerTheme(ThemeDB db) {
        OreTheme.registerType(db, "TextureRect");
    }

    private static void addQuad(List<TexturePainter.Quad> out, TextureRegion image, float x, float y, float width, float height,
                                float sx, float sy, float sw, float sh, boolean flipH, boolean flipV) {
        float u0 = flipH ? image.getWidth() - sx : sx, u1 = flipH ? image.getWidth() - sx - sw : sx + sw;
        float v0 = flipV ? image.getHeight() - sy : sy, v1 = flipV ? image.getHeight() - sy - sh : sy + sh;
        out.add(new TexturePainter.Quad(x, y, width, height, image.getX() + u0, image.getY() + v0, image.getX() + u1, image.getY() + v1));
    }

    public TextureSource getTexture() {
        return texture;
    }

    public TextureRect setTexture(TextureSource source) {
        getSignalScope().checkThread();
        if (!isAlive()) throw new IllegalStateException("Cannot update a pending/freed image");
        if (!Objects.equals(texture, source)) {
            TextureRegion before = getResolvedTextureRegion();
            TextureRegion after = source == null ? null : source.resolve(this);
            texture = source;
            clearGeometry();
            boolean presenceChanged = (before == null) != (after == null);
            boolean sizeChanged = before != null && after != null && (before.getWidth() != after.getWidth() || before.getHeight() != after.getHeight());
            if (presenceChanged || getExpandMode() == ExpandMode.KEEP_SIZE && sizeChanged) requestLayout();
        }
        return this;
    }

    public TextureRegion getResolvedTextureRegion() {
        return texture == null ? null : texture.resolve(this);
    }

    @Inspectable(group = "Image")
    public StretchMode getStretchMode() {
        return DebugBridge.value(this, "stretchMode", stretchMode);
    }

    public TextureRect setStretchMode(StretchMode mode) {
        Objects.requireNonNull(mode);
        if (DebugBridge.previewWrite(this, "stretchMode", mode)) return this;
        if (stretchMode != mode) {
            stretchMode = mode;
            clearGeometry();
        }
        return this;
    }

    @Inspectable(group = "Image")
    public ExpandMode getExpandMode() {
        return DebugBridge.value(this, "expandMode", expandMode);
    }

    public TextureRect setExpandMode(ExpandMode mode) {
        Objects.requireNonNull(mode);
        if (DebugBridge.previewWrite(this, "expandMode", mode)) return this;
        if (expandMode != mode) {
            expandMode = mode;
            requestLayout();
        }
        return this;
    }

    @Inspectable(group = "Image")
    public boolean getFlipHorizontal() {
        return DebugBridge.value(this, "flipHorizontal", flipHorizontal);
    }

    public TextureRect setFlipHorizontal(boolean flip) {
        if (DebugBridge.previewWrite(this, "flipHorizontal", flip)) return this;
        if (flipHorizontal != flip) {
            flipHorizontal = flip;
            clearGeometry();
        }
        return this;
    }

    @Inspectable(group = "Image")
    public boolean getFlipVertical() {
        return DebugBridge.value(this, "flipVertical", flipVertical);
    }

    public TextureRect setFlipVertical(boolean flip) {
        if (DebugBridge.previewWrite(this, "flipVertical", flip)) return this;
        if (flipVertical != flip) {
            flipVertical = flip;
            clearGeometry();
        }
        return this;
    }

    @Inspectable(group = "Image", min = 0, max = 1)
    public float getOpacity() {
        return DebugBridge.value(this, "opacity", opacity);
    }

    public TextureRect setOpacity(float opacity) {
        if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1)
            throw new IllegalArgumentException("Opacity must be between 0 and 1");
        if (DebugBridge.previewWrite(this, "opacity", opacity)) return this;
        this.opacity = opacity;
        return this;
    }

    @Inspectable(group = "Image")
    public int getModulate() {
        return DebugBridge.value(this, "modulate", modulate);
    }

    public TextureRect setModulate(int color) {
        if (DebugBridge.previewWrite(this, "modulate", color)) return this;
        modulate = color;
        return this;
    }

    @Override
    public int getMinimumWidth() {
        TextureRegion region = getResolvedTextureRegion();
        return Math.max(super.getMinimumWidth(), getExpandMode() == ExpandMode.KEEP_SIZE && region != null ? region.getWidth() : 0);
    }

    @Override
    public int getMinimumHeight() {
        TextureRegion region = getResolvedTextureRegion();
        return Math.max(super.getMinimumHeight(), getExpandMode() == ExpandMode.KEEP_SIZE && region != null ? region.getHeight() : 0);
    }

    @Override
    public int measureWidth(net.minecraft.client.gui.Font font, int available) {
        return isAlive() ? Math.max(getMinimumWidth(), super.measureWidth(font, available)) : 0;
    }

    @Override
    public int measureHeight(net.minecraft.client.gui.Font font, int available) {
        return isAlive() ? Math.max(getMinimumHeight(), super.measureHeight(font, available)) : 0;
    }

    @Override
    public boolean hit(int x, int y) {
        return false;
    }

    @Override
    protected void validateChildren(List<Control> children) {
        if (!children.isEmpty()) throw new IllegalStateException("TextureRect is a leaf control");
    }

    @Override
    protected void notification(ControlNotification notification) {
        if (notification.type() == ControlNotification.Type.THEME_CHANGED) clearGeometry();
        if (notification.type() == ControlNotification.Type.PRE_FREE) {
            texture = null;
            clearGeometry();
        }
    }

    private void clearGeometry() {
        cachedArea = null;
        cachedRegion = null;
        cachedQuads = List.of();
    }

    /**
     * Cached paint geometry also supports diagnostics and CPU-only layout tests.
     */
    public List<TexturePainter.Quad> getDrawQuads(Rect area) {
        TextureRegion image = getResolvedTextureRegion();
        StretchMode mode = getStretchMode();
        boolean flipH = getFlipHorizontal(), flipV = getFlipVertical();
        if (Objects.equals(cachedArea, area) && Objects.equals(cachedRegion, image) && cachedMode == mode
                && cachedFlipH == flipH && cachedFlipV == flipV) return cachedQuads;
        cachedArea = area;
        cachedRegion = image;
        cachedMode = mode;
        cachedFlipH = flipH;
        cachedFlipV = flipV;
        if (image == null || area.width() == 0 || area.height() == 0) return cachedQuads = List.of();
        List<TexturePainter.Quad> quads = new ArrayList<>();
        int iw = image.getWidth(), ih = image.getHeight();
        if (mode == StretchMode.TILE) {
            for (int y = 0; y < area.height(); y += ih)
                for (int x = 0; x < area.width(); x += iw) {
                    int w = Math.min(iw, area.width() - x), h = Math.min(ih, area.height() - y);
                    addQuad(quads, image, area.x() + x, area.y() + y, w, h, 0, 0, w, h, flipH, flipV);
                }
        } else if (mode == StretchMode.KEEP_ASPECT_COVERED) {
            double scale = Math.max((double) area.width() / iw, (double) area.height() / ih);
            float sw = (float) (area.width() / scale), sh = (float) (area.height() / scale);
            addQuad(quads, image, area.x(), area.y(), area.width(), area.height(), (iw - sw) / 2, (ih - sh) / 2, sw, sh, flipH, flipV);
        } else {
            int width = area.width(), height = area.height();
            if (mode == StretchMode.KEEP || mode == StretchMode.KEEP_CENTERED) {
                width = iw;
                height = ih;
            }
            if (mode == StretchMode.KEEP_ASPECT || mode == StretchMode.KEEP_ASPECT_CENTERED) {
                double scale = Math.min((double) width / iw, (double) height / ih);
                width = Math.max(1, Math.min(area.width(), (int) Math.round(iw * scale)));
                height = Math.max(1, Math.min(area.height(), (int) Math.round(ih * scale)));
            }
            boolean centered = mode == StretchMode.KEEP_CENTERED || mode == StretchMode.KEEP_ASPECT_CENTERED;
            addQuad(quads, image, area.x() + (centered ? (area.width() - width) / 2 : 0), area.y() + (centered ? (area.height() - height) / 2 : 0),
                    width, height, 0, 0, iw, ih, flipH, flipV);
        }
        return cachedQuads = List.copyOf(quads);
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        TextureRegion image = getResolvedTextureRegion();
        if (image != null)
            TexturePainter.draw(context, image, getDrawQuads(getScreenBounds(screenX, screenY)), getModulate(), getOpacity());
    }

    @Override
    public void debugProperties(List<DebugProperty> out) {
        super.debugProperties(out);
        out.add(DebugProperty.number(this, "modulate", "Image", this::getModulate, Integer.MIN_VALUE, 0xFFFFFFFFL));
    }

    public enum StretchMode {SCALE, TILE, KEEP, KEEP_CENTERED, KEEP_ASPECT, KEEP_ASPECT_CENTERED, KEEP_ASPECT_COVERED}

    public enum ExpandMode {KEEP_SIZE, IGNORE_SIZE}
}
