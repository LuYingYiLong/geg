package io.github.luyingyilong.ore.control.layout;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.StyleBox;
import io.github.luyingyilong.ore.theme.ThemeInsets;
import io.github.luyingyilong.ore.theme.ThemeKey;


import io.github.luyingyilong.ore.render.DrawContext;
import net.minecraft.client.gui.Font;

/** Themed background; space allocation is inherited from Control. */
public final class Panel extends Container {
    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) { io.github.luyingyilong.ore.theme.OreTheme.registerType(db,"Panel"); }
    private final ThemeKey<StyleBox> themeKey;
    private Control child;

    public Panel() { this(OreTheme.PANEL); }
    public Panel(ThemeKey<StyleBox> themeKey) { this.themeKey = themeKey == null ? OreTheme.PANEL : themeKey; }
    public StyleBox getStyleBox() { return getThemeItem(themeKey); }
    public Panel setChild(Control child) {
        replaceChild(this.child, child);
        return this;
    }

    @Override protected void validateChildren(java.util.List<Control> proposed) {
        if (proposed.size() > 1) throw new IllegalStateException("Panel accepts one child; use setChild");
    }

    @Override protected void childrenChanged() {
        child = getChildCount() == 0 ? null : getChild(0);
    }

    @Override public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        ThemeInsets margin=getStyleBox().getContentMargin();
        Size childSize = child == null ? new Size(0, 0) : child.desiredSize(font, new Size(Math.max(0,available.width()-margin.getHorizontal()),Math.max(0,available.height()-margin.getVertical())));
        return new Size(Math.max(getMinimumWidth(), childSize.width()+margin.getHorizontal()),
                Math.max(getMinimumHeight(), childSize.height()+margin.getVertical()));
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(localBounds, font);
            if (child != null) fitChild(child, getStyleBox().getContentRect(new Rect(0,0,localBounds.width(),localBounds.height())), font);
        }
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        getStyleBox().draw(context, getScreenBounds(screenX, screenY));
    }
}
