package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.theme.ThemeKey;
import io.github.luyingyilong.ore.theme.ThemePadding;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Non-interactive right-side indicator: null count is a dot, including zero displays a number.
 */
public final class Badge extends Container {
    private static final ThemePadding PADDING = ThemePadding.of("Badge", "", 1, 2, 1, 2);
    public static final ThemeKey<Integer> PADDING_TOP = PADDING.top();
    public static final ThemeKey<Integer> PADDING_RIGHT = PADDING.right();
    public static final ThemeKey<Integer> PADDING_BOTTOM = PADDING.bottom();
    public static final ThemeKey<Integer> PADDING_LEFT = PADDING.left();

    public static final int DOT_SIZE = 3;
    public static final int MIN_COUNT_SIZE = 12;
    public static final int SHADOW_HEIGHT = 1;
    // Mojangles digits are seven pixels high; their advance includes one trailing spacing pixel.
    private static final int TRAILING_SPACING = 1;
    private final Label label = ownPart("label", new Label().setOpticalCenter(true).setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER));
    private Long count;
    private long overflowCount = 99L;
    private Tag.Type type = Tag.Type.PRIMARY;

    public Badge() {
        setMinimum(DOT_SIZE, DOT_SIZE + SHADOW_HEIGHT);
        updateChildren(java.util.List.of(label));
        label.setVisible(false);
        setType(type);
        setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
    }

    public Badge(long count) {
        this();
        setCount(count);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Badge");
        PADDING.register(db);
    }

    @Override
    protected void validateChildren(java.util.List<io.github.luyingyilong.ore.layout.Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Badge has a fixed skeleton; configure its properties");
    }

    public Long getCount() {
        return count;
    }

    /**
     * Null restores dot mode. Negative counts are rejected.
     */
    public Badge setCount(Long count) {
        if (count != null && count < 0L) throw new IllegalArgumentException("Badge count cannot be negative");
        if (!Objects.equals(this.count, count)) {
            this.count = count;
            updateText();
            requestLayout();
        }
        return this;
    }

    public Badge setCount(long count) {
        return setCount(Long.valueOf(count));
    }

    public long getOverflowCount() {
        return overflowCount;
    }

    public Badge setOverflowCount(long overflowCount) {
        if (overflowCount <= 0L) throw new IllegalArgumentException("Badge overflow count must be positive");
        if (this.overflowCount != overflowCount) {
            this.overflowCount = overflowCount;
            updateText();
            requestLayout();
        }
        return this;
    }

    public Tag.Type getType() {
        return type;
    }

    public Badge setType(Tag.Type type) {
        this.type = Objects.requireNonNull(type);
        label.setThemeTypeVariation(type.textColor() == OreTheme.TEXT_DARK ? "LabelDark" : "");
        return this;
    }

    public String getDisplayText() {
        return count == null ? "" : count > overflowCount ? overflowCount + "+" : Long.toString(count);
    }

    private void updateText() {
        int side = count == null ? DOT_SIZE : MIN_COUNT_SIZE;
        setMinimum(side, side + SHADOW_HEIGHT);
        label.setText(Component.literal(getDisplayText()));
        label.setVisible(count != null);
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        int width = DOT_SIZE, height = DOT_SIZE;
        if (count != null) {
            var padding = PADDING.getInsets(this);
            width = Math.max(MIN_COUNT_SIZE, label.measureWidth(font, Integer.MAX_VALUE) + padding.getHorizontal());
            height = Math.max(MIN_COUNT_SIZE, label.lineHeight(font) + padding.getVertical());
        }
        return new Size(Math.max(super.measureWidth(font, available.width()), width),
                Math.max(super.measureHeight(font, available.height()), height + SHADOW_HEIGHT));
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).width();
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return measureSize(font, new Size(available, available)).height();
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            Rect face = faceBounds(new Rect(0, 0, bounds.width(), bounds.height()));
            Rect content = PADDING.getInsets(this).inset(face);
            int advance = label.measureWidth(font, Integer.MAX_VALUE);
            int inkWidth = Math.max(0, advance - TRAILING_SPACING);
            // Center the visible number, excluding both the line's leading and the badge's shadow.
            layoutChild(label, new Rect(content.x() + Math.round((content.width() - inkWidth) / 2.0F),
                    content.y(), advance, content.height()), font);
        }
    }

    Rect faceBounds(Rect area) {
        return new Rect(area.x(), area.y(), area.width(), Math.max(0, area.height() - SHADOW_HEIGHT));
    }

    Rect shadowBounds(Rect area) {
        return new Rect(area.x(), Math.max(area.y(), area.bottom() - SHADOW_HEIGHT), area.width(), Math.min(SHADOW_HEIGHT, area.height()));
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        if (area.width() <= 0 || area.height() <= 0) return;
        Rect face = faceBounds(area), shadow = shadowBounds(area);
        var graphics = context.getGraphics();
        graphics.fill(shadow.x(), shadow.y(), shadow.right(), shadow.bottom(), getThemeItem(OreTheme.TEXT_DARK_KEY));
        graphics.fill(face.x(), face.y(), face.right(), face.bottom(), type.getColor(this));
    }

    /**
     * Decoration never steals pointer or keyboard input from the owning button.
     */
    @Override
    protected Control hitTestTree(int rootX,int rootY,int windowX,int windowY) {
        return null;
    }

}
