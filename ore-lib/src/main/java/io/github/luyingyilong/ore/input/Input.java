package io.github.luyingyilong.ore.input;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.TextFieldTheme;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.signal.*;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.util.NativePaintPass;
import io.github.luyingyilong.ore.util.OreLayers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntPredicate;

public class Input extends TextFieldWidget implements SignalOwner {
    private static final InputShortcuts.Clipboard CLIPBOARD = new InputShortcuts.Clipboard() {
        @Override
        public String get() {
            return io.github.luyingyilong.ore.window.WindowContext.getCurrent().getClipboard();
        }

        @Override
        public void set(String text) {
            io.github.luyingyilong.ore.window.WindowContext.getCurrent().setClipboard(text);
        }
    };
    private final float depth;
    private final SignalScope signalScope = new SignalScope();
    private final Label placeholderLabel = new Label().setOverflow(Label.Overflow.VISIBLE);
    private final SignalSource<String> textChanged = new SignalSource<>(this, "textChanged", "String");
    private final SignalSource<String> submitted = new SignalSource<>(this, "submitted", "String");
    private final SignalSource0 pointerPressed = new SignalSource0(this, "pointerPressed");
    private final SignalSource0 focusEntered = new SignalSource0(this, "focusEntered");
    private final SignalSource0 focusExited = new SignalSource0(this, "focusExited");
    private final NativePaintPass paintPass = new NativePaintPass();
    private Button.Size controlSize = Button.Size.MEDIUM;
    private boolean inputVisible = true;
    private IntPredicate tabNavigation = modifiers -> false;
    private boolean submitOnEnter;
    private int silentTextDepth;
    private int textMutationDepth;
    private boolean signalFocused;
    private String observedText = "";
    private InputControl layoutControl;
    private boolean routingInput;

    public Input() {
        this(0.0F);
    }

    public Input(float depth) {
        this.depth = depth;
        // MUI dispose() can be a temporary detach; the signal scope is the permanent lifetime.
        signalScope.own(() -> {
            if (layoutControl == null) placeholderLabel.queueFree();
        });
        handler = new OreTextFieldHandler(this);
        renderer = new OreTextFieldRenderer(handler, this);
        handler.setRenderer(renderer);
        handler.setScrollArea(getScrollArea());
        getScrollArea().setScrollX(io.github.luyingyilong.ore.input.HiddenInputScroll.horizontal());
        // An explicit empty builder prevents TextFieldWidget.onInit from adding its overflow tooltip.
        tooltipBuilder(tooltip -> {
        });
        showScrollShadows(false);
    }

    @Override
    public final SignalScope getSignalScope() {
        return signalScope;
    }

    public final Signal<String> textChanged() {
        return textChanged.signal();
    }

    public final Signal<String> submitted() {
        return submitted.signal();
    }

    public final Signal0 pointerPressed() {
        return pointerPressed.signal();
    }

    public final Signal0 focusEntered() {
        return focusEntered.signal();
    }

    public final Signal0 focusExited() {
        return focusExited.signal();
    }

    public boolean getSubmitOnEnter() {
        return submitOnEnter;
    }

    public Input setSubmitOnEnter(boolean value) {
        submitOnEnter = value;
        return this;
    }

    protected final void submitText() {
        if (this instanceof NumberInput number && !number.commit()) return;
        submitted.emit(getText());
    }

    public final void setTextNoSignal(String text) {
        silentTextDepth++;
        try {
            setText(text);
        } finally {
            silentTextDepth--;
        }
    }

    @Override
    protected void onTextChanged() {
        super.onTextChanged();
        // ModularUI may invoke this hook while its superclass is being constructed.
        if (textChanged == null || textMutationDepth > 0) return;
        String text = getText();
        if (java.util.Objects.equals(observedText, text)) return;
        observedText = text;
        if (silentTextDepth == 0) textChanged.emit(text);
    }

    protected final OreTextFieldHandler editingHandler() {
        return (OreTextFieldHandler) handler;
    }

