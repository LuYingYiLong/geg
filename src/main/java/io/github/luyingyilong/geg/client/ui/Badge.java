package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Non-interactive right-side indicator: null count is a dot, including zero displays a number. */
public final class Badge extends Container {
    public static final int DOT_SIZE = 3;
    public static final int MIN_COUNT_SIZE = 12;
    public static final int SHADOW_HEIGHT = 1;
    private static final int PADDING_X = 2;
    // Mojangles digits are seven pixels high; their advance includes one trailing spacing pixel.
    private static final int DIGIT_HEIGHT = 7;
    private static final int TRAILING_SPACING = 1;
    private final Label label = new Label();
    private Long count;
    private long overflowCount = 99L;
    private Tag.Type type = Tag.Type.PRIMARY;

    public Badge() {
        minimum(DOT_SIZE, DOT_SIZE + SHADOW_HEIGHT);
        addChild(label);
        label.visible(false);
        type(type);
        sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
    }
    public Badge(long count) { this(); count(count); }

    /** Null restores dot mode. Negative counts are rejected. */
    public Badge count(Long count) {
        if (count != null && count < 0L) throw new IllegalArgumentException("Badge count cannot be negative");
        if (!Objects.equals(this.count, count)) {
            this.count = count;
            updateText();
            requestLayout();
        }
        return this;
    }
    public Badge count(long count) { return count(Long.valueOf(count)); }
    public Long count() { return count; }
    public Badge overflowCount(long overflowCount) {
        if (overflowCount <= 0L) throw new IllegalArgumentException("Badge overflow count must be positive");
        if (this.overflowCount != overflowCount) {
            this.overflowCount = overflowCount;
            updateText();
            requestLayout();
        }
        return this;
    }
    public long overflowCount() { return overflowCount; }
    public Badge type(Tag.Type type) {
        this.type = Objects.requireNonNull(type);
        label.color(type.textColor());
        return this;
    }
    public Tag.Type type() { return type; }
    public String displayText() {
        return count == null ? "" : count > overflowCount ? overflowCount + "+" : Long.toString(count);
    }
    private void updateText() {
        int side = count == null ? DOT_SIZE : MIN_COUNT_SIZE;
        minimum(side, side + SHADOW_HEIGHT);
        label.text(Component.literal(displayText()));
        label.visible(count != null);
    }

    @Override public Size measureSize(Font font, Size available) {
        int width = DOT_SIZE, height = DOT_SIZE;
        if (count != null) {
            width = Math.max(MIN_COUNT_SIZE, label.measureWidth(font, Integer.MAX_VALUE) + 2 * PADDING_X);
            height = Math.max(MIN_COUNT_SIZE, label.lineHeight(font) + 2);
        }
        return new Size(Math.max(super.measureWidth(font, available.width()), width),
                Math.max(super.measureHeight(font, available.height()), height + SHADOW_HEIGHT));
    }
    @Override public int measureWidth(Font font, int available) { return measureSize(font, new Size(available, available)).width(); }
    @Override public int measureHeight(Font font, int available) { return measureSize(font, new Size(available, available)).height(); }
    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        Rect face = faceBounds(new Rect(0, 0, bounds.width(), bounds.height()));
        int advance = label.measureWidth(font, Integer.MAX_VALUE);
        int inkWidth = Math.max(0, advance - TRAILING_SPACING);
        // Center the visible number, excluding both the line's leading and the badge's shadow.
        layoutChild(label, new Rect(Math.round((face.width() - inkWidth) / 2.0F),
                Math.round((face.height() - DIGIT_HEIGHT) / 2.0F), advance, label.lineHeight(font)), font);
    }
    Rect faceBounds(Rect area) { return new Rect(area.x(), area.y(), area.width(), Math.max(0, area.height() - SHADOW_HEIGHT)); }
    Rect shadowBounds(Rect area) { return new Rect(area.x(), Math.max(area.y(), area.bottom() - SHADOW_HEIGHT), area.width(), Math.min(SHADOW_HEIGHT, area.height())); }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = screenBounds(screenX, screenY);
        if (area.width() <= 0 || area.height() <= 0) return;
        Rect face = faceBounds(area), shadow = shadowBounds(area);
        var graphics = context.getGraphics();
        graphics.fill(shadow.x(), shadow.y(), shadow.right(), shadow.bottom(), OreTheme.TEXT_DARK);
        graphics.fill(face.x(), face.y(), face.right(), face.bottom(), type.color());
    }

    /** Decoration never steals pointer or keyboard input from the owning button. */
    @Override public Control hitTop(int rootX, int rootY) { return null; }
    @Override public boolean dispatchPointer(int rootX, int rootY) { return false; }
}
