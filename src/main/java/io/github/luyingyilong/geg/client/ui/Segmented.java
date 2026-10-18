package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.FocusManager;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.theme.OreFonts;
import io.github.luyingyilong.geg.client.ui.theme.OreSounds;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import io.github.luyingyilong.geg.client.ui.util.ReleaseGesture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/** Content-sized single-choice options; FILL/EXPAND distributes any additional width. */
public final class Segmented extends Control {
    private static final int SELECTED_TOP_INSET = 2;
    private static final int INDICATOR_MAX_WIDTH = 16;
    private static final int TEXT_PADDING = 4;

    private final List<Component> options;
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

    public Segmented(List<Component> options) {
        this(options, Button.Size.MEDIUM);
    }

    public Segmented(List<Component> options, Button.Size size) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("Segmented requires at least one option");
        }
        this.options = List.copyOf(options);
        this.size = java.util.Objects.requireNonNull(size);
        minimum(0, size.height());
        focusable(true);
        focusExpansion(1);
    }

    public Segmented onSelect(IntConsumer onSelect) {
        this.onSelect = java.util.Objects.requireNonNull(onSelect);
        return this;
    }

    public Segmented selected(int index) {
        int next = Math.max(0, Math.min(options.size() - 1, index));
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
        int width = Math.max(area.width(), minimumRowWidth(layoutFont));
        int index = focused ? focusedIndex : selectedIndex;
        int left = optionStart(index, width, layoutFont);
        int right = optionStart(index + 1, width, layoutFont);
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
            case RIGHT -> Math.min(options.size() - 1, focusedIndex + 1);
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
        for (Component label : options)
            width += optionWidth(label, font);
        return width;
    }

    @Override public int measureWidth(Font font, int available) {
        return Math.max(super.measureWidth(font, available), minimumRowWidth(font));
    }

    private static int optionWidth(Component option, Font font) {
        return Math.max(24, (font == null ? 0 : font.width(OreFonts.text(option))) + 2 * TEXT_PADDING + 8);
    }

    /** Shared by painting, pointer hits, focus outlines and scroll-to-selection. */
    private int optionStart(int index, int rowWidth, Font font) {
        int start = 0;
        for (int preceding = 0; preceding < index; preceding++) start += optionWidth(options.get(preceding), font);
        return start + Math.max(0, rowWidth - minimumRowWidth(font)) * index / options.size();
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
        drawOptions(context, viewport.x() - scrollX, viewport.y(), contentWidth, selectedIndex);
        graphics.disableScissor();
    }

    private void ensureSelectedVisible(int contentWidth, int viewportWidth) {
        int index = focused ? focusedIndex : selectedIndex;
        int left = optionStart(index, contentWidth, layoutFont);
        int right = optionStart(index + 1, contentWidth, layoutFont);
        if (right - left > viewportWidth) scrollX = left;
        else if (left < scrollX) scrollX = left;
        else if (right > scrollX + viewportWidth) scrollX = right - viewportWidth;
        scrollX = Math.max(0, Math.min(contentWidth - viewportWidth, scrollX));
    }

    /** Arms the option under the pointer; selection is deferred until a matching release. */
    public int clickAt(int screenX, int screenY, int mouseX, int mouseY, Font font) {
        int index = indexAt(screenX, screenY, mouseX, mouseY, font);
        if (index >= 0) gesture.pointerDown(index);
        return index;
    }

    private int indexAt(int screenX, int screenY, int mouseX, int mouseY, Font font) {
        Rect viewport = screenBounds(screenX, screenY);
        if (!viewport.contains(mouseX, mouseY)) return -1;
        int contentWidth = Math.max(viewport.width(), minimumRowWidth(font));
        return hitIndex(viewport.x() - scrollX, viewport.y(), contentWidth, mouseX, mouseY, font);
    }

    public boolean scrollBy(int amount, Font font) {
        int max = Math.max(0, minimumRowWidth(font) - bounds().width());
        if (max == 0) return false;
        cancelInteraction();
        scrollX = Math.max(0, Math.min(max, scrollX + amount));
        return true;
    }

    private void drawOptions(ModularGuiContext context, int x, int y,
                     int width, int selectedIndex) {
        int height = size.height();
        GuiGraphics graphics = context.getGraphics();
        WidgetThemeEntry<WidgetTheme> selectedEntry = context.getTheme()
                .getWidgetTheme(OreTheme.SEGMENTED_BUTTON_SELECTED);
        int hoveredIndex = hitIndex(x, y, width,
                context.getAbsMouseX(), context.getAbsMouseY(), context.getFont());

        for (int index = 0; index < options.size(); index++) {
            int optionX = x + optionStart(index, width, context.getFont());
            int optionEnd = x + optionStart(index + 1, width, context.getFont());
            // Overlap the adjacent 1px texture borders without changing the row's outer width.
            int optionWidth = optionEnd - optionX + (index < options.size() - 1 ? 1 : 0);
            boolean selected = index == selectedIndex;
            boolean hovered = index == hoveredIndex;
            int topInset = topInset(index, hovered);
            WidgetTheme style = selected && selectedEntry != null ? selectedEntry.theme()
                    : Button.theme(context.getTheme(), Button.Type.SECONDARY,
                    hovered, gesture.keyPressed(index) || hovered && gesture.pointerPressed(index));
            IDrawable background = style.getBackground();
            if (background != null) {
                int inset = Math.min(topInset, height);
                background.draw(context, optionX, y + inset,
                        optionWidth, height - inset, style);
            }

            Label text = new Label(options.get(index)).opticalCenter(true)
                    .color(style.getTextColor()).shadow(style.isTextShadow()).overflow(Label.Overflow.CLIP)
                    .align(Label.Horizontal.CENTER, Label.Vertical.CENTER);
            text.layout(new Rect(
                            optionX + TEXT_PADDING, y + topInset,
                            Math.max(0, optionWidth - 2 * TEXT_PADDING), height), context.getFont());
            text.draw(context, 0, 0);

            if (selected && optionWidth > 0 && height > 0) {
                int lineWidth = Math.min(INDICATOR_MAX_WIDTH, Math.max(1, optionWidth / 3));
                int lineX = optionX + (optionWidth - lineWidth) / 2;
                graphics.fill(lineX, y + height - 2,
                        lineX + lineWidth, y + height - 1, OreTheme.TEXT_WHITE);
            }
        }
    }

    public int hitIndex(int x, int y, int width, int mouseX, int mouseY) {
        return hitIndex(x, y, width, mouseX, mouseY, layoutFont);
    }

    private int hitIndex(int x, int y, int width, int mouseX, int mouseY, Font font) {
        int height = size.height();
        if (!Button.contains(x, y, width, height, mouseX, mouseY)) {
            return -1;
        }
        for (int index = 0; index < options.size(); index++) {
            int left = x + optionStart(index, width, font);
            int right = x + optionStart(index + 1, width, font);
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
