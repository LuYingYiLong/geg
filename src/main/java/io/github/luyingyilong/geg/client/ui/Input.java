package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import io.github.luyingyilong.geg.client.ui.layout.Rect;

import java.util.function.IntPredicate;

public class Input extends TextFieldWidget {
    private final float depth;
    private Button.Size controlSize = Button.Size.MEDIUM;
    private boolean inputVisible = true;
    private IntPredicate tabNavigation = modifiers -> false;
    private Runnable mousePressHandler = () -> { };
    private Runnable focusLostHandler = () -> { };
    private InputControl layoutControl;

    public Input() {
        this(0.0F);
    }

    public Input(float depth) {
        this.depth = depth;
        renderer = new OreTextFieldRenderer(handler);
        handler.setRenderer(renderer);
    }

    public Input controlSize(Button.Size size) {
        controlSize = java.util.Objects.requireNonNull(size);
        height(size.height());
        return this;
    }

    public Button.Size controlSize() {
        return controlSize;
    }

    public Input placeholder(Component placeholder) {
        hintText(java.util.Objects.requireNonNull(placeholder));
        hintColor(0xFFA9AAAC);
        return this;
    }

    public void setInputVisible(boolean visible) {
        inputVisible = visible;
        setEnabled(visible);
        if (!visible) OreInputMethod.release(this);
    }

    public boolean inputVisible() {
        return inputVisible && (layoutControl == null || layoutControl.layoutVisible());
    }

    void layoutControl(InputControl control) { layoutControl = control; }

    @Override public boolean isEnabled() { return super.isEnabled() && inputVisible(); }

    private boolean layoutInteractive() {
        return isEnabled() && (layoutControl == null || layoutControl.layoutInteractive());
    }

    protected boolean supportsInputMethod() { return true; }
    /** Uncommitted text is separate from getText()/Form values and never sent to the server. */
    public InputPreedit preedit() { return OreInputMethod.preedit(this); }
    public boolean composing() { return OreInputMethod.composing(this); }
    public void cancelPreedit() {
        OreInputMethod.cancelComposition(this);
        clearPreeditDisplay();
    }
    void clearPreeditDisplay() { ((OreTextFieldRenderer) renderer).preedit(InputPreedit.EMPTY); }
    private void refreshPreeditDisplay() {
        ((OreTextFieldRenderer) renderer).preedit(OreInputMethod.inlinePreedit(this) ? preedit() : InputPreedit.EMPTY);
    }
    boolean inputMethodInteractive() { return layoutInteractive(); }
    Rect inputMethodBounds() {
        return layoutControl == null ? new Rect(getArea().x, getArea().y, getArea().width, getArea().height)
                : layoutControl.displayedBounds();
    }
    int inputMethodFontHeight() { return (int) Math.ceil(renderer.getFontHeight()); }
    record Caret(int x, int y) { }
    Caret inputMethodCaret() {
        Rect area = inputMethodBounds();
        var cursor = ((OreTextFieldRenderer) renderer).caretPosition();
        return new Caret(
                area.x() + Math.max(0, Math.min(area.width() - 2, (int) cursor.x)),
                area.y() + Math.max(0, Math.min(area.height(), (int) cursor.y + inputMethodFontHeight())));
    }

    @Override public boolean canHover() { return layoutInteractive() && super.canHover(); }

    @Override public boolean isBelowMouseFor(int button) {
        if (!layoutInteractive()) return false;
        Rect clip = layoutControl == null ? null : layoutControl.drawingClip();
        if (clip != null && !clip.contains(getContext().getAbsMouseX(), getContext().getAbsMouseY())) return false;
        return super.isBelowMouseFor(button);
    }

    @Override public void onUpdate() {
        if (layoutControl != null) layoutControl.syncWidgetArea();
        super.onUpdate();
    }

    public Input tabNavigation(IntPredicate navigation) {
        tabNavigation = java.util.Objects.requireNonNull(navigation);
        return this;
    }

