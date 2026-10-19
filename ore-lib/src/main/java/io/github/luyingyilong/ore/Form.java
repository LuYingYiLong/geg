package io.github.luyingyilong.ore;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.input.NumberInput;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import net.minecraft.client.gui.Font;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Named values, submit validation, and field layout share one Ore node. */
public final class Form extends Container {
    public enum Layout { VERTICAL, HORIZONTAL }

    private final Map<String, FormItem> items = new LinkedHashMap<>();
    private Layout layout = Layout.VERTICAL;
    private boolean shown;
    private int lastBottom;

    public Form() {
        visible(false);
    }

    public Form layout(Layout layout) {
        this.layout = Objects.requireNonNull(layout);
        requestLayout();
        return this;
    }

    public static Layout effectiveLayout(Layout chosen, int viewportWidth) {
        return chosen == Layout.HORIZONTAL && viewportWidth <= 128 ? Layout.VERTICAL : chosen;
    }

    public Form add(FormItem item) {
        if (items.containsKey(item.name())) throw new IllegalArgumentException("Duplicate field: " + item.name());
        items.put(item.name(), item);
        addChild(item);
        item.setInputVisible(shown && item.active());
        return this;
    }

    public void setVisible(boolean visible) {
        shown = visible;
        super.visible(visible);
        for (FormItem item : items.values()) item.setInputVisible(visible && item.active());
    }

    @Override public boolean visible() { return shown; }
    public int bottom() { return lastBottom; }

    public String value(String name) {
        FormItem item = items.get(name);
        if (item == null) throw new IllegalArgumentException("Unknown field: " + name);
        return item.value();
    }

    /** MUI widgets are siblings of the Ore canvas, so hide fields outside a scrolled viewport. */
    public void clipInputs(Rect viewport) {
        for (FormItem item : items.values()) {
            if (item.inputNode() == null) continue;
            Rect area = item.inputNode().absoluteBounds();
            boolean fullyVisible = area.x() >= viewport.x() && area.right() <= viewport.right()
                    && area.y() >= viewport.y() && area.bottom() <= viewport.bottom();
            item.setInputVisible(shown && item.active() && fullyVisible);
        }
    }

    public boolean validate() {
        if (!shown) return false;
        for (FormItem item : items.values()) {
            if (item.active() && item.input() instanceof NumberInput number && !number.commit()) {
                requestLayout();
                return false;
            }
        }
        requestLayout();
        return true;
    }

    @Override public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        boolean horizontal = effectiveLayout(layout, available.width()) == Layout.HORIZONTAL;
        int height = 0;
        for (FormItem item : items.values()) if (item.active()) {
            item.horizontal(horizontal);
            height += item.rowHeight(font, available.width());
        }
        return new Size(Math.max(minimumWidth(), available.width()),
                Math.max(minimumHeight(), height));
    }

    @Override public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            boolean horizontal = effectiveLayout(layout, bounds.width()) == Layout.HORIZONTAL;
            int top = 0;
            for (FormItem item : items.values()) {
                item.horizontal(horizontal);
                item.visible(item.active());
                if (!item.active()) continue;
                int height = item.rowHeight(font, bounds.width());
                fitChild(item, new Rect(0, top, bounds.width(), height), font);
                top += height;
            }
            lastBottom = bounds.y() + top;
        }
    }

    /** Compatibility entry point until a modal directly owns the Form node. */
    public int draw(ModularGuiContext context, int x, int y, int width, int viewportWidth) {
        if (!shown) return y;
        Font font = context.getFont();
        int height = measureSize(font, new Size(width, viewportWidth)).height();
        layout(new Rect(x, y, width, height), font);
        super.draw(context, 0, 0);
        return lastBottom;
    }
}