    @Override
    public void setText(String text) {
        beginTextMutation();
        try {
            super.setText(text);
            editingHandler().clearHistory();
            handler.setCursor(new brachy.modularui.utils.Point(0, 0), false);
        } finally {
            endTextMutation();
        }
    }

    protected final void beginTextMutation() {
        textMutationDepth++;
    }

    protected final void endTextMutation() {
        if (--textMutationDepth == 0) onTextChanged();
    }

    protected void onEditShortcut() {
        renderer.setCursor(true);
    }

    public int getPreferredInputHeight() {
        return controlSize.getHeight();
    }

    public Button.Size getControlSize() {
        return controlSize;
    }

    public Input setControlSize(Button.Size size) {
        controlSize = java.util.Objects.requireNonNull(size);
        height(size.getHeight());
        return this;
    }

    public Input setPlaceholder(Component placeholder) {
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

    void layoutControl(InputControl control) {
        layoutControl = control;
        control.ownPart("placeholder", placeholderLabel);
    }

    public final void releaseSession() {
        signalScope.checkThread();
        OreInputMethod.release(this);
        setInputVisible(false);
        signalScope.close(SignalDiagnostics.Reason.OWNER_DISPOSED);
    }

    /**
     * Validation text can change the owning FormItem's measured height.
     */
    protected final void invalidateInputLayout() {
        if (layoutControl != null) layoutControl.requestLayout();
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && inputVisible();
    }

    private boolean layoutInteractive() {
        return isEnabled() && (layoutControl == null || layoutControl.layoutInteractive());
    }

    protected boolean supportsInputMethod() {
        return true;
    }

    /**
     * Uncommitted text is separate from getText()/Form values and never sent to the server.
     */
    public io.github.luyingyilong.ore.window.WindowContext getWindowContext() {
        return layoutControl != null ? layoutControl.getWindowContext() : isValid()
                ? io.github.luyingyilong.ore.window.WindowContext.of(getContext())
                : io.github.luyingyilong.ore.window.WindowContext.getCurrent();
    }

    public InputPreedit getPreedit() {
        return OreInputMethod.preedit(this);
    }

    public boolean getComposing() {
        return OreInputMethod.composing(this);
    }

    public void cancelPreedit() {
        OreInputMethod.cancelComposition(this);
        clearPreeditDisplay();
    }

    void clearPreeditDisplay() {
        ((OreTextFieldRenderer) renderer).preedit(InputPreedit.EMPTY);
    }

    private void refreshPreeditDisplay() {
        ((OreTextFieldRenderer) renderer).preedit(OreInputMethod.inlinePreedit(this) ? getPreedit() : InputPreedit.EMPTY);
    }

    boolean inputMethodInteractive() {
        return layoutInteractive();
    }

    Rect inputMethodBounds() {
        return layoutControl == null ? new Rect(getArea().x, getArea().y, getArea().width, getArea().height)
                : layoutControl.getWidgetBounds();
    }

    int inputMethodFontHeight() {
        return (int) Math.ceil(renderer.getFontHeight());
    }

    Caret inputMethodCaret() {
        Rect area = inputMethodBounds();
        var cursor = ((OreTextFieldRenderer) renderer).caretPosition();
        Rect caret = toHost(new Rect(
                area.x() + Math.max(0, Math.min(area.width() - 2, (int) cursor.x)),
                area.y() + Math.max(0, Math.min(area.height(), (int) cursor.y - getScrollY() + inputMethodFontHeight())), 0, 0));
        return new Caret(caret.x(), caret.y());
    }

    /**
     * A tree field is painted/edited by Ore and only registered with MUI for lifecycle/focus.
     * It must not enter MUI's hovering list: even IGNORE stops at a non-click-through widget,
     * blocking the Canvas for pointer, key and character events whenever the mouse is over it.
     */
    @Override
    public boolean canHover() {
        return layoutControl == null && layoutInteractive() && super.canHover();
    }

    @Override
    public boolean canClickThrough() {
        return layoutControl != null || super.canClickThrough();
    }

    @Override
    public boolean isHovering() {
        if (layoutControl != null && routingInput)
            return layoutControl.pointerInteractive(getContext().getAbsMouseX(), getContext().getAbsMouseY());
        return super.isHovering();
    }

    @Override
    public boolean isBelowMouseFor(int button) {
        if (!layoutInteractive()) return false;
        if (layoutControl != null && !layoutControl.pointerInteractive(getContext().getAbsMouseX(), getContext().getAbsMouseY()))
            return false;
        Rect clip = layoutControl == null ? null : layoutControl.drawingClip();
        if (clip != null && !clip.contains(getContext().getAbsMouseX(), getContext().getAbsMouseY())) return false;
        return super.isBelowMouseFor(button);
    }

    @Override
    public void onUpdate() {
        if (layoutControl != null) layoutControl.syncWidgetArea();
        super.onUpdate();
    }

    public Input setTabNavigation(IntPredicate navigation) {
        tabNavigation = java.util.Objects.requireNonNull(navigation);
        return this;
    }


    @Override
    public void onFocus(ModularGuiContext context) {
        editingHandler().endEditGroup();
        super.onFocus(context);
        OreInputMethod.focus(this);
        if (!signalFocused) {
            signalFocused = true;
            focusEntered.emit();
        }
    }

    @Override
    public void onRemoveFocus(ModularGuiContext context) {
        editingHandler().endEditGroup();
        OreInputMethod.release(this);
        removeNativeFocus(context);
        if (signalFocused) {
            signalFocused = false;
            focusExited.emit();
        }
    }

    protected void removeNativeFocus(ModularGuiContext context) {
        super.onRemoveFocus(context);
    }

    @Override
    public void dispose() {
        OreInputMethod.release(this);
        super.dispose();
    }

    protected boolean submitsOnEnter() {
        return submitOnEnter;
    }

    protected boolean isSubmitKey(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
    }

    protected final boolean acceptsHostInput() {
        return layoutControl == null || routingInput;
    }

    /**
     * Only InputControl calls this; native callbacks on tree fields cannot edit twice.
     */
    final void receive(io.github.luyingyilong.ore.event.InputEvent event) {
        // Prepare the exact row geometry used by drawing before hit testing. A scroll,
        // modal move or resize may have happened since the last painted frame.
        setupDrawText(getContext(), getWidgetTheme(getPanel().getTheme(), TextFieldTheme.class).theme());
        // MUI editing expects its own local matrix, even when a Canvas delivered the event.
        var located = brachy.modularui.screen.viewport.LocatedWidget.of(this);
        located.applyMatrix(getContext());
        int hostMouseX = getContext().getAbsMouseX(), hostMouseY = getContext().getAbsMouseY();
        if (layoutControl != null && (event instanceof io.github.luyingyilong.ore.event.PointerEvent
                || event instanceof io.github.luyingyilong.ore.event.ScrollEvent)) {
            var field = layoutControl.getWidgetBounds();
            var tree = layoutControl.getAbsoluteBounds();
            getContext().updateState(event.treeX() - tree.x() + field.x(), event.treeY() - tree.y() + field.y(), 0);
        }
        routingInput = true;
        try {
            boolean handled = false;
            if (event instanceof io.github.luyingyilong.ore.event.PointerEvent pointer) {
                switch (pointer.action()) {
                    case PRESS -> handled = onMousePressed(pointer.button()) != Interactable.Result.IGNORE;
                    case RELEASE -> handled = onMouseReleased(pointer.button());
                    case MOVE -> {
                        onMouseDrag(pointer.button(), 0, 0);
                        handled = true;
                    }
                }
            } else if (event instanceof io.github.luyingyilong.ore.event.KeyEvent key) {
                if (key.action() == io.github.luyingyilong.ore.event.KeyEvent.Action.REPEAT && submitsOnEnter()
                        && (key.keyCode() == GLFW.GLFW_KEY_ENTER || key.keyCode() == GLFW.GLFW_KEY_KP_ENTER)
                        && (key.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0) {
                    event.accept();
                    return;
                }
                if (key.keyCode() == GLFW.GLFW_KEY_TAB && !getComposing()) return;
                handled = key.action() == io.github.luyingyilong.ore.event.KeyEvent.Action.RELEASE
                        ? onKeyReleased(key.keyCode(), key.scanCode(), key.modifiers())
                        : onKeyPressed(key.keyCode(), key.scanCode(), key.modifiers()) != Interactable.Result.IGNORE;
            } else if (event instanceof io.github.luyingyilong.ore.event.TextInputEvent text) {
                for (char character : text.text().toCharArray()) onCharTyped(character, text.modifiers());
                handled = true;
            } else if (event instanceof io.github.luyingyilong.ore.event.ScrollEvent scroll)
                handled = onMouseScrolled(scroll.delta());
            else if (event instanceof io.github.luyingyilong.ore.event.CompositionEvent composition) {
                if (composition.action() == io.github.luyingyilong.ore.event.CompositionEvent.Action.CANCEL)
                    cancelPreedit();
                else ((OreTextFieldRenderer) renderer).preedit(composition.preedit());
                handled = true;
            }
            if (handled) event.accept();
        } finally {
            routingInput = false;
            located.unapplyMatrix(getContext());
            getContext().updateState(hostMouseX, hostMouseY, 0);
        }
    }

    @Override
    public boolean onMouseReleased(int button) {
        return acceptsHostInput() && super.onMouseReleased(button);
    }

    @Override
    public void onMouseDrag(int button, double x, double y) {
        if (acceptsHostInput()) super.onMouseDrag(button, x, y);
    }

    @Override
    public boolean onMouseScrolled(double delta) {
        return acceptsHostInput() && super.onMouseScrolled(delta);
    }

    @Override
    public boolean onKeyReleased(int key, int scan, int modifiers) {
        return acceptsHostInput() && super.onKeyReleased(key, scan, modifiers);
    }

    @Override
    public @NotNull Interactable.Result onMousePressed(int button) {
        if (!acceptsHostInput()) return Interactable.Result.IGNORE;
        if (!layoutInteractive() || layoutControl != null
                && !layoutControl.pointerInteractive(getContext().getAbsMouseX(), getContext().getAbsMouseY()))
            return Interactable.Result.IGNORE;
        editingHandler().endEditGroup();
        if (isFocused() && getComposing()) cancelPreedit();
        Interactable.Result result = super.onMousePressed(button);
        if (result != Interactable.Result.IGNORE) pointerPressed.emit();
        return result;
    }

    @Override
    public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!acceptsHostInput()) return Interactable.Result.IGNORE;
        if (!layoutInteractive()) return Interactable.Result.IGNORE;
        if (isFocused() && InputShortcuts.matches(keyCode, modifiers)) {
            if (getComposing()) {
                cancelPreedit();
                if (keyCode == GLFW.GLFW_KEY_Z) return Interactable.Result.SUCCESS;
            }
            InputShortcuts.dispatch(editingHandler(), keyCode, modifiers, CLIPBOARD);
            onEditShortcut();
            return Interactable.Result.SUCCESS;
        }
        if (isFocused() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (getComposing()) {
                cancelPreedit();
                return Interactable.Result.STOP;
            }
            getContext().removeFocus();
            return Interactable.Result.STOP;
        }
        if (isFocused() && (keyCode >= GLFW.GLFW_KEY_ESCAPE && keyCode <= GLFW.GLFW_KEY_END
                || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && OreInputMethod.composing(this)) return Interactable.Result.STOP;
        if (submitOnEnter && isFocused() && isSubmitKey(keyCode, modifiers)) {
            if (!getComposing()) submitText();
            return Interactable.Result.STOP;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && isFocused() && tabNavigation.test(modifiers))
            return Interactable.Result.STOP;
        return super.onKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public @NotNull Interactable.Result onCharTyped(char character, int modifiers) {
        if (!acceptsHostInput()) return Interactable.Result.IGNORE;
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
        } else if (!hasCanvasTransform()) {
            super.preDraw(context, false);
        }
    }

    @Override
    public void postDraw(ModularGuiContext context, boolean drawText) {
        // Pair the native text clip, but omit AbstractScrollWidget's scrollbar/shadow painting.
        if (paintPass.accepts(layoutControl != null) && !drawText && !hasCanvasTransform()) context.getStencil().pop();
    }

    /**
     * Paint at the InputControl's position in the tree, before its Scroll paints the bars.
     */
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

    /**
     * A single-line field centers its hint within its actual, possibly expanded, height.
     */
    protected int firstLineTop() {
        return io.github.luyingyilong.ore.input.InputGeometry.centeredLineTop(
                getArea().getPadding().top(), getArea().paddedHeight(), renderer.getFontHeight());
    }

    @Override
    protected void setupDrawText(ModularGuiContext context, TextFieldTheme theme) {
        super.setupDrawText(context, theme);
        renderer.setPos(getArea().getPadding().left(), firstLineTop());
    }

    @Override
    protected void drawText(ModularGuiContext context, TextFieldTheme theme) {
        Component placeholder = hintText;
        hintText = null;
        try {
            if (placeholder != null && handler.isTextEmpty() && !getComposing()) {
                drawPlaceholder(context, placeholder);
            }
            // The editor paints its caret last, above the placeholder and committed text.
            super.drawText(context, theme);
        } finally {
            hintText = placeholder;
        }
    }

    io.github.luyingyilong.ore.layout.Control debugControl() {
        return layoutControl;
    }

    protected Label placeholderLabel(Component placeholder) {
        int color = hintTextColor == null ? OreTheme.TEXT_SECONDARY : hintTextColor;
        // Keep a snapshot so in-place edits to a MutableComponent also invalidate styled text.
        if (!placeholderLabel.getText().equals(placeholder)) placeholderLabel.setText(placeholder.copy());
        return placeholderLabel.setColor(color).setScale(scale);
    }

    protected void drawPlaceholder(ModularGuiContext context, Component placeholder) {
        int top = firstLineTop();
        Label label = placeholderLabel(placeholder);
        label.layout(new Rect(getArea().getPadding().left(), top, getArea().paddedWidth(),
                Math.max(0, Math.min(controlSize.getHeight(), getArea().height - top))), context.getFont());
        if (layoutControl != null) DebugBridge.backend().internal(layoutControl, "placeholder", label);
        try (var frame = io.github.luyingyilong.ore.window.MuiDrawing.begin(context, getWindowContext())) {
            label.draw(frame, 0, 0);
        }
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
        if (clip != null) {
            graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
            DebugBridge.backend().clip(clip);
        }
        Rect fieldClip = toHost(inputMethodBounds());
        graphics.enableScissor(fieldClip.x(), fieldClip.y(), fieldClip.right(), fieldClip.bottom());
        DebugBridge.backend().clip(fieldClip);
        try {
            draw.run();
            graphics.flush();
        } finally {
            graphics.disableScissor();
            DebugBridge.backend().unclip();
            if (clip != null) {
                graphics.disableScissor();
                DebugBridge.backend().unclip();
            }
            graphics.pose().popPose();
        }
    }

    private boolean hasCanvasTransform() {
        return layoutControl != null && io.github.luyingyilong.ore.window.CanvasRegion.find(layoutControl) != null;
    }

    protected final Rect toHost(Rect raw) {
        var region = layoutControl == null ? null : io.github.luyingyilong.ore.window.CanvasRegion.find(layoutControl);
        return region == null ? raw : region.getTransform().toScreen(raw);
    }

    record Caret(int x, int y) {
    }
}
