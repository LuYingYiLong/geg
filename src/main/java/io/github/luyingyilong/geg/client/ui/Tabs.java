package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** A single horizontal row of equally sized tabs. */
public final class Tabs {
    private static final int SELECTED_TOP_INSET = 2;
    private static final int INDICATOR_MAX_WIDTH = 64;
    private static final int TEXT_PADDING = 4;

    private final List<Component> labels;
    private final Button.Size size;

    public Tabs(List<Component> labels) {
        this(labels, Button.Size.MEDIUM);
    }

    public Tabs(List<Component> labels, Button.Size size) {
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("Tabs require at least one label");
        }
        this.labels = List.copyOf(labels);
        this.size = java.util.Objects.requireNonNull(size);
    }

    public void draw(ModularGuiContext context, int x, int y,
                     int width, int selectedIndex) {
        int height = size.height();
        GuiGraphics graphics = context.getGraphics();
        WidgetThemeEntry<WidgetTheme> normalEntry = context.getTheme()
                .getWidgetTheme(OreTheme.TAB_BUTTON);
        WidgetThemeEntry<WidgetTheme> selectedEntry = context.getTheme()
                .getWidgetTheme(OreTheme.TAB_BUTTON_SELECTED);
        WidgetThemeEntry<WidgetTheme> fallback = context.getTheme().getButtonTheme();
        int hoveredIndex = hitIndex(x, y, width,
                context.getAbsMouseX(), context.getAbsMouseY());

        for (int index = 0; index < labels.size(); index++) {
            int tabX = x + index * width / labels.size();
            int tabEnd = x + (index + 1) * width / labels.size();
            // Overlap the adjacent 1px texture borders without changing the row's outer width.
            int tabWidth = tabEnd - tabX + (index < labels.size() - 1 ? 1 : 0);
            boolean selected = index == selectedIndex;
            boolean hovered = index == hoveredIndex;
            WidgetThemeEntry<WidgetTheme> entry = selected && selectedEntry != null
                    ? selectedEntry : normalEntry != null ? normalEntry : fallback;
            WidgetTheme style = selected ? entry.theme() : entry.getTheme(hovered);
            IDrawable background = style.getBackground();
            if (background != null) {
                int inset = selected ? Math.min(SELECTED_TOP_INSET, height) : 0;
                background.draw(context, tabX, y + inset,
                        tabWidth, height - inset, style);
            }

            Label.draw(context, labels.get(index), new io.github.luyingyilong.geg.client.ui.layout.Rect(
                            tabX + TEXT_PADDING, y + (selected ? SELECTED_TOP_INSET : 0),
                            Math.max(0, tabWidth - 2 * TEXT_PADDING), height),
                    style.getTextColor(), style.isTextShadow(), Label.Overflow.CLIP,
                    Label.Horizontal.CENTER, Label.Vertical.CENTER);

            if (selected && tabWidth > 0 && height > 0) {
                int lineWidth = Math.min(INDICATOR_MAX_WIDTH, Math.max(1, tabWidth / 3));
                int lineX = tabX + (tabWidth - lineWidth) / 2;
                graphics.fill(lineX, y + height - 2,
                        lineX + lineWidth, y + height - 1, 0xFFFFFFFF);
            }
        }
    }

    public int hitIndex(int x, int y, int width, int mouseX, int mouseY) {
        int height = size.height();
        if (!Button.contains(x, y, width, height, mouseX, mouseY)) {
            return -1;
        }
        for (int index = 0; index < labels.size(); index++) {
            int left = x + index * width / labels.size();
            int right = x + (index + 1) * width / labels.size();
            if (mouseX >= left && mouseX < right) {
                return index;
            }
        }
        return -1;
    }
}
