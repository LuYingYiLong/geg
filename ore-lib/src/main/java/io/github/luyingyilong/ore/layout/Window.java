package io.github.luyingyilong.ore.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.ore.theme.OreFonts;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.window.*;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

/**
 * GUI-viewport root, not a native operating-system window.
 */
public final class Window extends Container {
    private final boolean fullscreen;
    private final FocusManager focusManager = new FocusManager(this);
    private final FocusOverlay focusOverlay = new FocusOverlay(focusManager);
    private WindowHost host;
    private boolean inspectable = true;
    private boolean automaticPopups;
    private boolean closedNotified;
    private Runnable onClosed = () -> {
    };
    private Control content;
    private Overlay overlay;
    private int fontRevision = -1;
    private Control pointerCapture;
    private Runnable onBack = OreScreen::back;

    public Window(boolean fullscreen) {
        this.fullscreen = fullscreen;
        host = new EmbeddedWindowHost();
        host.context().owner(this);
    }

    public static Window embedded() {
        Window root = new Window(true);
        root.automaticPopups = true;
        root.onBack = root::close;
        return root;
    }

    public static Window nativeWindow(WindowOptions options) {
        Window root = new Window(true);
        root.host = new NativeWindowHost(options);
        root.automaticPopups = true;
        root.context().owner(root);
        root.onBack = root::close;
        return root;
    }

    public WindowHost host() {
        return host;
    }

    public WindowContext context() {
        return host.context();
    }

    public Window open() {
        closedNotified = false;
        host.open(this);
        return this;
    }

    public void close() {
        if (closedNotified) return;
        closedNotified = true;
        cancelInteractions();
        DebugBridge.backend().close(this);
        host.close();
        onClosed.run();
    }

    public void hostClosed() {
        if (closedNotified) return;
        closedNotified = true;
        cancelInteractions();
        io.github.luyingyilong.ore.input.OreInputMethod.releaseWindow(context());
        DebugBridge.backend().close(this);
        if (host instanceof EmbeddedWindowHost embedded) embedded.detached();
        onClosed.run();
    }

    public void requestFocus() {
        host.requestFocus();
    }

    public Window onClosed(Runnable action) {
        onClosed = java.util.Objects.requireNonNull(action);
        return this;
    }

    public boolean inspectable() {
        return inspectable;
    }

    public Window inspectable(boolean value) {
        inspectable = value;
        return this;
    }

    public Window content(Control content) {
        if (this.content != null) removeChild(this.content);
        addChild(content);
        return this;
    }

    public Window overlay(Overlay overlay) {
        if (this.overlay != null) removeChild(this.overlay);
        addChild(overlay);
        return this;
    }

    @Override
    public Window addChild(Control child) {
        if (child instanceof Overlay layer) {
            if (overlay != null) throw new IllegalStateException("Window already has an overlay");
            super.addChild(layer);
            overlay = layer;
        } else {
            if (content != null) throw new IllegalStateException("Window already has content");
            super.addChild(child);
            content = child;
        }
        return this;
    }

    @Override
    public boolean removeChild(Control child) {
        if (!super.removeChild(child)) return false;
        if (child == content) content = null;
        if (child == overlay) overlay = null;
        return true;
    }

    public boolean fullscreen() {
        return fullscreen;
    }

    public FocusManager focusManager() {
        return focusManager;
    }

    public Window onBack(Runnable action) {
        onBack = java.util.Objects.requireNonNull(action);
        return this;
    }

    Control contentNode() {
        return content;
    }

    Overlay overlayLayer() {
        return overlay;
    }

    public boolean pointerPressed(int rootX, int rootY, ModularGuiContext context) {
        if (inspectable && DebugBridge.backend().picking(this)) return true;
        cancelPointer();
        if (automaticPopups && this.context().popups().press(rootX, rootY)) return true;
        focusManager.cancelActivation();
        Control hit = pointerTarget(rootX, rootY);
        focusManager.pointerPressed(hit, context);
        pointerCapture = hit;
        return dispatchPointer(rootX, rootY);
    }

    private Control pointerTarget(int rootX, int rootY) {
        Control hit = overlay == null ? null : overlay.hitTop(rootX, rootY);
        return hit == null && content != null ? content.hitTop(rootX, rootY) : hit;
    }

    public Control tooltipTarget(int rootX, int rootY) {
        if (automaticPopups && context().popups().open()) return null;
        if (!visible() || !hit(rootX, rootY)) return null;
        for (Control node = pointerTarget(rootX, rootY); node != null && node != this; node = node.parent()) {
            if (!node.visible()) return null;
            if (node.hasTooltip() && focusManager.allowsNativeInput(node)) return node;
        }
        return null;
    }

    private void drawTooltip(ModularGuiContext context, int screenX, int screenY) {
        int mouseX = context.getAbsMouseX(), mouseY = context.getAbsMouseY();
        Control target = tooltipTarget(mouseX - screenX, mouseY - screenY);
        if (target == null) return;
        float depth = 0.0F;
        for (Control node = target; node != null && node != this; node = node.parent()) depth += node.contentDepth();
        var graphics = context.getGraphics();
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0.0F, 0.0F, depth);
            target.renderTooltip(context, mouseX, mouseY);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    public void capturePointer(Control control) {
        if (attachedAndVisible(control)) pointerCapture = control;
    }

