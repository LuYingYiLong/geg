package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.util.OreFonts;
import io.github.luyingyilong.geg.client.ui.util.OreSounds;
import io.github.luyingyilong.geg.client.ui.util.ReleaseGesture;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.FocusManager;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.function.IntConsumer;

/** A single horizontal row of equally sized tabs. */
public final class Tabs extends Control {
    private static final int SELECTED_TOP_INSET = 2;
    private static final int INDICATOR_MAX_WIDTH = 32;
    private static final int TEXT_PADDING = 4;

    private final List<Component> labels;
    private final Button.Size size;
    private int selectedIndex;
    private int focusedIndex;
    private boolean focused;
    private int scrollX;
    private boolean revealSelected = true;
    private int lastViewportWidth = -1;
    private IntConsumer onSelect = index -> { };
    private Font layoutFont;
    private final ReleaseGesture<Integer> gesture = new ReleaseGesture<>();

    public Tabs(List<Component> labels) {
        this(labels, Button.Size.MEDIUM);
    }

    public Tabs(List<Component> labels, Button.Size size) {
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("Tabs require at least one label");
        }
        this.labels = List.copyOf(labels);
        this.size = java.util.Objects.requireNonNull(size);
        minimum(0, size.height());
        focusable(true);
        focusExpansion(1);
    }

    public Tabs onSelect(IntConsumer onSelect) {
        this.onSelect = java.util.Objects.requireNonNull(onSelect);
        return this;
    }

    public Tabs selected(int index) {
        int next = Math.max(0, Math.min(labels.size() - 1, index));
        if (next != selectedIndex) {
            cancelInteraction();
            selectedIndex = next;
            focusedIndex = next;
            revealSelected = true;
        } else if (!focused) focusedIndex = next;
        return this;
    }

    public int selected() { return selectedIndex; }
    public int scrollX() { return scrollX; }

    @Override public void invalidateTextLayout() {
        revealSelected = true;
        super.invalidateTextLayout();
    }

    @Override public Rect focusBounds() {
        Rect area = absoluteBounds();
        int width = layoutFont == null ? area.width()
                : Math.max(area.width(), minimumRowWidth(layoutFont));
        int index = focused ? focusedIndex : selectedIndex;
        int left = index * width / labels.size();
        int right = (index + 1) * width / labels.size();
        int inset = topInset(index, true);
        return new Rect(area.x() + left - scrollX, area.y() + inset,
                right - left + 1, Math.max(0, size.height() - inset));
    }

    @Override public void onFocusChanged(boolean focused) {
        this.focused = focused;
        if (!focused) cancelInteraction();
        if (focused) {
            focusedIndex = selectedIndex;
            revealSelected = true;
        }
    }

    @Override public boolean onFocusMove(FocusManager.Direction direction) {
        int next = switch (direction) {
            case LEFT -> Math.max(0, focusedIndex - 1);
            case RIGHT -> Math.min(labels.size() - 1, focusedIndex + 1);
            default -> focusedIndex;
        };
        if (next == focusedIndex) return false;
        cancelInteraction();
        focusedIndex = next;
        revealSelected = true;
        if (layoutFont != null && bounds().width() > 0)
            ensureSelectedVisible(Math.max(bounds().width(), minimumRowWidth(layoutFont)), bounds().width());
        return true;
    }

    @Override public boolean activate() {
        return selectFromInput(focusedIndex);
    }

    private boolean selectFromInput(int index) {
        if (!visible()) return false;
        OreSounds.play(OreSounds.Cue.CLICK);
        selected(index);
        focusedIndex = index;
        onSelect.accept(selectedIndex);
        return true;
    }

    public int minimumRowWidth(Font font) {
        int width = 0;
        for (Component label : labels)
            width += Math.max(24, font.width(OreFonts.text(label)) + 2 * TEXT_PADDING + 8);
        return width;
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        layoutFont = font;
        if (lastViewportWidth != bounds.width()) {
            lastViewportWidth = bounds.width();
            revealSelected = true;
        }
        if (font != null && revealSelected && bounds.width() > 0) {
            int contentWidth = Math.max(bounds.width(), minimumRowWidth(font));
            ensureSelectedVisible(contentWidth, bounds.width());
            revealSelected = false;
        }
    }

    @Override protected boolean onPointerPressed(int rootX, int rootY) {
        if (layoutFont == null || clickAt(0, 0, rootX, rootY, layoutFont) < 0) return false;
        capturePointer();
        return true;
    }

    @Override public boolean releasePointer(int rootX, int rootY, boolean over) {
        int index = layoutFont == null ? -1 : indexAt(0, 0, rootX, rootY, layoutFont);
        var release = gesture.pointerUp(index < 0 ? null : index, over && visible());
        if (release.target() != null) selectFromInput(release.target());
        return release.handled();
    }

    @Override public boolean onActivationPressed(int keyCode) {
        if (!visible()) return false;
        gesture.keyDown(keyCode, focusedIndex);
        return true;
    }

    @Override public boolean onActivationReleased(int keyCode) {
        var release = gesture.keyUp(keyCode, visible() ? focusedIndex : null);
        if (release.target() != null) selectFromInput(release.target());
        return release.handled();
    }

    @Override public void cancelInteraction() { gesture.cancel(); }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect viewport = screenBounds(screenX, screenY);
        if (viewport.width() <= 0 || viewport.height() <= 0) return;
        int contentWidth = Math.max(viewport.width(), minimumRowWidth(context.getFont()));
        scrollX = Math.min(scrollX, contentWidth - viewport.width());
        if (revealSelected) {
            ensureSelectedVisible(contentWidth, viewport.width());
            revealSelected = false;
        }
        GuiGraphics graphics = context.getGraphics();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        draw(context, viewport.x() - scrollX, viewport.y(), contentWidth, selectedIndex);
        graphics.disableScissor();
    }

    private void ensureSelectedVisible(int contentWidth, int viewportWidth) {
        int index = focused ? focusedIndex : selectedIndex;
        int left = index * contentWidth / labels.size();
        int right = (index + 1) * contentWidth / labels.size();
        if (left < scrollX) scrollX = left;
        else if (right > scrollX + viewportWidth) scrollX = right - viewportWidth;
        scrollX = Math.max(0, Math.min(contentWidth - viewportWidth, scrollX));
    }

    /** Arms the tab under the pointer; selection is deferred until a matching release. */
    public int clickAt(int screenX, int screenY, int mouseX, int mouseY, Font font) {
        int index = indexAt(screenX, screenY, mouseX, mouseY, font);
        if (index >= 0) gesture.pointerDown(index);
        return index;
    }

    private int indexAt(int screenX, int screenY, int mouseX, int mouseY, Font font) {
        Rect viewport = screenBounds(screenX, screenY);
        if (!viewport.contains(mouseX, mouseY)) return -1;
        int contentWidth = Math.max(viewport.width(), minimumRowWidth(font));
        return hitIndex(viewport.x() - scrollX, viewport.y(), contentWidth, mouseX, mouseY);
    }

    public boolean scrollBy(int amount, Font font) {
        int max = Math.max(0, minimumRowWidth(font) - bounds().width());
        if (max == 0) return false;
        cancelInteraction();
        scrollX = Math.max(0, Math.min(max, scrollX + amount));
        return true;
    }

    public void draw(ModularGuiContext context, int x, int y,
                     int width, int selectedIndex) {
        int height = size.height();
        GuiGraphics graphics = context.getGraphics();
        WidgetThemeEntry<WidgetTheme> selectedEntry = context.getTheme()
                .getWidgetTheme(OreTheme.TAB_BUTTON_SELECTED);
        int hoveredIndex = hitIndex(x, y, width,
                context.getAbsMouseX(), context.getAbsMouseY());

        for (int index = 0; index < labels.size(); index++) {
            int tabX = x + index * width / labels.size();
            int tabEnd = x + (index + 1) * width / labels.size();
            // Overlap the adjacent 1px texture borders without changing the row's outer width.
            int tabWidth = tabEnd - tabX + (index < labels.size() - 1 ? 1 : 0);
            boolean selected = index == selectedIndex;
            boolean hovered = index == hoveredIndex;
            int topInset = topInset(index, hovered);
            WidgetTheme style = selected && selectedEntry != null ? selectedEntry.theme()
                    : Button.theme(context.getTheme(), Button.Type.SECONDARY,
                    hovered, gesture.keyPressed(index) || hovered && gesture.pointerPressed(index));
            IDrawable background = style.getBackground();
            if (background != null) {
                int inset = Math.min(topInset, height);
                background.draw(context, tabX, y + inset,
                        tabWidth, height - inset, style);
            }

            Label.draw(context, labels.get(index), new io.github.luyingyilong.geg.client.ui.layout.Rect(
                            tabX + TEXT_PADDING, y + topInset,
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
                if (index == selectedIndex && mouseY < y + SELECTED_TOP_INSET) return -1;
                return index;
            }
        }
        return -1;
    }

    int topInset(int index, boolean hovered) {
        return index == selectedIndex || gesture.keyPressed(index)
                || hovered && gesture.pointerPressed(index) ? SELECTED_TOP_INSET : 0;
    }
}
