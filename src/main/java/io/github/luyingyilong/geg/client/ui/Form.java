package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** Small market form: shared field layout, named values, and submit validation. */
public final class Form {
    public enum Layout { VERTICAL, HORIZONTAL }

    private static final int LABEL_HEIGHT = 10;
    private static final int GAP = 3;
    private static final int ROW_GAP = 6;
    private static final int ERROR_HEIGHT = 10;
    private final Map<String, FormItem> items = new LinkedHashMap<>();
    private Layout layout = Layout.VERTICAL;
    private boolean visible;
    private int lastBottom;

    public Form layout(Layout layout) {
        this.layout = Objects.requireNonNull(layout);
        return this;
    }

    public static Layout effectiveLayout(Layout chosen, int viewportWidth) {
        return chosen == Layout.HORIZONTAL && viewportWidth <= 128 ? Layout.VERTICAL : chosen;
    }

    public Form add(FormItem item) {
        if (items.putIfAbsent(item.name(), item) != null) {
            throw new IllegalArgumentException("Duplicate field: " + item.name());
        }
        return this;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        for (FormItem item : items.values()) item.input().setInputVisible(visible && item.active());
    }

    public boolean visible() { return visible; }
    public int bottom() { return lastBottom; }

    public String value(String name) {
        FormItem item = items.get(name);
        if (item == null) throw new IllegalArgumentException("Unknown field: " + name);
        return item.value();
    }

    public boolean validate() {
        if (!visible) return false;
        for (FormItem item : items.values()) {
            if (item.active() && item.input() instanceof NumberInput number && !number.commit()) {
                return false;
            }
        }
        return true;
    }

    public int draw(ModularGuiContext context, int x, int y, int width, int viewportWidth) {
        if (!visible) return y;
        Font font = context.getFont();
        boolean horizontal = effectiveLayout(layout, viewportWidth) == Layout.HORIZONTAL;
        List<FormItem> active = items.values().stream().filter(FormItem::active).toList();
        Box rows = new Box();
        List<Control> rowSlots = new ArrayList<>(active.size());
        int totalHeight = 0;
        for (FormItem item : active) {
            int rowHeight = (horizontal ? 0 : LABEL_HEIGHT + GAP)
                    + item.input().controlSize().height() + 1
                    + (item.error() == null ? 0 : ERROR_HEIGHT) + ROW_GAP;
            Control slot = new Control().minimum(0, rowHeight).preferred(0, rowHeight)
                    .policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
            rows.add(slot);
            rowSlots.add(slot);
            totalHeight += rowHeight;
        }
        rows.layout(new Rect(x, y, width, totalHeight), font);
        for (int index = 0; index < active.size(); index++) {
            FormItem item = active.get(index);
            Rect row = rowSlots.get(index).absoluteBounds();
            int labelWidth = horizontal ? Math.min(90, Math.max(36, width / 3)) : width;
            int controlX = horizontal ? row.x() + labelWidth + GAP : row.x();
            int controlY = horizontal ? row.y() : row.y() + LABEL_HEIGHT + GAP;
            int controlWidth = horizontal ? width - labelWidth - GAP : width;
            int errorY = controlY + item.input().controlSize().height() + 1;
            item.place(row.x(), row.y() + (horizontal ? 5 : 0), labelWidth,
                    controlX, controlY, controlWidth, errorY);
            Label.draw(context, item.label(), new Rect(
                            item.labelX(), item.labelY(), Math.max(0, labelWidth), LABEL_HEIGHT),
                    0xFFD8D9DC, Label.Overflow.CLIP,
                    Label.Horizontal.LEFT, Label.Vertical.TOP);
            Component error = item.error();
            if (error != null) {
                Label.draw(context, error, new Rect(
                                row.x(), item.errorY(), Math.max(0, width), ERROR_HEIGHT),
                        0xFFFF7777, Label.Overflow.CLIP,
                        Label.Horizontal.LEFT, Label.Vertical.TOP);
            }
        }
        lastBottom = y + totalHeight;
        return lastBottom;
    }
}
