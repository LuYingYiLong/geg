package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.input.NumberInput;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.render.DrawContext;
import net.minecraft.client.gui.Font;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Named values, submit validation, and field layout share one Ore node.
 */
public final class Form extends Container {
    private final Map<String, FormItem> items = new LinkedHashMap<>();
    private Layout layout = Layout.VERTICAL;
    private int lastBottom;
    public Form() {
        setVisible(false);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Form");
    }

    public static Layout effectiveLayout(Layout chosen, int viewportWidth) {
        return chosen == Layout.HORIZONTAL && viewportWidth <= 128 ? Layout.VERTICAL : chosen;
    }

    public Form setLayout(Layout layout) {
        this.layout = Objects.requireNonNull(layout);
        requestLayout();
        return this;
    }

    public Form addItem(FormItem item) {
        addChild(item);
        return this;
    }

    @Override
    protected void validateChildren(java.util.List<Control> proposed) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Control child : proposed) {
            if (!(child instanceof FormItem item))
                throw new IllegalArgumentException("Form accepts FormItem; use addItem");
            if (!names.add(item.getName())) throw new IllegalArgumentException("Duplicate field: " + item.getName());
        }
    }

    @Override
    protected void childrenChanged() {
        items.clear();
        for (Control child : getChildren())
            if (child instanceof FormItem item && item.isAlive()) {
                items.put(item.getName(), item);
                item.setInputVisible(getVisible() && item.getActive());
            }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        for (FormItem item : items.values()) item.setInputVisible(visible && item.getActive());
    }

    public int getBottom() {
        return lastBottom;
    }

    public String getValue(String name) {
        FormItem item = items.get(name);
        if (item == null) throw new IllegalArgumentException("Unknown field: " + name);
        return item.getValue();
    }

    /**
     * MUI widgets are siblings of the Ore canvas, so hide fields outside a scrolled viewport.
     */
    public void clipInputs(Rect viewport) {
        for (FormItem item : items.values()) {
            if (item.getInputNode() == null) continue;
            Rect area = item.getInputNode().getAbsoluteBounds();
            boolean fullyVisible = area.x() >= viewport.x() && area.right() <= viewport.right()
                    && area.y() >= viewport.y() && area.bottom() <= viewport.bottom();
            item.setInputVisible(getVisible() && item.getActive() && fullyVisible);
        }
    }

    public boolean validate() {
        if (!getVisible()) return false;
        for (FormItem item : items.values()) {
            if (item.getActive() && item.getInput() instanceof NumberInput number && !number.commit()) {
                requestLayout();
                return false;
            }
        }
        requestLayout();
        return true;
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        boolean horizontal = effectiveLayout(layout, available.width()) == Layout.HORIZONTAL;
        int height = 0;
        for (FormItem item : items.values())
            if (item.getActive()) {
                item.setHorizontal(horizontal);
                height += item.rowHeight(font, available.width());
            }
        return new Size(Math.max(getMinimumWidth(), available.width()),
                Math.max(getMinimumHeight(), height));
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            boolean horizontal = effectiveLayout(layout, bounds.width()) == Layout.HORIZONTAL;
            int top = 0;
            for (FormItem item : items.values()) {
                item.setHorizontal(horizontal);
                item.setVisible(item.getActive());
                if (!item.getActive()) continue;
                int height = item.rowHeight(font, bounds.width());
                fitChild(item, new Rect(0, top, bounds.width(), height), font);
                top += height;
            }
            lastBottom = bounds.y() + top;
        }
    }

    /**
     * Compatibility entry point until a modal directly owns the Form node.
     */
    public int draw(DrawContext context, int x, int y, int width, int viewportWidth) {
        if (!getVisible()) return y;
        Font font = context.getFont();
        int height = measureSize(font, new Size(width, viewportWidth)).height();
        layout(new Rect(x, y, width, height), font);
        super.draw(context, 0, 0);
        return lastBottom;
    }

    public enum Layout {VERTICAL, HORIZONTAL}
}