    public boolean pointerDragged(int rootX, int rootY) {
        if (pointerCapture == null) return false;
        if (!attachedAndVisible(pointerCapture) || !focusManager.allowsNativeInput(pointerCapture)) {
            cancelPointer();
            return false;
        }
        Rect point = capturedCoordinates(pointerCapture, rootX, rootY);
        return pointerCapture.dragPointer(point.x(), point.y());
    }

    /**
     * A Scroll transforms its content branch, not its own scrollbar branch.
     */
    private Rect capturedCoordinates(Control captured, int rootX, int rootY) {
        for (Control child = captured, node = captured.parent(); node != null;
             child = node, node = node.parent())
            if (node instanceof Scroll scroll && scroll.isContentChild(child)) {
                rootX += scroll.scrollX();
                rootY += scroll.scrollY();
            }
        return new Rect(rootX, rootY, 0, 0);
    }

    public boolean pointerReleased(int rootX, int rootY) {
        if (automaticPopups && context().popups().release(rootX, rootY)) return true;
        Control captured = pointerCapture;
        pointerCapture = null;
        if (captured == null) return false;
        Control hit = pointerTarget(rootX, rootY);
        boolean over = false;
        for (Control node = hit; node != null; node = node.parent())
            if (node == captured) {
                over = true;
                break;
            }
        if (!attachedAndVisible(captured) || !focusManager.allowsNativeInput(captured)) {
            captured.cancelInteraction();
            return false;
        }
        Rect point = capturedCoordinates(captured, rootX, rootY);
        return captured.releasePointer(point.x(), point.y(), over);
    }

    private boolean attachedAndVisible(Control target) {
        for (Control node = target; node != null; node = node.parent()) {
            if (!node.visible()) return false;
            if (node == this) return true;
        }
        return false;
    }

    private void cancelPointer() {
        if (pointerCapture != null) pointerCapture.cancelInteraction();
        pointerCapture = null;
    }

    @Override
    public void cancelInteraction() {
        context().popups().cancel();
        cancelPointer();
        focusManager.resetInput();
    }

    @Override
    public boolean dispatchScroll(int x, int y, double delta, boolean horizontal) {
        return automaticPopups && context().popups().scroll(delta) || super.dispatchScroll(x, y, delta, horizontal);
    }

    public boolean keyPressed(int keyCode, int modifiers, ModularGuiContext context) {
        if (focusManager.keyPressed(keyCode, modifiers, context)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onBack.run();
            return true;
        }
        return false;
    }

    public boolean keyReleased(int keyCode, int modifiers, ModularGuiContext context) {
        return focusManager.keyReleased(keyCode, modifiers, context);
    }

    public void layoutViewport(int width, int height, Font font) {
        if (pointerCapture != null && (!attachedAndVisible(pointerCapture)
                || !focusManager.allowsNativeInput(pointerCapture))) cancelPointer();
        if (fontRevision != OreFonts.revision()) {
            fontRevision = OreFonts.revision();
            invalidateTextLayout();
        }
        int actualWidth = fullscreen ? width : Math.min(width, measureWidth(font, width));
        int actualHeight = fullscreen ? height : Math.min(height, measureHeight(font, height));
        layout(new Rect(0, 0, actualWidth, actualHeight), font);
    }

    @Override
    public int measureWidth(Font font, int available) {
        return content == null ? 0 : content.measureWidth(font, available);
    }

    @Override
    public int measureHeight(Font font, int available) {
        return content == null ? 0 : content.measureHeight(font, available);
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        Rect full = new Rect(0, 0, localBounds.width(), localBounds.height());
        if (content != null) fitChild(content, full, font);
        if (overlay != null) layoutChild(overlay, full, font);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!visible()) return;
        this.context().bind(context);
        if (host instanceof EmbeddedWindowHost embedded) {
            closedNotified = false;
            embedded.attached();
            WindowManager.embedded(this);
            this.context().dimensions(context.getScreenArea().width, context.getScreenArea().height,
                    context.getScreenArea().width, context.getScreenArea().height, 1);
        }
        try (var frame = this.context().enter()) {
            DebugBridge.backend().begin(this, context, screenX, screenY);
            DebugBridge.backend().paint(this, context, screenX, screenY);
            Rect area = screenBounds(screenX, screenY);
            context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), OreTheme.OVERLAY_COLOR);
            if (content != null) content.draw(context, screenX, screenY);
            if (overlay != null) overlay.draw(context, screenX, screenY);
            if (automaticPopups) this.context().popups().draw(context, screenX, screenY);
            focusManager.synchronizeNativeFocus(context);
            focusOverlay.draw(context, screenX, screenY);
            drawTooltip(context, screenX, screenY);
            DebugBridge.backend().end(this, context, screenX, screenY);
        }
    }
}
