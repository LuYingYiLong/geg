package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.*;

import io.github.luyingyilong.ore.debug.DebugBridge;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.FocusManager;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.theme.OreFonts;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.util.ReleaseGesture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Content-sized single-choice options; FILL/EXPAND distributes any additional width. */
public final class Segmented extends Control {
    private static final int SELECTED_TOP_INSET = 2;
    private static final int INDICATOR_MAX_WIDTH = 16;
    private static final int TEXT_PADDING = 4;

    private final List<Component> options;
    private final List<Label> optionLabels;
    private final Button.Size size;
    private int selectedIndex;
    private int focusedIndex;
    private boolean focused;
    private int scrollX;
    private boolean revealSelected = true;
    private int lastViewportWidth = -1;
    private final SignalSource<Integer> selectionChanged = new SignalSource<>(this, "selectionChanged", "Integer");
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
        optionLabels = options.stream().map(option -> new Label(option).setOpticalCenter(true)
                .setOverflow(Label.Overflow.CLIP).setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER)).toList();
        for (int i = 0; i < optionLabels.size(); i++) ownPart("option_" + i, optionLabels.get(i));
        this.size = java.util.Objects.requireNonNull(size);
        setMinimum(0, size.getHeight());
        setFocusable(true);
        setFocusExpansion(1);
    }

    public Signal<Integer> selectionChanged() { return selectionChanged.signal(); }

    public Segmented setSelected(int index) {
        int previous = selectedIndex;
        setSelectedNoSignal(index);
        if (previous != selectedIndex) selectionChanged.emit(selectedIndex);
        return this;
    }

    public Segmented setSelectedNoSignal(int index) {
        int next = Math.max(0, Math.min(options.size() - 1, index));
        if (next != selectedIndex) {
            cancelInteraction();
            selectedIndex = next;
            focusedIndex = next;
            revealSelected = true;
        } else if (!focused) focusedIndex = next;
        return this;
    }

    public int getSelected() { return selectedIndex; }

    public int getScrollX() { return scrollX; }

    @Override public void invalidateTextLayout() {
        revealSelected = true;
        super.invalidateTextLayout();
    }

    @Override public Rect getFocusBounds() {
        Rect area = getAbsoluteBounds();
        int width = Math.max(area.width(), minimumRowWidth(layoutFont));
        int index = focused ? focusedIndex : selectedIndex;
        int left = optionStart(index, width, layoutFont);
        int right = optionStart(index + 1, width, layoutFont);
        int inset = topInset(index, true);
        return new Rect(area.x() + left - scrollX, area.y() + inset,
                right - left + 1, Math.max(0, size.getHeight() - inset));
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
        if (layoutFont != null && getBounds().width() > 0)
            ensureSelectedVisible(Math.max(getBounds().width(), minimumRowWidth(layoutFont)), getBounds().width());
        return true;
    }

    @Override public boolean activate() {
        return selectFromInput(focusedIndex);
    }

    private boolean selectFromInput(int index) {
        if (!getVisible()) return false;
        OreSounds.play(OreSounds.Cue.CLICK);
        setSelected(index);
        focusedIndex = index;
        return true;
    }

    public int minimumRowWidth(Font font) {
        int width = 0;
        for (Component label : options)
            width += optionWidth(label, font);
        return width;
    }

    @Override public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
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
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
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
    }

    @Override protected boolean wheel(int x,int y,double delta,boolean horizontal) {
        return getWindowContext().getMui()!=null && scrollBy(-(int)Math.signum(delta)*24,getWindowContext().getMui().getFont());
    }

    @Override protected boolean pointerDown(int rootX, int rootY) {
        if (layoutFont == null || beginAt(0, 0, rootX, rootY, layoutFont) < 0) return false;

        return true;
    }

    @Override protected boolean pointerUp(int rootX, int rootY, boolean over) {
        int index = layoutFont == null ? -1 : indexAt(0, 0, rootX, rootY, layoutFont);
        var release = gesture.pointerUp(index < 0 ? null : index, over && getVisible());
        if (release.target() != null) selectFromInput(release.target());
        return release.handled();
    }

    @Override public boolean onActivationPressed(int keyCode) {
        if (!getVisible()) return false;
        gesture.keyDown(keyCode, focusedIndex);
        return true;
    }

    @Override public boolean onActivationReleased(int keyCode) {
        var release = gesture.keyUp(keyCode, getVisible() ? focusedIndex : null);
        if (release.target() != null) selectFromInput(release.target());
        return release.handled();
    }

    @Override public void cancelInteraction() { gesture.cancel(); }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect viewport = getScreenBounds(screenX, screenY);
        if (viewport.width() <= 0 || viewport.height() <= 0) return;
        int contentWidth = Math.max(viewport.width(), minimumRowWidth(context.getFont()));
        scrollX = Math.min(scrollX, contentWidth - viewport.width());
        if (revealSelected) {
            ensureSelectedVisible(contentWidth, viewport.width());
            revealSelected = false;
        }
        GuiGraphics graphics = context.getGraphics();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        DebugBridge.backend().clip(viewport);
        drawOptions(context, viewport.x() - scrollX, viewport.y(), contentWidth, selectedIndex);
        graphics.disableScissor(); DebugBridge.backend().unclip();
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
    private int beginAt(int screenX, int screenY, int mouseX, int mouseY, Font font) {
        int index = indexAt(screenX, screenY, mouseX, mouseY, font);
        if (index >= 0) gesture.pointerDown(index);
        return index;
    }

    private int indexAt(int screenX, int screenY, int mouseX, int mouseY, Font font) {
        Rect viewport = getScreenBounds(screenX, screenY);
        if (!viewport.contains(mouseX, mouseY)) return -1;
        int contentWidth = Math.max(viewport.width(), minimumRowWidth(font));
        return hitIndex(viewport.x() - scrollX, viewport.y(), contentWidth, mouseX, mouseY, font);
    }

    public boolean scrollBy(int amount, Font font) {
        int max = Math.max(0, minimumRowWidth(font) - getBounds().width());
        if (max == 0) return false;
        cancelInteraction();
        scrollX = Math.max(0, Math.min(max, scrollX + amount));
        return true;
    }

    private void drawOptions(ModularGuiContext context, int x, int y,
                     int width, int selectedIndex) {
        int height = size.getHeight();
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

            Label text = optionLabels.get(index).setColor(style.getTextColor()).setShadow(style.isTextShadow());
            text.layout(new Rect(
                            optionX + TEXT_PADDING, y + topInset,
                            Math.max(0, optionWidth - 2 * TEXT_PADDING), height), context.getFont());
            DebugBridge.backend().internal(this, "option_" + index, text);
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
        int height = size.getHeight();
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
