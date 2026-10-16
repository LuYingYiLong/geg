package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.util.TextLines;
import io.github.luyingyilong.geg.client.ui.util.HiddenInputScroll;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.utils.PointF;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** Multiline Ore editor; the existing MUI handler still owns selection, scrolling and IME commits. */
public final class TextArea extends Input {
    private int rows = 6;
    private PointF lastCaret;
    private int lastWidth = -1, lastHeight = -1;
    private float lastTextHeight = -1;
    public TextArea(float depth) {
        super(depth);
        handler.setMaxLines(2049);
        ((OreTextFieldRenderer) renderer).multiline(true);
        setMaxLength(4096);
        setPattern(java.util.regex.Pattern.compile(".*", java.util.regex.Pattern.DOTALL));
        setTextAlignment(brachy.modularui.utils.Alignment.TopLeft);
        getScrollArea().setScrollY(HiddenInputScroll.vertical());
    }
    public TextArea rows(int rows) {
        if (rows < 2 || rows > 20) throw new IllegalArgumentException("Rows must be between 2 and 20");
        this.rows = rows; return this;
    }
    @Override protected void setupDrawText(brachy.modularui.screen.viewport.ModularGuiContext context,
                                            brachy.modularui.theme.TextFieldTheme theme) {
        super.setupDrawText(context, theme);
        renderer.setPos(getArea().getPadding().left(), firstLineTop());
        updateTextScroll();
    }
    @Override public int preferredInputHeight() { return rows * 12 + 8; }
    @Override public void onUpdate() {
        super.onUpdate();
        updateTextScroll();
    }

    private void updateTextScroll() {
        var textRenderer = (OreTextFieldRenderer) renderer;
        var scroll = getScrollArea().getScrollY();
        float textHeight = textRenderer.visualHeight();
        scroll.setScrollSize((int) Math.ceil(firstLineTop() + textHeight + getArea().getPadding().bottom()));
        scroll.clamp(getScrollArea());
        PointF caret = textRenderer.caretPosition();
        boolean changed = lastCaret == null || caret.x != lastCaret.x || caret.y != lastCaret.y
                || lastWidth != getArea().width || lastHeight != getArea().height || lastTextHeight != textHeight;
        if (isFocused() && changed) {
            int top = (int) Math.floor(caret.y) - 1;
            int bottom = (int) Math.ceil(caret.y + renderer.getFontHeight()) + 1;
            int viewport = Math.max(1, getArea().height - getArea().getPadding().bottom());
            if (top < getScrollY()) scroll.scrollTo(getScrollArea(), top);
            else if (bottom > getScrollY() + viewport) scroll.scrollTo(getScrollArea(), bottom - viewport);
        }
        // Soft wrapping owns the horizontal extent; only the single-line Input scrolls sideways.
        getScrollArea().getScrollX().setScrollSize(0);
        getScrollArea().getScrollX().scrollTo(getScrollArea(), 0);
        lastCaret = caret;
        lastWidth = getArea().width;
        lastHeight = getArea().height;
        lastTextHeight = textHeight;
    }

    @Override public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (inputMethodInteractive() && isFocused() && !composing()
                && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) == 0
                && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END)) {
            var point = ((OreTextFieldRenderer) renderer).moveVisual(
                    keyCode == GLFW.GLFW_KEY_UP ? -1 : keyCode == GLFW.GLFW_KEY_DOWN ? 1 : 0,
                    keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END, keyCode == GLFW.GLFW_KEY_END);
            handler.setCursor(point.y, point.x, (modifiers & GLFW.GLFW_MOD_SHIFT) == 0, false);
            renderer.setCursor(true);
            updateTextScroll();
            return Interactable.Result.SUCCESS;
        }
        return super.onKeyPressed(keyCode, scanCode, modifiers);
    }
    @Override public int getMaxLines() { return 2049; }
    @Override public String getText() { return String.join("\n", handler.getText()); }

    @Override protected void removeNativeFocus(brachy.modularui.screen.viewport.ModularGuiContext context) {
        // MUI's single-line value adapter rejects multiple source lines on blur. Pass it the
        // complete value as one string, then restore the editor's explicit newline structure.
        String text = getText();
        var lines = handler.getText();
        lines.clear();
        lines.add(text);
        try { super.removeNativeFocus(context); }
        finally {
            String value = String.join("\n", lines);
            lines.clear();
            lines.addAll(TextLines.split(value));
        }
    }

    @Override public void setText(String text) {
        var lines = handler.getText(); lines.clear();
        lines.addAll(TextLines.split(text));
        handler.setMainCursor(new brachy.modularui.utils.Point(0, 0), false);
        lastCaret = null;
        scheduleResize();
    }
}
