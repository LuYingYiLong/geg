package io.github.luyingyilong.ore.control.navigation;

import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.event.gesture.ReleaseGesture;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.text.CaptionGeometry;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.theme.StyleBox;
import io.github.luyingyilong.ore.window.FocusManager;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;

/** One roving keyboard target; ordinary caption children remain visible to Inspector. */
final class TabsBar extends Container {
    private final Tabs tabs;
    final ReleaseGesture<String> gesture = new ReleaseGesture<>();
    String focusedKey;
    String hoveredKey;
    boolean revealFocus = true;
    private int lastWidth = -1;
    private int lastContentWidth = -1;

    TabsBar(Tabs tabs) {
        this.tabs = tabs;
        setThemeParent(tabs);
        setFocusable(true);
    }

    @Override public String getThemeType() { return "Tabs"; }
    void setCaptions(List<Control> controls) {
        updateChildren(controls);
        for (Control caption : controls) caption.setThemeParent(tabs);
    }
    @Override protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren()) throw new IllegalStateException("Tab bar belongs to Tabs");
    }

    int minimumHeight() { return tabs.getThemeItem(Tabs.HEADER_HEIGHT); }

    @Override public int measureWidth(Font font, int available) {
        long width = Math.max(0, getChildCount() - 1) * (long) tabs.getThemeItem(Tabs.ITEM_GAP);
        for (Control child : getChildren()) if (child.isAlive()) width += child.measureWidth(font, Integer.MAX_VALUE);
        return (int) Math.min(Integer.MAX_VALUE, width);
    }

    @Override public int measureHeight(Font font, int available) {
        int height = minimumHeight();
        for (Control child : getChildren()) if (child.isAlive()) height = Math.max(height, child.measureHeight(font, available));
        return height;
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        if (lastWidth != tabs.header.getBounds().width() || lastContentWidth != bounds.width()) {
            lastWidth = tabs.header.getBounds().width();
            lastContentWidth = bounds.width();
            revealFocus = true;
        }
        int x = 0;
        for (Control caption : getChildren()) if (caption.isAlive()) {
            int width = caption.measureWidth(font, Integer.MAX_VALUE);
            layoutChild(caption, new Rect(x, 0, width, bounds.height()), font);
            x += width + tabs.getThemeItem(Tabs.ITEM_GAP);
        }
    }

    void synchronizeFocus() {
        if (!enabled(focusedKey)) focusedKey = enabled(tabs.getSelectedKey()) ? tabs.getSelectedKey()
                : tabs.entries().stream().filter(e -> !e.item.getDisabled()).map(e -> e.item.getKey()).findFirst().orElse(null);
        setFocusable(focusedKey != null);
    }

    private boolean enabled(String key) {
        Tabs.Entry entry = tabs.entry(key);
        return entry != null && !entry.item.getDisabled();
    }

    void reveal() {
        if (!revealFocus) return;
        Tabs.Entry entry = tabs.entry(focusedKey);
        if (entry != null) {
            Rect bounds = entry.caption.getBounds();
            if (bounds.width() > tabs.header.getBounds().width()) tabs.header.setScroll(bounds.x(), 0);
            else tabs.header.ensureVisible(bounds);
        }
        revealFocus = false;
    }

    @Override public void onFocusChanged(boolean focused) {
        if (focused) {
            // FocusManager has not yet called MUI.removeFocus on the old editor.
            tabs.suspendPageEditor();
            synchronizeFocus();
            revealFocus = true;
        } else cancelInteraction();
    }

    @Override public boolean onFocusMove(FocusManager.Direction direction) {
        if (direction != FocusManager.Direction.LEFT && direction != FocusManager.Direction.RIGHT) return false;
        List<String> keys = enabledKeys();
        if (keys.isEmpty()) return false;
        int next = Math.max(0, Math.min(keys.size() - 1, keys.indexOf(focusedKey)
                + (direction == FocusManager.Direction.LEFT ? -1 : 1)));
        focusKey(keys.get(next));
        return true;
    }

    @Override public boolean onFocusKey(int key, int modifiers) {
        if (key != GLFW.GLFW_KEY_HOME && key != GLFW.GLFW_KEY_END) return false;
        List<String> keys = enabledKeys();
        if (keys.isEmpty()) return false;
        focusKey(keys.get(key == GLFW.GLFW_KEY_HOME ? 0 : keys.size() - 1));
        return true;
    }

    private List<String> enabledKeys() {
        return tabs.entries().stream().filter(e -> !e.item.getDisabled()).map(e -> e.item.getKey()).toList();
    }

    private void focusKey(String key) {
        cancelInteraction();
        focusedKey = key;
        revealFocus = true;
        reveal();
    }

    @Override public Rect getFocusBounds() {
        Tabs.Entry entry = tabs.entry(focusedKey);
        return entry == null ? new Rect(0, 0, 0, 0) : entry.caption.style().getBackgroundRect(entry.caption.getAbsoluteBounds());
    }

    private String keyAt(int x, int y) {
        for (Tabs.Entry entry : tabs.entries()) if (entry.caption.isAlive() && entry.caption.getAbsoluteBounds().contains(x, y))
            return entry.item.getKey();
        return null;
    }

    @Override protected boolean pointerDown(int x, int y) {
        String key = keyAt(x, y);
        if (key == null) return false;
        hoveredKey = key;
        if (enabled(key)) { focusedKey = key; gesture.pointerDown(key); }
        return true;
    }

    @Override protected boolean pointerMove(int x, int y) {
        hoveredKey = keyAt(x, y);
        return gesture.pointerPending();
    }

    @Override protected boolean pointerUp(int x, int y, boolean over) {
        String key = keyAt(x, y);
        var release = gesture.pointerUp(key, over && enabled(key));
        if (release.target() != null) tabs.activate(release.target());
        return release.handled();
    }

    @Override public boolean onActivationPressed(int key) {
        if (!enabled(focusedKey)) return false;
        gesture.keyDown(key, focusedKey);
        return true;
    }

    @Override public boolean onActivationReleased(int key) {
        var release = gesture.keyUp(key, enabled(focusedKey) ? focusedKey : null);
        if (release.target() != null) tabs.activate(release.target());
        return release.handled();
    }

    @Override public boolean activate() {
        if (!enabled(focusedKey)) return false;
        tabs.activate(focusedKey); return true;
    }

    @Override public void cancelInteraction() { gesture.cancel(); }
    @Override public String getThemeState() { return "focused=" + focusedKey; }

    @Override protected void render(DrawContext context, int screenX, int screenY) {
        // Pointer moves outside this branch do not bubble through the bar. Refresh from this frame's geometry.
        Rect viewport = tabs.header.getScreenBounds(screenX + tabs.header.getScrollX(), screenY);
        int x = context.getAbsMouseX(), y = context.getAbsMouseY();
        hoveredKey = viewport.contains(x, y) ? keyAt(x - screenX, y - screenY) : null;
    }

    static final class Caption extends Control {
        private TabItem item;
        private final Label label = ownPart("label", new Label().setOpticalCenter(true).setOverflow(Label.Overflow.ELLIPSIS).setMaxLines(1)
                .setAlignment(Label.Horizontal.CENTER, Label.Vertical.CENTER));
        private final TextureRect icon = ownPart("icon", new TextureRect().setStretchMode(TextureRect.StretchMode.KEEP_ASPECT_CENTERED)
                .setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE));

        Caption(TabItem item) { update(item); }
        void update(TabItem item) {
            this.item = item;
            label.setText(item.getLabel());
            icon.setTexture(item.getIcon());
            icon.setOpacity(item.getDisabled() ? 0.5F : 1);
            setDebugName("Tab: " + item.getKey());
            requestLayout();
        }
        @Override public String getThemeType() { return "Tabs"; }

        private TabsBar bar() { return getParent() instanceof TabsBar bar ? bar : null; }
        private boolean selected() { return bar() != null && Objects.equals(item.getKey(), bar().tabs.getSelectedKey()); }
        private boolean hovered() { return bar() != null && Objects.equals(item.getKey(), bar().hoveredKey); }
        private boolean pressed() { return bar() != null && (bar().gesture.keyPressed(item.getKey())
                || hovered() && bar().gesture.pointerPressed(item.getKey())); }
        StyleBox style() { return getThemeItem(item.getDisabled() ? Tabs.DISABLED : selected() ? Tabs.SELECTED
                : pressed() ? Tabs.PRESSED : hovered() ? Tabs.HOVER : Tabs.NORMAL); }

        private Size imageSize(Font font) {
            var region = item.getIcon() == null ? null : item.getIcon().resolve(this);
            int size = getThemeItem(Tabs.ICON_SIZE);
            if (region == null || size == 0) return new Size(0, 0);
            double ratio = Math.min(1, Math.min(size / (double) region.getWidth(), size / (double) region.getHeight()));
            return new Size(Math.max(1, (int) Math.round(region.getWidth() * ratio)), Math.max(1, (int) Math.round(region.getHeight() * ratio)));
        }

        @Override public int measureWidth(Font font, int available) {
            int text = font == null ? 0 : label.measureWidth(font, Integer.MAX_VALUE);
            Size image = imageSize(font);
            int content = text + image.width() + (text > 0 && image.width() > 0 ? getThemeItem(Tabs.ICON_GAP) : 0);
            int margin = 0;
            for (var key : List.of(Tabs.NORMAL, Tabs.HOVER, Tabs.PRESSED, Tabs.SELECTED, Tabs.DISABLED))
                margin = Math.max(margin, getThemeItem(key).getContentMargin().getHorizontal());
            int natural = Math.max(getThemeItem(Tabs.MINIMUM_ITEM_WIDTH), content + margin);
            int maximum = bar() == null ? 0 : bar().tabs.getMaxTabWidth();
            return maximum == 0 ? natural : Math.min(maximum, natural);
        }

        @Override public int measureHeight(Font font, int available) {
            int height = getThemeItem(Tabs.HEADER_HEIGHT), image = imageSize(font).height();
            int text = font == null ? 0 : label.lineHeight(font);
            for (var key : List.of(Tabs.NORMAL, Tabs.HOVER, Tabs.PRESSED, Tabs.SELECTED, Tabs.DISABLED))
                height = Math.max(height, Math.max(image, text) + getThemeItem(key).getContentMargin().getVertical());
            return height;
        }

        @Override protected void render(DrawContext context, int screenX, int screenY) {
            Rect area = getScreenBounds(screenX, screenY);
            StyleBox style = style();
            style.draw(context, area);
            layoutContents(area, style, context.getFont());
            context.enableScissor(area.x(), area.y(), area.right(), area.bottom());
            DebugBridge.backend().clip(context.toScreenRect(area));
            try {
                DebugBridge.backend().internal(this, "label", label);
                label.draw(context, 0, 0);
                if (icon.getBounds().width() > 0 && icon.getBounds().height() > 0) {
                    DebugBridge.backend().internal(this, "icon", icon);
                    icon.draw(context, 0, 0);
                }
            } finally {
                context.disableScissor();
                DebugBridge.backend().unclip();
            }
            if (selected()) getThemeItem(Tabs.SELECTED_INDICATOR).draw(context, indicator(area));
        }

        void layoutContents(Rect area, StyleBox style, Font font) {
            Rect content = style.getContentDrawRect(area);
            Size image = imageSize(font);
            int width = label.measureWidth(font, Integer.MAX_VALUE);
            int gap = image.width() > 0 && width > 0 ? getThemeItem(Tabs.ICON_GAP) : 0;
            int labelWidth = Math.min(width, Math.max(0, content.width() - image.width() - gap));
            int left = content.x() + Math.max(0, (content.width() - image.width() - gap - labelWidth) / 2);
            label.setThemeOverride(OreTheme.FONT_COLOR, getThemeItem(item.getDisabled() ? Tabs.FONT_DISABLED_COLOR
                    : selected() ? Tabs.FONT_SELECTED_COLOR : pressed() ? Tabs.FONT_PRESSED_COLOR : hovered() ? Tabs.FONT_HOVER_COLOR : Tabs.FONT_COLOR));
            label.layout(CaptionGeometry.getLabelBounds(new Rect(left + image.width() + gap, content.y(), labelWidth, content.height())), font);
            icon.layout(CaptionGeometry.getIconBounds(new Rect(left, content.y(), image.width(), content.height()), image.width(), image.height()), font);
        }

        Rect indicator(Rect area) {
            int width = Math.min(area.width(), Math.min(getThemeItem(Tabs.INDICATOR_MAX_WIDTH),
                    Math.max(0, (int) (area.width() * getThemeItem(Tabs.INDICATOR_WIDTH_RATIO)))));
            int gap = Math.min(area.height(), getThemeItem(Tabs.INDICATOR_BOTTOM_GAP));
            int height = Math.min(area.height() - gap, getThemeItem(Tabs.INDICATOR_HEIGHT));
            return new Rect(area.x() + (area.width() - width) / 2, area.bottom() - gap - height, width, height);
        }

        @Override public String getThemeState() {
            return item.getDisabled() ? "disabled" : selected() ? "selected" : pressed() ? "pressed" : hovered() ? "hover" : "normal";
        }
    }
}
