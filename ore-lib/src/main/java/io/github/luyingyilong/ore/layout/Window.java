package io.github.luyingyilong.ore.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.ore.signal.DispatchGuard;
import io.github.luyingyilong.ore.signal.Signal0;
import io.github.luyingyilong.ore.signal.SignalDiagnostics;
import io.github.luyingyilong.ore.signal.SignalSource0;
import io.github.luyingyilong.ore.theme.OreFonts;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.window.*;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Control-tree root shared by embedded and native WindowHost implementations.
 */
public final class Window extends Container {
    private final boolean fullscreen;
    private final FocusManager focusManager = new FocusManager(this);
    private final FocusOverlay focusOverlay = ownPart("focusOverlay", new FocusOverlay(focusManager));
    private final SignalSource0 closed = new SignalSource0(this, "closed");
    private final Map<Integer, Control> pointerCaptures = new HashMap<>();
    private WindowHost host;
    private boolean inspectable = true;
    private boolean closedNotified;
    private boolean backEnabled = true;
    private Control content;
    private Overlay overlay;
    private int fontRevision = -1;
    private InputEvent dispatching;
    private Thread inputThread;
    private boolean wasPicking;
    private int lastPointerX = Integer.MIN_VALUE, lastPointerY = Integer.MIN_VALUE;
    private Runnable onBack = OreScreen::back;

    public Window(boolean fullscreen) {
        this.fullscreen = fullscreen;
        host = new EmbeddedWindowHost();
        host.getContext().setOwner(this);
        getSignalScope().own(this::releaseHost);
    }

    public static Window embedded() {
        Window root = new Window(true);
        root.onBack = root::close;
        return root;
    }

    public static Window nativeWindow(WindowOptions options) {
        Window root = new Window(true);
        root.host = new NativeWindowHost(options);
        root.getContext().setOwner(root);
        root.onBack = root::close;
        return root;
    }

    /** A coordinating root on an existing Screen; it never owns that Screen's navigation. */
    public static Window external(ScreenWindowHost host) {
        Window root = new Window(true);
        root.host = java.util.Objects.requireNonNull(host);
        root.getContext().setOwner(root);
        root.backEnabled = false;
        return root;
    }

    public WindowHost getHost() {
        return host;
    }

    public WindowContext getContext() {
        return host.getContext();
    }

    public Window open() {
        if (!isAlive()) throw new IllegalStateException("Cannot open a pending/freed window");
        closedNotified = false;
        host.open(this);
        return this;
    }

    public void close() {
        if (closedNotified) return;
        closedNotified = true;
        cancelInteractions();
        io.github.luyingyilong.ore.input.OreInputMethod.releaseWindow(getContext());
        DebugBridge.backend().close(this);
        host.close();
        closed.emit();
    }

    public void hostClosed() {
        if (closedNotified) return;
        closedNotified = true;
        cancelInteractions();
        io.github.luyingyilong.ore.input.OreInputMethod.releaseWindow(getContext());
        DebugBridge.backend().close(this);
        if (host instanceof EmbeddedWindowHost embedded) embedded.detached();
        closed.emit();
    }

    /**
     * MUI releases panels while retaining their Screen for back navigation.
     */
    public void hostDetached() {
        suspendTree(true);
        cancelInteractions();
        io.github.luyingyilong.ore.input.OreInputMethod.releaseWindow(getContext());
        if (host instanceof EmbeddedWindowHost embedded) embedded.detached();
    }

    public void prepareTree(ModularGuiContext mui) {
        getContext().bind(mui);
        if (host instanceof EmbeddedWindowHost embedded) {
            embedded.attached();
            WindowManager.embedded(this);
        }
        prepareTree();
    }

    public void prepareTree() {
        if (!isAlive()) return;
        if (!isInsideTree()) enterTree(getContext(), false, true);
        else suspendTree(false);
        LifecycleScheduler.prepare(this);
        LifecycleScheduler.notifications();
    }

    public void requestFocus() {
        host.requestFocus();
    }

    public void setSuspended(boolean suspended) {
        suspendTree(suspended);
    }

    public Signal0 closed() {
        return closed.signal();
    }

    @Override
    protected void notification(ControlNotification notice) {
        if (notice.type() == ControlNotification.Type.PRE_FREE) hostClosed();
    }

    private void releaseHost() {
        getContext().getPopups().cancel();
        hostClosed();
        DebugBridge.backend().close(this);
        if (host instanceof EmbeddedWindowHost embedded) embedded.detached();
        else host.close();
    }

    public boolean getInspectable() {
        return inspectable;
    }

    public Window setInspectable(boolean value) {
        inspectable = value;
        return this;
    }

