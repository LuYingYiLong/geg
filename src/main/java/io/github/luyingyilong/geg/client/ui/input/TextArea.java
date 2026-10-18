package io.github.luyingyilong.geg.client.ui.input;

import io.github.luyingyilong.geg.client.ui.debug.DebugBridge;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.utils.PointF;
import io.github.luyingyilong.geg.client.ui.Label;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.theme.OreTextMetrics;
import io.github.luyingyilong.geg.client.ui.util.TextLines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** Multiline Ore editor; the existing MUI handler still owns selection, scrolling and IME commits. */
public final class TextArea extends Input {
    private int rows = 6;
    private PointF lastCaret;
    private int lastWidth = -1, lastHeight = -1;
    private float lastTextHeight = -1;
    private Runnable submit;
    private java.util.function.BiPredicate<Integer, Integer> keyHandler = (key, modifiers) -> false;
    private int minimumRows, maximumRows;
    private int automaticRows;
    private int pendingCursor = -1;
    public TextArea onSubmit(Runnable submit) { this.submit = submit; return this; }
    public TextArea keyHandler(java.util.function.BiPredicate<Integer, Integer> handler) {
        keyHandler = java.util.Objects.requireNonNull(handler); return this;
    }
    public int cursorOffset() {
        var caret = handler.getMainCursor();
        return TextLines.offset(handler.getText(), caret.x, caret.y);
    }
    public TextArea cursorOffset(int offset) {
        if (!isValid()) { pendingCursor = Math.max(0, offset); return this; }
        pendingCursor = -1;
        var lines = handler.getText();
        if (lines.isEmpty()) lines.add("");
        int row = 0, column = Math.max(0, Math.min(getText().length(), offset));
        while (row < lines.size() - 1 && column > lines.get(row).length()) { column -= lines.get(row).length() + 1; row++; }
        editingHandler().endEditGroup();
        handler.setCursor(row, Math.min(column, lines.get(row).length()), true, false);
        lastCaret = null;
        return this;
    }
    /** Completion is one undoable edit, preserving the rest of the document and its cursor. */
    public void applyCompletion(String value, int offset) {
        if (!editingHandler().replaceAll(value)) return;
        cursorOffset(offset);
    }
    public TextArea autoHeight(int minimumRows, int maximumRows) {
        if (minimumRows < 1 || maximumRows > 20 || maximumRows < minimumRows) throw new IllegalArgumentException("Invalid row range");
        this.minimumRows = minimumRows; this.maximumRows = maximumRows; this.rows = minimumRows;
        return this;
    }
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
        if (rows < 1 || rows > 20) throw new IllegalArgumentException("Rows must be between 1 and 20");
        this.rows = rows;
        minimumRows = maximumRows = automaticRows = 0;
        invalidateInputLayout();
        return this;
    }
    @Override protected void setupDrawText(brachy.modularui.screen.viewport.ModularGuiContext context,
                                            brachy.modularui.theme.TextFieldTheme theme) {
        super.setupDrawText(context, theme);
        updateTextScroll();
    }
    /** Multiline editors keep both the hint and caret on the first row, even when expanded. */
    @Override protected int firstLineTop() {
        return io.github.luyingyilong.geg.client.ui.input.InputGeometry.firstLineTop(
                getArea().getPadding().top(), getArea().paddedHeight(), controlSize().height(), renderer.getFontHeight());
    }
    @Override protected void drawPlaceholder(ModularGuiContext context, Component placeholder) {
        int top = firstLineTop();
        int width = (int) Math.floor(((OreTextFieldRenderer) renderer).textWidth());
        int height = Math.max(0, getArea().height - getArea().getPadding().bottom() - top);
        if (height == 0) return;
        Label label = placeholderLabel(placeholder).wrap(Label.Wrap.WORD);
        label.maxLines(Math.max(1, height / label.lineHeight(context.getFont())));
        int left = getArea().getPadding().left();
        label.layout(new Rect(left, top, width, height), context.getFont());
        // Native field text uses a translated pose; GuiGraphics scissor coordinates remain global.
        Rect field = inputMethodBounds();
        context.getGraphics().enableScissor(field.x() + left, field.y() + top,
                field.x() + left + width, field.y() + top + height);
        if (debugControl() != null) DebugBridge.backend().internal(debugControl(),"placeholder",label);
        try { label.draw(context, 0, 0); }
        finally { context.getGraphics().disableScissor(); }
    }
    @Override public int preferredInputHeight() {
        int count = minimumRows == 0 ? rows : Math.max(minimumRows, Math.min(maximumRows, automaticRows));
        return controlSize().height() + (count - 1) * OreTextMetrics.body().lineHeight(scale);
    }
    @Override public void onUpdate() {
        super.onUpdate();
        if (pendingCursor >= 0 && isValid()) cursorOffset(pendingCursor);
        updateTextScroll();
        if (minimumRows > 0) {
            int next = Math.max(minimumRows, Math.min(maximumRows,
                    (int) Math.ceil(((OreTextFieldRenderer) renderer).visualHeight() / Math.max(1, renderer.getFontHeight()))));
            if (automaticRows != next) { automaticRows = next; invalidateInputLayout(); }
        }
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

    @Override protected void onEditShortcut() {
        super.onEditShortcut();
        updateTextScroll();
    }

    @Override public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (submit != null && inputMethodInteractive() && isFocused() && keyCode == GLFW.GLFW_KEY_ESCAPE && composing()) {
            cancelPreedit(); return Interactable.Result.STOP;
        }
        if (inputMethodInteractive() && isFocused() && !composing() && keyHandler.test(keyCode, modifiers))
            return Interactable.Result.STOP;
        if (submit != null && inputMethodInteractive() && isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
            if (!composing()) submit.run();
            return Interactable.Result.STOP;
        }
        if (inputMethodInteractive() && isFocused() && !composing()
                && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) == 0
                && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END)) {
            editingHandler().endEditGroup();
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
        editingHandler().clearHistory();
        handler.setCursor(new brachy.modularui.utils.Point(0, 0), false);
        lastCaret = null;
        scheduleResize();
    }
}
