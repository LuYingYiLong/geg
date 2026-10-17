package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.theme.OreFonts;
import io.github.luyingyilong.geg.client.ui.screen.OreScreen;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

/** GUI-viewport root, not a native operating-system window. */
public final class Window extends Container {
    public static final int BACKGROUND_COLOR = 0x80000000;
    private final boolean fullscreen;
    private final FocusManager focusManager = new FocusManager(this);
    private final FocusOverlay focusOverlay = new FocusOverlay(focusManager);
    private Control content;
    private Overlay overlay;
    private int fontRevision = -1;
    private Control pointerCapture;
    private Runnable onBack = OreScreen::back;

    public Window(boolean fullscreen) { this.fullscreen = fullscreen; }
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
    @Override public Window addChild(Control child) {
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
    @Override public boolean removeChild(Control child) {
        if (!super.removeChild(child)) return false;
        if (child == content) content = null;
        if (child == overlay) overlay = null;
        return true;
    }
    public boolean fullscreen() { return fullscreen; }
    public FocusManager focusManager() { return focusManager; }
    public Window onBack(Runnable action) { onBack = java.util.Objects.requireNonNull(action); return this; }
    Control contentNode() { return content; }
    Overlay overlayLayer() { return overlay; }

    public boolean pointerPressed(int rootX, int rootY, ModularGuiContext context) {
        cancelPointer();
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

    /** A Scroll transforms its content branch, not its own scrollbar branch. */
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
        Control captured = pointerCapture;
        pointerCapture = null;
        if (captured == null) return false;
        Control hit = pointerTarget(rootX, rootY);
        boolean over = false;
        for (Control node = hit; node != null; node = node.parent())
            if (node == captured) { over = true; break; }
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

    @Override public void cancelInteraction() {
        cancelPointer();
        focusManager.resetInput();
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
        Rect area = screenBounds(screenX, screenY);
        context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), BACKGROUND_COLOR);
        if (content != null) content.draw(context, screenX, screenY);
        if (overlay != null) overlay.draw(context, screenX, screenY);
        focusManager.synchronizeNativeFocus(context);
        focusOverlay.draw(context, screenX, screenY);
    }
}
