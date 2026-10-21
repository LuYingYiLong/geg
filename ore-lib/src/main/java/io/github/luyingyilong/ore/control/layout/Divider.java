package io.github.luyingyilong.ore.control.layout;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.theme.OreTheme;

/**
 * A themed line: horizontal is 2px high, vertical is exactly 1px wide.
 */
public final class Divider extends Control {
    private final io.github.luyingyilong.ore.theme.ThemeKey<io.github.luyingyilong.ore.theme.StyleBox> horizontalTheme;
    private Layout layout = Layout.HORIZONTAL;

    public Divider() {
        this(OreTheme.H_DIVIDER);
    }

    public Divider(io.github.luyingyilong.ore.theme.ThemeKey<io.github.luyingyilong.ore.theme.StyleBox> theme) {
        this.horizontalTheme = theme == null ? OreTheme.PANEL : theme;
        setMinimum(0, 2);
        setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Divider");
    }

    public Layout getLayout() {
        return layout;
    }

    public Divider setLayout(Layout layout) {
        java.util.Objects.requireNonNull(layout);
        if (this.layout == layout) return this;
        this.layout = layout;
        if (layout == Layout.HORIZONTAL) {
            setMinimum(0, 2);
            setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        } else {
            setMinimum(1, 0);
            setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        }
        requestLayout();
        return this;
    }

    public int getThickness() {
        return layout == Layout.VERTICAL ? 1 : 2;
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        var area = getScreenBounds(screenX, screenY);
        if (layout == Layout.VERTICAL) {
            int width = Math.min(1, area.width());
            getThemeItem(OreTheme.V_DIVIDER).draw(context, new io.github.luyingyilong.ore.control.base.Rect(
                    area.x() + (area.width() - width) / 2, area.y(), width, area.height()));
        } else {
            int height = Math.min(2, area.height());
            getThemeItem(horizontalTheme).draw(context, new io.github.luyingyilong.ore.control.base.Rect(
                    area.x(), area.y() + (area.height() - height) / 2, area.width(), height));
        }
    }

    public enum Layout {HORIZONTAL, VERTICAL}
}
