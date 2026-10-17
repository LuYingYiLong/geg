package io.github.luyingyilong.geg.client.ui.input;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.TextFieldTheme;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import io.github.luyingyilong.geg.client.ui.Button;
import io.github.luyingyilong.geg.client.ui.Label;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import io.github.luyingyilong.geg.client.ui.util.NativePaintPass;
import io.github.luyingyilong.geg.client.ui.util.OreLayers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntPredicate;

public class Input extends TextFieldWidget {
    private static final InputShortcuts.Clipboard CLIPBOARD = new InputShortcuts.Clipboard() {
        @Override public String get() { return net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard(); }
        @Override public void set(String text) { net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text); }
    };
    private final float depth;
    private Button.Size controlSize = Button.Size.MEDIUM;
    private boolean inputVisible = true;
    private IntPredicate tabNavigation = modifiers -> false;
    private Runnable mousePressHandler = () -> { };
    private Runnable focusLostHandler = () -> { };
    private InputControl layoutControl;
    private final NativePaintPass paintPass = new NativePaintPass();

    public Input() {
        this(0.0F);
    }

    public Input(float depth) {
        this.depth = depth;
        handler = new OreTextFieldHandler(this);
        renderer = new OreTextFieldRenderer(handler);
        handler.setRenderer(renderer);
        handler.setScrollArea(getScrollArea());
        getScrollArea().setScrollX(io.github.luyingyilong.geg.client.ui.input.HiddenInputScroll.horizontal());
        // An explicit empty builder prevents TextFieldWidget.onInit from adding its overflow tooltip.
        tooltipBuilder(tooltip -> { });
        showScrollShadows(false);
    }

    protected final OreTextFieldHandler editingHandler() { return (OreTextFieldHandler) handler; }

    @Override public void setText(String text) {
        super.setText(text);
        editingHandler().clearHistory();
        handler.setCursor(new brachy.modularui.utils.Point(0, 0), false);
    }

    protected void onEditShortcut() { renderer.setCursor(true); }

    public Input controlSize(Button.Size size) {
        controlSize = java.util.Objects.requireNonNull(size);
        height(size.height());
        return this;
    }

    public int preferredInputHeight() { return controlSize.height(); }

    public Button.Size controlSize() {
        return controlSize;
    }

    public Input placeholder(Component placeholder) {
        hintText(java.util.Objects.requireNonNull(placeholder));
        hintColor(OreTheme.TEXT_SECONDARY);
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
                area.y() + Math.max(0, Math.min(area.height(), (int) cursor.y - getScrollY() + inputMethodFontHeight())));
    }

    @Override public boolean canHover() { return layoutInteractive() && super.canHover(); }

    @Override public boolean isBelowMouseFor(int button) {
        if (!layoutInteractive()) return false;
        if (layoutControl != null && !layoutControl.pointerInteractive(getContext().getAbsMouseX(), getContext().getAbsMouseY())) return false;
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
        editingHandler().endEditGroup();
        super.onFocus(context);
        OreInputMethod.focus(this);
    }

    @Override public void onRemoveFocus(ModularGuiContext context) {
        editingHandler().endEditGroup();
        OreInputMethod.release(this);
        removeNativeFocus(context);
        focusLostHandler.run();
    }

    protected void removeNativeFocus(ModularGuiContext context) { super.onRemoveFocus(context); }

    @Override public void dispose() {
        OreInputMethod.release(this);
        super.dispose();
    }

    @Override public @NotNull Interactable.Result onMousePressed(int button) {
        if (!layoutInteractive() || layoutControl != null
                && !layoutControl.pointerInteractive(getContext().getAbsMouseX(), getContext().getAbsMouseY())) return Interactable.Result.IGNORE;
        editingHandler().endEditGroup();
        if (isFocused() && composing()) cancelPreedit();
        Interactable.Result result = super.onMousePressed(button);
        if (result != Interactable.Result.IGNORE) mousePressHandler.run();
        return result;
    }

    @Override public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!layoutInteractive()) return Interactable.Result.IGNORE;
        if (isFocused() && InputShortcuts.matches(keyCode, modifiers)) {
            if (composing()) {
                cancelPreedit();
                if (keyCode == GLFW.GLFW_KEY_Z) return Interactable.Result.SUCCESS;
            }
            InputShortcuts.dispatch(editingHandler(), keyCode, modifiers, CLIPBOARD);
            onEditShortcut();
            return Interactable.Result.SUCCESS;
        }
        if (isFocused() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (composing()) cancelPreedit();
            getContext().removeFocus();
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
        if (!isFocused()) return Interactable.Result.IGNORE;
        return InputShortcuts.type(editingHandler(), character) ? Interactable.Result.SUCCESS : Interactable.Result.STOP;
    }

    @Override
    public void preDraw(ModularGuiContext context, boolean drawText) {
        if (!paintPass.accepts(layoutControl != null)) return;
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
    public void postDraw(ModularGuiContext context, boolean drawText) {
        // Pair the native text clip, but omit AbstractScrollWidget's scrollbar/shadow painting.
        if (paintPass.accepts(layoutControl != null) && !drawText) context.getStencil().pop();
    }

    /** Paint at the InputControl's position in the tree, before its Scroll paints the bars. */
    void drawInTree(ModularGuiContext context) {
        if (!inputVisible()) return;
        GuiGraphics graphics = context.getGraphics();
        graphics.flush();
        graphics.pose().pushPose();
        try {
            // drawTree applies the MUI context matrix itself. Remove only that matrix here,
            // retaining the enclosing Drawer/Dialog pose and the active Ore viewport clips.
            graphics.pose().mulPoseMatrix(new Matrix4f(context.peek().getMatrix()).invert());
            paintPass.paint(() -> WidgetTree.drawTree(this, context));
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    /** A single-line field centers its hint within its actual, possibly expanded, height. */
    protected int firstLineTop() {
        return io.github.luyingyilong.geg.client.ui.input.InputGeometry.centeredLineTop(
                getArea().getPadding().top(), getArea().paddedHeight(), renderer.getFontHeight());
    }

    @Override protected void drawText(ModularGuiContext context, TextFieldTheme theme) {
        Component placeholder = hintText;
        hintText = null;
        try { super.drawText(context, theme); }
        finally { hintText = placeholder; }
        if (placeholder == null || !handler.isTextEmpty() || composing()) return;
        int top = firstLineTop();
        int color = hintTextColor == null ? OreTheme.TEXT_SECONDARY : hintTextColor;
        Label label = new Label(placeholder).color(color).scale(scale).overflow(Label.Overflow.VISIBLE);
        label.layout(new Rect(getArea().getPadding().left(), top, getArea().paddedWidth(),
                Math.max(0, Math.min(controlSize.height(), getArea().height - top))), context.getFont());
        label.draw(context, 0, 0);
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        // Background and text belong to the same Ore-owned pass, not MUI's sibling background pass.
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (inputVisible() && paintPass.accepts(layoutControl != null)) {
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
        if (inputVisible() && paintPass.accepts(layoutControl != null)) {
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
                paintPass.active() ? OreLayers.INPUT : layoutControl == null ? depth : layoutControl.drawingDepth());
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