    public Window setContent(Control content) {
        if (content instanceof Overlay) throw new IllegalArgumentException("Use setOverlay for an Overlay");
        replaceChild(this.content, content);
        return this;
    }

    public Window setOverlay(Overlay overlay) {
        replaceChild(this.overlay, overlay);
        return this;
    }

    @Override
    protected void validateChildren(List<Control> proposed) {
        long overlays = proposed.stream().filter(Overlay.class::isInstance).count();
        if (overlays > 1 || proposed.size() - overlays > 1)
            throw new IllegalStateException("Window accepts one content and one Overlay; use setContent/setOverlay");
    }

    @Override
    protected void childrenChanged() {
        content = null;
        overlay = null;
        for (Control child : getChildren()) {
            if (child instanceof Overlay layer) overlay = layer;
            else content = child;
        }
    }

    public Overlay getPopupOverlay() {
        if (overlay == null) setOverlay(new Overlay());
        return overlay;
    }

    public boolean getFullscreen() {
        return fullscreen;
    }

    public FocusManager getFocusManager() {
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

    /**
     * The sole event entrance for embedded, native and business hosts.
     */
    public boolean dispatchInput(InputEvent event) {
        if (!isAlive() || isSuspended()) return false;
        if (event.context() != getContext()) throw new IllegalArgumentException("Event belongs to another Window");
        if (event.dispatched()) throw new IllegalStateException("InputEvent already dispatched");
        if (inputThread == null) inputThread = Thread.currentThread();
        if (Thread.currentThread() != inputThread)
            throw new IllegalStateException("Input must run on the client thread");
        if (dispatching != null) throw new IllegalStateException("Reentrant input dispatch");
        try (var lifecycleGuard = DispatchGuard.enter(); var ignored = getContext().enter(); var trace = SignalDiagnostics.suppress(!inspectable)) {
            dispatching = event;
            if (inspectable && event instanceof KeyEvent shortcut && shortcut.action() == KeyEvent.Action.PRESS
                    && DebugBridge.handleHostShortcut(shortcut.keyCode(), shortcut.scanCode())) {
                event.begin(this);
                event.accept();
                return true;
            }
            getContext().setModifiers(event.modifiers());
            if (event instanceof KeyEvent key)
                getContext().key(key.keyCode(), key.action() == KeyEvent.Action.RELEASE ? 0 : 1, key.modifiers());
            getContext().getPopups().synchronize();
            validateCaptures();
            if (event instanceof CancelEvent) {
                event.begin(this);
                cancelInteractions();
                event.accept();
                return true;
            }
            boolean picking = inspectable && DebugBridge.backend().picking(this);
            if (picking != wasPicking) {
                cancelPointer();
                focusManager.cancelActivation();
                if (picking && focusManager.getFocused() instanceof io.github.luyingyilong.ore.input.InputControl field)
                    field.getWidget().cancelPreedit();
                wasPicking = picking;
            }
            boolean intercepted = picking;
            if (inspectable && event instanceof PointerEvent pointer && pointer.action() != PointerEvent.Action.MOVE)
                intercepted |= DebugBridge.backend().pointer(this, pointer.windowX() + getContext().getOriginX(), pointer.windowY() + getContext().getOriginY(), pointer.button(), pointer.action() == PointerEvent.Action.PRESS);
            if (inspectable && event instanceof KeyEvent key)
                intercepted |= DebugBridge.backend().key(this, key.keyCode(), key.action() == KeyEvent.Action.RELEASE ? GLFW.GLFW_RELEASE : GLFW.GLFW_PRESS);
            if (intercepted) {
                event.begin(this);
                cancelPointer();
                event.accept();
                return true;
            }
            if (event instanceof KeyEvent key && focusManager.activationHeld(key.keyCode())) {
                event.begin(focusManager.getFocused());
                if (key.action() == KeyEvent.Action.RELEASE)
                    focusManager.keyReleased(key.keyCode(), key.modifiers(), getContext().getMui());
                event.accept();
                return true;
            }
            if (event instanceof KeyEvent key && key.action() == KeyEvent.Action.REPEAT && key.keyCode() == GLFW.GLFW_KEY_ESCAPE) {
                event.begin(this);
                event.accept();
                return true;
            }
            Control target;
            if (event instanceof PointerEvent pointer) {
                lastPointerX = pointer.windowX();
                lastPointerY = pointer.windowY();
                if (pointer.action() != PointerEvent.Action.MOVE)
                    getContext().button(pointer.button(), pointer.action() == PointerEvent.Action.PRESS);
                if (pointer.action() == PointerEvent.Action.PRESS) {
                    Control previous = pointerCaptures.remove(pointer.button());
                    if (previous != null) previous.cancelInteraction();
                    focusManager.cancelActivation();
                    target = pointerTarget(pointer.windowX(), pointer.windowY());
                    focusManager.pointerPressed(target, getContext().getMui());
                } else target = pointerCaptures.get(pointer.button());
                if (target == null && pointer.action() == PointerEvent.Action.MOVE)
                    target = pointerTarget(pointer.windowX(), pointer.windowY());
            } else if (event instanceof ScrollEvent scroll) target = pointerTarget(scroll.windowX(), scroll.windowY());
            else target = focusManager.getFocused();
            event.begin(target);
            if (target != null) {
                List<Control> path = new ArrayList<>();
                for (Control node = target; node != null && node != this; node = node.getParent()) path.add(node);
                for (Control node : path) {
                    if (!attachedAndVisible(node)) break;
                    int x = 0, y = 0;
                    if (event instanceof PointerEvent pointer) {
                        x = pointer.windowX();
                        y = pointer.windowY();
                    }
                    if (event instanceof ScrollEvent scroll) {
                        x = scroll.windowX();
                        y = scroll.windowY();
                    }
                    Rect point = EventGeometry.point(node, x, y);
                    event.deliver(node, point.x(), point.y());
                    if (event.accepted()) {
                        if (event instanceof PointerEvent pointer && pointer.action() == PointerEvent.Action.PRESS && !closedNotified && attachedAndVisible(node))
                            pointerCaptures.put(pointer.button(), event.captureRequest() == null ? node : event.captureRequest());
                        break;
                    }
                }
            }
            if (!event.accepted()) unhandledInput(event);
            if (event instanceof PointerEvent pointer && pointer.action() == PointerEvent.Action.RELEASE)
                pointerCaptures.remove(pointer.button());
            getContext().getPopups().synchronize();
            validateCaptures();
            return event.accepted();
        } catch (RuntimeException | Error error) {
            cancelPointer();
            if (event.target() != null) event.target().cancelInteraction();
            throw error;
        } finally {
            if (event instanceof PointerEvent pointer && pointer.action() == PointerEvent.Action.RELEASE)
                pointerCaptures.remove(pointer.button());
            dispatching = null;
        }
    }

    public void unhandledInput(InputEvent event) {
        if (event instanceof KeyEvent key) {
            boolean handled = key.action() == KeyEvent.Action.RELEASE
                    ? focusManager.keyReleased(key.keyCode(), key.modifiers(), getContext().getMui())
                    : focusManager.keyPressed(key.keyCode(), key.modifiers(), getContext().getMui());
            if (handled) event.accept();
            else if (backEnabled && key.action() == KeyEvent.Action.PRESS && key.keyCode() == GLFW.GLFW_KEY_ESCAPE) {
                onBack.run();
                event.accept();
            }
        }
    }

    private Control pointerTarget(int x, int y) {
        Control hit = overlay == null ? null : overlay.hitTop(x, y);
        return hit == null && content != null ? content.hitTop(x, y) : hit;
    }

    public boolean getPointerCaptured(int button) { return pointerCaptures.containsKey(button); }

    public Control getPointerTarget(int x, int y) { return pointerTarget(x, y); }

    public boolean pointerOver(Control control, int x, int y) {
        for (Control node = pointerTarget(x, y); node != null; node = node.getParent())
            if (node == control) return true;
        return false;
    }

    public Control tooltipTarget(int rootX, int rootY) {
        if (getContext().getPopups().open()) return null;
        if (!getVisible() || !hit(rootX, rootY)) return null;
        for (Control node = pointerTarget(rootX, rootY); node != null && node != this; node = node.getParent()) {
            if ((!node.isAlive() || !node.getVisible())) return null;
            if (node.hasTooltip() && focusManager.allowsNativeInput(node)) return node;
        }
        return null;
    }

    private void drawTooltip(DrawContext context, int screenX, int screenY) {
        int mouseX = context.getAbsMouseX(), mouseY = context.getAbsMouseY();
        Control target = tooltipTarget(mouseX - screenX, mouseY - screenY);
        if (target == null) return;
        float depth = 0.0F;
        for (Control node = target; node != null && node != this; node = node.getParent())
            depth += node.getContentDepth();
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

    private boolean attachedAndVisible(Control target) {
        for (Control node = target; node != null; node = node.getParent()) {
            if ((!node.isAlive() || !node.getVisible())) return false;
            if (node == this) return true;
        }
        return false;
    }

    public void cancelCaptures(Control subtree, CancelEvent.Reason reason) {
        for (var entry : List.copyOf(pointerCaptures.entrySet())) {
            for (Control node = entry.getValue(); node != null; node = node.getParent()) {
                if (node == subtree) {
                    pointerCaptures.remove(entry.getKey());
                    cancelTarget(entry.getValue(), reason);
                    break;
                }
            }
        }
    }

    private void cancelTarget(Control target, CancelEvent.Reason reason) {
        CancelEvent cancel = new CancelEvent(getContext(), reason);
        cancel.begin(target);
        cancel.deliver(target, 0, 0);
    }

    private void validateCaptures() {
        for (var entry : List.copyOf(pointerCaptures.entrySet())) {
            Control target = entry.getValue();
            if (attachedAndVisible(target) && focusManager.allowsNativeInput(target)) continue;
            pointerCaptures.remove(entry.getKey());
            cancelTarget(target, CancelEvent.Reason.BLOCKED);
        }
    }

    private void cancelPointer() {
        var captured = List.copyOf(pointerCaptures.values());
        pointerCaptures.clear();
        for (Control target : captured) cancelTarget(target, CancelEvent.Reason.LOST_FOCUS);
    }

    @Override
    public void cancelInteraction() {
        getContext().getPopups().cancel();
        cancelPointer();
        focusManager.resetInput();
        getContext().clearInput();
    }

    public void layoutViewport(int width, int height, Font font) {
        if (!isAlive()) return;
        if (isInsideTree()) prepareTree();

        if (!isAlive()) return;
        validateCaptures();
        getContext().getPopups().synchronize();
        if (fontRevision != OreFonts.revision()) {
            fontRevision = OreFonts.revision();
            invalidateTextLayout();
        }
        int actualWidth = fullscreen ? width : Math.min(width, measureWidth(font, width));
        int actualHeight = fullscreen ? height : Math.min(height, measureHeight(font, height));
        layout(new Rect(0, 0, actualWidth, actualHeight), font);
        LifecycleScheduler.notifications();
    }

    @Override
    public int measureWidth(Font font, int available) {
        if (!isAlive()) return 0;
        return content == null ? 0 : content.measureWidth(font, available);
    }

    @Override
    public int measureHeight(Font font, int available) {
        if (!isAlive()) return 0;
        return content == null ? 0 : content.measureHeight(font, available);
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        try (var phase = DispatchGuard.enter(); var trace = SignalDiagnostics.suppress(!inspectable)) {
            super.layout(localBounds, font);
            Rect full = new Rect(0, 0, localBounds.width(), localBounds.height());
            if (content != null) fitChild(content, full, font);
            if (overlay != null) layoutChild(overlay, full, font);
        }
    }

    @Override
    public void draw(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        if (!getVisible()) return;

        this.getContext().setOrigin(screenX, screenY);
        if (host instanceof EmbeddedWindowHost embedded) {
            closedNotified = false;
            embedded.attached();
            WindowManager.embedded(this);
            this.getContext().setDimensions(context.getViewport().width(), context.getViewport().height(),
                    context.getViewport().width(), context.getViewport().height(), 1);
        }
        try (var phase = DispatchGuard.enter(); var frame = this.getContext().enter(); var trace = SignalDiagnostics.suppress(!inspectable)) {
            if (host instanceof EmbeddedWindowHost && dispatching == null) {
                int x = context.getAbsMouseX() - screenX, y = context.getAbsMouseY() - screenY;
                if (x != lastPointerX || y != lastPointerY)
                    dispatchInput(new PointerEvent(this.getContext(), PointerEvent.Action.MOVE, x, y, 0, this.getContext().getModifiers()));
            }
            Control body = content;
            Overlay layers = overlay;
            DebugBridge.backend().begin(this, context, screenX, screenY);
            DebugBridge.backend().paint(this, context, screenX, screenY);
            Rect area = getScreenBounds(screenX, screenY);
            context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), OreTheme.OVERLAY_COLOR);
            if (body != null && body.getParent() == this && body.isAlive()) body.draw(context, screenX, screenY);
            if (layers != null && layers.getParent() == this && layers.isAlive())
                layers.draw(context, screenX, screenY);

            focusManager.synchronizeNativeFocus(getContext().getMui());
            focusOverlay.draw(context, screenX, screenY);
            drawTooltip(context, screenX, screenY);
            DebugBridge.backend().end(this, context, screenX, screenY);
        }
    }

    /** External hosts render recipe bodies in their native callback, then these layers once. */
    public void drawExternalOverlay(DrawContext context) {
        if (!isAlive() || !getVisible() || isSuspended()) return;
        try (var phase = DispatchGuard.enter(); var active = getContext().enter()) {
            getContext().getPopups().synchronize();
            if (overlay != null && overlay.isAlive()) {
                overlay.layout(getBounds(), context.getFont());
                overlay.draw(context, 0, 0);
            }
            focusManager.synchronizeNativeFocus(getContext().getMui());
            focusOverlay.draw(context, 0, 0);
            drawTooltip(context, 0, 0);
            DebugBridge.backend().end(this, context, 0, 0);
        }
    }
}
