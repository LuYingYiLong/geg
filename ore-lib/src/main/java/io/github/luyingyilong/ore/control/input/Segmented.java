package io.github.luyingyilong.ore.control.input;

import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.text.CaptionGeometry;
import io.github.luyingyilong.ore.theme.ThemeKey;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.window.FocusManager;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.OreSounds;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.StyleBox;
import io.github.luyingyilong.ore.event.gesture.ReleaseGesture;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Content-sized single-choice options; FILL/EXPAND distributes any additional width.
 */
public final class Segmented extends Control {
    public static final io.github.luyingyilong.ore.theme.ThemeKey<StyleBox> NORMAL = io.github.luyingyilong.ore.theme.ThemeKey.of("Segmented", "normal", StyleBox.class, io.github.luyingyilong.ore.theme.ThemeKey.Kind.STYLE, Button.NORMAL.defaultValue(), true);
    public static final io.github.luyingyilong.ore.theme.ThemeKey<StyleBox> HOVER = io.github.luyingyilong.ore.theme.ThemeKey.of("Segmented", "hover", StyleBox.class, io.github.luyingyilong.ore.theme.ThemeKey.Kind.STYLE, Button.HOVER.defaultValue(), true);
    public static final io.github.luyingyilong.ore.theme.ThemeKey<StyleBox> PRESSED = io.github.luyingyilong.ore.theme.ThemeKey.of("Segmented", "pressed", StyleBox.class, io.github.luyingyilong.ore.theme.ThemeKey.Kind.STYLE, Button.PRESSED.defaultValue(), true);
    public static final ThemeKey<Integer> ICON_SIZE = new ThemeKey<>("Segmented", "icon_size", Integer.class,
            ThemeKey.Kind.INTEGER, 8, true, v -> v >= 0 && v <= 1_000_000);
    public static final ThemeKey<Integer> ICON_GAP = new ThemeKey<>("Segmented", "icon_gap", Integer.class,
            ThemeKey.Kind.INTEGER, 4, true, v -> v >= 0 && v <= 1_000_000);
    private final List<Component> options;
    private final List<Label> optionLabels;
    private final List<TextureRect> optionIcons;
    private final Button.Size size;
    private final SignalSource<Integer> selectionChanged = new SignalSource<>(this, "selectionChanged", "Integer");
    private final ReleaseGesture<Integer> gesture = new ReleaseGesture<>();
    private int selectedIndex;
    private int focusedIndex;
    private boolean focused;
    private int scrollX;
    private boolean revealSelected = true;
    private int lastViewportWidth = -1;
    private Font layoutFont;
    private int hoveredIndex = -1;

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
        optionIcons = options.stream().map(option -> new TextureRect().setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE)
                .setStretchMode(TextureRect.StretchMode.KEEP_ASPECT_CENTERED)).toList();
        for (int i = 0; i < optionIcons.size(); i++) ownPart("option_icon_" + i, optionIcons.get(i));
        this.size = java.util.Objects.requireNonNull(size);
        setMinimum(0, size.getHeight());
        setFocusable(true);

    }

    private record TreeState(List<TextureSource> icons, int selected, int focused, int scroll) { }
    @Override protected Object captureTreeState() { return new TreeState(optionIcons.stream().map(TextureRect::getTexture).toList(), selectedIndex, focusedIndex, scrollX); }
    @Override protected void restoreTreeState(Object state) {
        if (state instanceof TreeState saved) {
            for (int i = 0; i < saved.icons().size(); i++) optionIcons.get(i).setTexture(saved.icons().get(i));
            selectedIndex = saved.selected(); focusedIndex = saved.focused(); scrollX = saved.scroll();
        }
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Segmented");
        db.register(NORMAL);
        db.register(HOVER);
        db.register(PRESSED);
        db.register(ICON_SIZE);
        db.register(ICON_GAP);
        db.registerFallback(NORMAL, Button.NORMAL);
        db.registerFallback(HOVER, Button.HOVER);
        db.registerFallback(PRESSED, Button.PRESSED);
    }

    @Override
    public String getThemeState() {
        return "selected=" + selectedIndex + ", focused=" + focused;
    }

    @io.github.luyingyilong.ore.gui.UiSignal
    public Signal<Integer> selectionChanged() {
        return selectionChanged.signal();
    }

    public TextureSource getIcon(int index) { return optionIcons.get(index).getTexture(); }

    public Segmented setIcon(int index, TextureSource source) {
        getSignalScope().checkThread();
        if (!isAlive()) throw new IllegalStateException("Cannot update pending/freed Segmented");
        TextureRect icon = optionIcons.get(index);
        if (!java.util.Objects.equals(icon.getTexture(), source)) {
            icon.setTexture(source);
            cancelInteraction();
            revealSelected = true;
            requestLayout();
        }
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

    @io.github.luyingyilong.ore.gui.UiProperty(initialState = true, silentSetter = "setSelectedNoSignal")
    public int getSelected() {
        return selectedIndex;
    }

    public Segmented setSelected(int index) {
        int previous = selectedIndex;
        setSelectedNoSignal(index);
        if (previous != selectedIndex) selectionChanged.emit(selectedIndex);
        return this;
    }

    public int getScrollX() {
        return scrollX;
    }

    @Override
    public void invalidateTextLayout() {
        revealSelected = true;
        super.invalidateTextLayout();
    }

    @Override
    public Rect getFocusBounds() {
        Rect area = getAbsoluteBounds();
        int width = Math.max(area.width(), minimumRowWidth(layoutFont));
        int index = focused ? focusedIndex : selectedIndex;
        int left = optionStart(index, width, layoutFont);
        int right = optionStart(index + 1, width, layoutFont);
        return getOptionStyle(index, isOptionHovered(index)).getBackgroundRect(new Rect(
                area.x() + left - scrollX, area.y(), right - left, size.getHeight()));
    }

    @Override
    public void onFocusChanged(boolean focused) {
        this.focused = focused;
        if (!focused) cancelInteraction();
        if (focused) {
            focusedIndex = selectedIndex;
            revealSelected = true;
        }
    }

    @Override
    public boolean onFocusMove(FocusManager.Direction direction) {
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

    @Override
    public boolean activate() {
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
        for (int index = 0; index < options.size(); index++) width += optionWidth(index, font);
        return width;
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return Math.max(super.measureWidth(font, available), minimumRowWidth(font));
    }

    private int optionWidth(int index, Font font) {
        int textWidth = font == null ? 0 : optionLabels.get(index).measureWidth(font, Integer.MAX_VALUE);
        Size image = imageSize(index);
        int gap = image.width() > 0 && textWidth > 0 ? getThemeItem(ICON_GAP) : 0;
        return Math.max(getThemeItem(OreTheme.SEGMENTED_MIN_WIDTH), textWidth + image.width() + gap
                + getThemeItem(NORMAL).getContentMargin().getHorizontal());
    }

    private Size imageSize(int index) {
        var region = optionIcons.get(index).getResolvedTextureRegion();
        int maximum = getThemeItem(ICON_SIZE);
        if (region == null || maximum == 0) return new Size(0, 0);
        double ratio = Math.min(1, Math.min(maximum / (double) region.getWidth(), maximum / (double) region.getHeight()));
        return new Size(Math.max(1, (int) Math.round(region.getWidth() * ratio)), Math.max(1, (int) Math.round(region.getHeight() * ratio)));
    }

    /**
     * Shared by painting, pointer hits, focus outlines and scroll-to-selection.
     */
    private int optionStart(int index, int rowWidth, Font font) {
        int start = 0;
        for (int preceding = 0; preceding < index; preceding++) start += optionWidth(preceding, font);
        return start + Math.max(0, rowWidth - minimumRowWidth(font)) * index / options.size();
    }

    @Override
    public void layout(Rect bounds, Font font) {
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

    @Override
    protected boolean wheel(int x, int y, double delta, boolean horizontal) {
        return getWindowContext().getMui() != null && scrollBy(-(int) Math.signum(delta) * 24, getWindowContext().getMui().getFont());
    }

    @Override
    protected boolean pointerDown(int rootX, int rootY) {
        if (layoutFont == null || beginAt(0, 0, rootX, rootY, layoutFont) < 0) return false;

        return true;
    }

    @Override
    protected boolean pointerUp(int rootX, int rootY, boolean over) {
        int index = layoutFont == null ? -1 : indexAt(0, 0, rootX, rootY, layoutFont);
        var release = gesture.pointerUp(index < 0 ? null : index, over && getVisible());
        if (release.target() != null) selectFromInput(release.target());
        return release.handled();
    }

    @Override
    public boolean onActivationPressed(int keyCode) {
        if (!getVisible()) return false;
        gesture.keyDown(keyCode, focusedIndex);
        return true;
    }

    @Override
    public boolean onActivationReleased(int keyCode) {
        var release = gesture.keyUp(keyCode, getVisible() ? focusedIndex : null);
        if (release.target() != null) selectFromInput(release.target());
        return release.handled();
    }

    @Override
    public void cancelInteraction() {
        gesture.cancel();
    }

    @Override
    protected boolean pointerMove(int rootX, int rootY) {
        hoveredIndex = layoutFont == null ? -1 : indexAt(0, 0, rootX, rootY, layoutFont);
        return gesture.pointerPending();
    }

    private boolean isOptionHovered(int index) {
        return index == hoveredIndex;
    }

    StyleBox getOptionStyle(int index, boolean hovered) {
        return getThemeItem(index == selectedIndex ? OreTheme.SEGMENTED_BUTTON_SELECTED
                : (gesture.keyPressed(index) || hovered && gesture.pointerPressed(index) ? PRESSED : hovered ? HOVER : NORMAL));
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
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
        context.disableScissor();
        DebugBridge.backend().unclip();
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

    /**
     * Arms the option under the pointer; selection is deferred until a matching release.
     */
    private int beginAt(int screenX, int screenY, int mouseX, int mouseY, Font font) {
        int index = indexAt(screenX, screenY, mouseX, mouseY, font);
        hoveredIndex = index;
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
            var style = getOptionStyle(index, hovered);
            Rect area = new Rect(optionX, y, optionWidth, height);
            style.draw(context, area);
            Label text = optionLabels.get(index);
            text.setThemeOverride(OreTheme.FONT_COLOR, getThemeItem(selected ? OreTheme.SEGMENTED_SELECTED_FONT_COLOR : Button.FONT_COLOR));
            layoutOption(index, area, style, context.getFont());
            DebugBridge.backend().internal(this, "option_" + index, text);
            text.draw(context, 0, 0);
            TextureRect icon = optionIcons.get(index);
            if (icon.getBounds().width() > 0 && icon.getBounds().height() > 0) {
                DebugBridge.backend().internal(this, "option_icon_" + index, icon);
                icon.draw(context, 0, 0);
            }

            if (selected) {
                Rect indicator = getSelectedIndicatorRect(area);
                if (indicator.width() > 0 && indicator.height() > 0)
                    getThemeItem(OreTheme.SEGMENTED_INDICATOR).draw(context, indicator);
            }
        }
    }

    static Rect getOptionLabelBounds(Rect area, StyleBox style) {
        return Button.getLabelBounds(style.getContentDrawRect(area));
    }

    void layoutOption(int index, Rect area, StyleBox style, Font font) {
        Rect content = style.getContentDrawRect(area);
        Size image = imageSize(index);
        Label label = optionLabels.get(index);
        if (image.width() == 0) {
            label.layout(getOptionLabelBounds(area, style), font);
            optionIcons.get(index).layout(new Rect(content.x(), content.y(), 0, 0), font);
            return;
        }
        int textWidth = label.measureWidth(font, Integer.MAX_VALUE);
        int gap = textWidth > 0 ? getThemeItem(ICON_GAP) : 0;
        int labelWidth = Math.min(textWidth, Math.max(0, content.width() - image.width() - gap));
        int left = content.x() + Math.max(0, (content.width() - image.width() - gap - labelWidth) / 2);
        label.layout(CaptionGeometry.getLabelBounds(new Rect(left + image.width() + gap, content.y(), labelWidth, content.height())), font);
        optionIcons.get(index).layout(CaptionGeometry.getIconBounds(new Rect(left, content.y(), image.width(), content.height()), image.width(), image.height()), font);
    }

    /**
     * Decoration uses the original option rectangle, independently of background/content offsets.
     */
    Rect getSelectedIndicatorRect(Rect option) {
        float ratio = getThemeItem(OreTheme.SEGMENTED_INDICATOR_WIDTH_RATIO);
        int width = ratio == 0 || option.width() == 0 ? 0 : Math.min(option.width(), Math.min(
                getThemeItem(OreTheme.SEGMENTED_INDICATOR_MAX_WIDTH), Math.max(1, (int) (option.width() * ratio))));
        int gap = Math.min(option.height(), getThemeItem(OreTheme.SEGMENTED_INDICATOR_BOTTOM_GAP));
        int height = Math.min(option.height() - gap, getThemeItem(OreTheme.SEGMENTED_INDICATOR_HEIGHT));
        return new Rect(option.x() + (option.width() - width) / 2, option.bottom() - gap - height, width, height);
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
