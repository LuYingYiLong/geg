package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.*;

import io.github.luyingyilong.ore.debug.DebugBridge;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.FocusManager;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.StyleBox;
import io.github.luyingyilong.ore.util.ReleaseGesture;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Content-sized single-choice options; FILL/EXPAND distributes any additional width. */
public final class Segmented extends Control {
    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) { io.github.luyingyilong.ore.theme.OreTheme.registerType(db,"Segmented"); }
    @Override public String getThemeState() { return "selected="+selectedIndex+", focused="+focused; }

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
    private int hoveredIndex=-1;

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
        return getOptionStyle(index,isOptionHovered(index)).getBackgroundRect(new Rect(
                area.x() + left - scrollX, area.y(), right - left + 1, size.getHeight()));
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
        for (int index=0;index<options.size();index++) width += optionWidth(index,font);
        return width;
    }

    @Override public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return Math.max(super.measureWidth(font, available), minimumRowWidth(font));
    }

    private int optionWidth(int index, Font font) {
        int textWidth=font==null?0:optionLabels.get(index).measureWidth(font,Integer.MAX_VALUE);
        return Math.max(getThemeItem(OreTheme.SEGMENTED_MIN_WIDTH),textWidth+getThemeItem(OreTheme.BUTTON_SECONDARY_NORMAL).getContentMargin().getHorizontal());
    }

    /** Shared by painting, pointer hits, focus outlines and scroll-to-selection. */
    private int optionStart(int index, int rowWidth, Font font) {
        int start = 0;
        for (int preceding = 0; preceding < index; preceding++) start += optionWidth(preceding, font);
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

    @Override protected boolean pointerMove(int rootX, int rootY) {
        hoveredIndex=layoutFont==null?-1:indexAt(0,0,rootX,rootY,layoutFont);
        return gesture.pointerPending();
    }

    private boolean isOptionHovered(int index) { return index==hoveredIndex; }

    StyleBox getOptionStyle(int index, boolean hovered) {
        return getThemeItem(index==selectedIndex?OreTheme.SEGMENTED_BUTTON_SELECTED
                :Button.getStyleKey(Button.Type.SECONDARY,hovered,
                gesture.keyPressed(index) || hovered && gesture.pointerPressed(index),false));
    }

    @Override protected void render(DrawContext context, int screenX, int screenY) {
        Rect viewport = getScreenBounds(screenX, screenY);
        if (viewport.width() <= 0 || viewport.height() <= 0) return;
        int contentWidth = Math.max(viewport.width(), minimumRowWidth(context.getFont()));
        scrollX = Math.min(scrollX, contentWidth - viewport.width());
        if (revealSelected) {
            ensureSelectedVisible(contentWidth, viewport.width());
            revealSelected = false;
        }
        context.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        DebugBridge.backend().clip(context.toScreenRect(viewport));
        drawOptions(context, viewport.x() - scrollX, viewport.y(), contentWidth, selectedIndex);
        context.disableScissor(); DebugBridge.backend().unclip();
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
        hoveredIndex=index;
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

    private void drawOptions(DrawContext context, int x, int y,
                     int width, int selectedIndex) {
        int height = size.getHeight();

        hoveredIndex = hitIndex(x, y, width,
                context.getAbsMouseX(), context.getAbsMouseY(), context.getFont());

        for (int index = 0; index < options.size(); index++) {
            int optionX = x + optionStart(index, width, context.getFont());
            int optionEnd = x + optionStart(index + 1, width, context.getFont());
            // Overlap the adjacent 1px texture borders without changing the row's outer width.
            int optionWidth = optionEnd - optionX + (index < options.size() - 1 ? 1 : 0);
            boolean selected = index == selectedIndex;
            boolean hovered = index == hoveredIndex;
            var style=getOptionStyle(index,hovered);
            Rect area=new Rect(optionX,y,optionWidth,height);
            style.draw(context,area);
            Label text=optionLabels.get(index);
            text.setThemeOverride(OreTheme.TEXT_COLOR,getThemeItem(selected?OreTheme.SEGMENTED_BUTTON_SELECTED_TEXT:OreTheme.BUTTON_SECONDARY_NORMAL_TEXT));
            text.layout(style.getContentDrawRect(area),context.getFont());
            DebugBridge.backend().internal(this, "option_" + index, text);
            text.draw(context, 0, 0);

            if (selected) {
                Rect indicator=getSelectedIndicatorRect(area);
                if(indicator.width()>0 && indicator.height()>0)
                    getThemeItem(OreTheme.SEGMENTED_INDICATOR).draw(context,indicator);
            }
        }
    }

    /** Decoration uses the original option rectangle, independently of background/content offsets. */
    Rect getSelectedIndicatorRect(Rect option) {
        float ratio=getThemeItem(OreTheme.SEGMENTED_INDICATOR_WIDTH_RATIO);
        int width=ratio==0 || option.width()==0?0:Math.min(option.width(),Math.min(
                getThemeItem(OreTheme.SEGMENTED_INDICATOR_MAX_WIDTH),Math.max(1,(int)(option.width()*ratio))));
        int gap=Math.min(option.height(),getThemeItem(OreTheme.SEGMENTED_INDICATOR_BOTTOM_GAP));
        int height=Math.min(option.height()-gap,getThemeItem(OreTheme.SEGMENTED_INDICATOR_HEIGHT));
        return new Rect(option.x()+(option.width()-width)/2,option.bottom()-gap-height,width,height);
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
                return index;
            }
        }
        return -1;
    }

}