    public Input mousePressHandler(Runnable handler) {
        mousePressHandler = java.util.Objects.requireNonNull(handler);
        return this;
    }

    public Input focusLostHandler(Runnable handler) {
        focusLostHandler = java.util.Objects.requireNonNull(handler);
        return this;
    }

    @Override public void onFocus(ModularGuiContext context) {
        super.onFocus(context);
        OreInputMethod.focus(this);
    }

    @Override public void onRemoveFocus(ModularGuiContext context) {
        OreInputMethod.release(this);
        super.onRemoveFocus(context);
        focusLostHandler.run();
    }

    @Override public void dispose() {
        OreInputMethod.release(this);
        super.dispose();
    }

    @Override public @NotNull Interactable.Result onMousePressed(int button) {
        if (!layoutInteractive()) return Interactable.Result.IGNORE;
        if (isFocused() && composing()) cancelPreedit();
        Interactable.Result result = super.onMousePressed(button);
        if (result != Interactable.Result.IGNORE) mousePressHandler.run();
        return result;
    }

    @Override public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!layoutInteractive()) return Interactable.Result.IGNORE;
        if (isFocused() && keyCode == GLFW.GLFW_KEY_ESCAPE && composing()) {
            cancelPreedit();
            return Interactable.Result.STOP;
        }
        if (isFocused() && (keyCode >= GLFW.GLFW_KEY_ESCAPE && keyCode <= GLFW.GLFW_KEY_END
                || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && OreInputMethod.composing(this)) return Interactable.Result.STOP;
        if (keyCode == GLFW.GLFW_KEY_TAB && isFocused() && tabNavigation.test(modifiers))
            return Interactable.Result.STOP;
        return super.onKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override public @NotNull Interactable.Result onCharTyped(char character, int modifiers) {
        if (!layoutInteractive()) return Interactable.Result.IGNORE;
        // IMBlocker probes custom screens with NUL to discover their real text field focus.
        if (character == 0 && isFocused()) OreInputMethod.locateFocus(this);
        return super.onCharTyped(character, modifiers);
    }

    @Override
    public void preDraw(ModularGuiContext context, boolean drawText) {
        if (inputVisible()) refreshPreeditDisplay();
        if (drawText) {
            if (inputVisible()) {
                withDepth(context, () -> super.preDraw(context, true));
            }
        } else {
            super.preDraw(context, false);
        }
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        // The parent canvas may draw after the widget background pass.
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (inputVisible()) {
            refreshPreeditDisplay();
            withDepth(context, () -> {
                WidgetThemeEntry<WidgetTheme> inputEntry = context.getTheme()
                        .getWidgetTheme(OreTheme.INPUT_BOX);
                if (inputEntry != null) {
                    WidgetTheme inputTheme = inputEntry.theme();
                    IDrawable background = inputTheme.getBackground();
                    if (background != null) {
                        background.drawAtZero(context, getArea(), inputTheme);
                    }
                }
                super.draw(context, theme);
            });
        }
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (inputVisible()) {
            withDepth(context, () -> super.drawOverlay(context, theme));
        }
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        if (inputVisible()) {
            withDepth(context, () -> super.drawForeground(context));
        }
    }

    private void withDepth(ModularGuiContext context, Runnable draw) {
        GuiGraphics graphics = context.getGraphics();
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F,
                layoutControl == null ? depth : layoutControl.drawingDepth(depth));
        Rect clip = layoutControl == null ? null : layoutControl.drawingClip();
        if (clip != null) graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        Rect fieldClip = inputMethodBounds();
        graphics.enableScissor(fieldClip.x(), fieldClip.y(), fieldClip.right(), fieldClip.bottom());
        try {
            draw.run();
            graphics.flush();
        } finally {
            graphics.disableScissor();
            if (clip != null) graphics.disableScissor();
            graphics.pose().popPose();
        }
    }
}
