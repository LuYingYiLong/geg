package io.github.luyingyilong.ore.control.input;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.utils.PointF;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.control.base.Rect;

import io.github.luyingyilong.ore.text.TextLines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * Multiline Ore editor; the existing MUI handler still owns selection, scrolling and IME commits.
 */
public final class TextArea extends Input {
    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) { io.github.luyingyilong.ore.theme.OreTheme.registerType(db,"TextArea"); }
    private int rows = 6;
    private PointF lastCaret;
    private int lastWidth = -1, lastHeight = -1;
    private float lastTextHeight = -1;
    private java.util.function.BiPredicate<Integer, Integer> keyHandler = (key, modifiers) -> false;
    private int minimumRows, maximumRows;
    private int automaticRows;
    private int pendingCursor = -1;

    public TextArea(float depth) {
        super(depth);
        handler.setMaxLines(2049);
        ((OreTextFieldRenderer) renderer).multiline(true);
        setMaxLength(4096);
        setPattern(java.util.regex.Pattern.compile(".*", java.util.regex.Pattern.DOTALL));
        setTextAlignment(brachy.modularui.utils.Alignment.TopLeft);
        getScrollArea().setScrollY(HiddenInputScroll.vertical());
    }

    @Override
    public TextArea setSubmitOnEnter(boolean value) {
        super.setSubmitOnEnter(value);
        return this;
    }

    public TextArea setKeyHandler(java.util.function.BiPredicate<Integer, Integer> handler) {
        keyHandler = java.util.Objects.requireNonNull(handler);
        return this;
    }

    public int getCursorOffset() {
        var caret = handler.getMainCursor();
        return TextLines.offset(handler.getText(), caret.x, caret.y);
    }

    public TextArea setCursorOffset(int offset) {
        if (!isValid()) {
            pendingCursor = Math.max(0, offset);
            return this;
        }
        pendingCursor = -1;
        var lines = handler.getText();
        if (lines.isEmpty()) lines.add("");
        int row = 0, column = Math.max(0, Math.min(getText().length(), offset));
        while (row < lines.size() - 1 && column > lines.get(row).length()) {
            column -= lines.get(row).length() + 1;
            row++;
        }
        editingHandler().endEditGroup();
        handler.setCursor(row, Math.min(column, lines.get(row).length()), true, false);
        lastCaret = null;
        return this;
    }

    /**
     * Completion is one undoable edit, preserving the rest of the document and its cursor.
     */
    public void applyCompletion(String value, int offset) {
        if (!editingHandler().replaceAll(value)) return;
        setCursorOffset(offset);
    }

    public TextArea setAutoHeight(int minimumRows, int maximumRows) {
        if (minimumRows < 1 || maximumRows > 20 || maximumRows < minimumRows)
            throw new IllegalArgumentException("Invalid row range");
        this.minimumRows = minimumRows;
        this.maximumRows = maximumRows;
        this.rows = minimumRows;
        return this;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public int getRows() { return rows; }

    public TextArea setRows(int rows) {
        if (rows < 1 || rows > 20) throw new IllegalArgumentException("Rows must be between 1 and 20");
        this.rows = rows;
        minimumRows = maximumRows = automaticRows = 0;
        invalidateInputLayout();
        return this;
    }

    @Override
    protected void setupDrawText(brachy.modularui.screen.viewport.ModularGuiContext context,
                                 brachy.modularui.theme.TextFieldTheme theme) {
        super.setupDrawText(context, theme);
        updateTextScroll();
    }

    /**
     * Multiline editors keep both the hint and caret on the first row, even when expanded.
     */
    @Override
    protected int firstLineTop() {
        return io.github.luyingyilong.ore.control.input.InputGeometry.firstLineTop(
                getArea().getPadding().top(), getArea().paddedHeight(), getControlSize().getHeight(), renderer.getFontHeight());
    }

    @Override
    protected void drawPlaceholder(ModularGuiContext context, Component placeholder) {
        int top = firstLineTop();
        int width = (int) Math.floor(((OreTextFieldRenderer) renderer).textWidth());
        int height = Math.max(0, getArea().height - getArea().getPadding().bottom() - top);
        if (height == 0) return;
        Label label = placeholderLabel(placeholder);
        label.setMaxLines(Math.max(1, height / label.lineHeight(context.getFont())));
        int left = getArea().getPadding().left();
        label.layout(new Rect(left, top, width, height), context.getFont());
        // Native field text uses a translated pose; GuiGraphics scissor coordinates remain global.
        Rect field = inputMethodBounds();
        Rect hintClip = toHost(new Rect(field.x() + left, field.y() + top, width, height));
        context.getGraphics().enableScissor(hintClip.x(), hintClip.y(), hintClip.right(), hintClip.bottom());
        if (debugControl() != null) DebugBridge.backend().internal(debugControl(), "placeholder", label);
        try {
            try (var frame = io.github.luyingyilong.ore.window.MuiDrawing.begin(context, getWindowContext())) {
            label.draw(frame, 0, 0);
        }
        } finally {
            context.getGraphics().disableScissor();
        }
    }

    @Override
    protected Label placeholderLabel(Component placeholder) {
        return super.placeholderLabel(placeholder).setWrap(Label.Wrap.WORD);
    }

    @Override
    public int getPreferredInputHeight() {
        int count = minimumRows == 0 ? rows : Math.max(minimumRows, Math.min(maximumRows, automaticRows));
        return getControlSize().getHeight() + (count - 1) * (int)Math.ceil(renderer.getFontHeight());
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (pendingCursor >= 0 && isValid()) setCursorOffset(pendingCursor);
        updateTextScroll();
        if (minimumRows > 0) {
            int next = Math.max(minimumRows, Math.min(maximumRows,
                    (int) Math.ceil(((OreTextFieldRenderer) renderer).visualHeight() / Math.max(1, renderer.getFontHeight()))));
            if (automaticRows != next) {
                automaticRows = next;
                invalidateInputLayout();
            }
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

    @Override
    protected void onEditShortcut() {
        super.onEditShortcut();
        updateTextScroll();
    }


    @Override
    protected boolean isSubmitKey(int keyCode, int modifiers) {
        return super.isSubmitKey(keyCode, modifiers) && (modifiers & GLFW.GLFW_MOD_SHIFT) == 0;
    }

    @Override
    public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!acceptsHostInput()) return Interactable.Result.IGNORE;
        if (getSubmitOnEnter() && inputMethodInteractive() && isFocused() && keyCode == GLFW.GLFW_KEY_ESCAPE && getComposing()) {
            cancelPreedit();
            return Interactable.Result.STOP;
        }
        if (inputMethodInteractive() && isFocused() && !getComposing() && keyHandler.test(keyCode, modifiers))
            return Interactable.Result.STOP;
        if (inputMethodInteractive() && isFocused() && !getComposing()
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

    @Override
    public int getMaxLines() {
        return 2049;
    }

    @Override
    public String getText() {
        return String.join("\n", handler.getText());
    }

    @Override
    public void setText(String text) {
        beginTextMutation();
        try {
            var lines = handler.getText();
            lines.clear();
            lines.addAll(TextLines.split(text));
            editingHandler().clearHistory();
            handler.setCursor(new brachy.modularui.utils.Point(0, 0), false);
            lastCaret = null;
            scheduleResize();
        } finally {
            endTextMutation();
        }
    }

    @Override
    protected void removeNativeFocus(brachy.modularui.screen.viewport.ModularGuiContext context) {
        // MUI's single-line value adapter rejects multiple source lines on blur. Pass it the
        // complete value as one string, then restore the editor's explicit newline structure.
        String text = getText();
        var lines = handler.getText();
        lines.clear();
        lines.add(text);
        try {
            super.removeNativeFocus(context);
        } finally {
            String value = String.join("\n", lines);
            lines.clear();
            lines.addAll(TextLines.split(value));
        }
    }
}
