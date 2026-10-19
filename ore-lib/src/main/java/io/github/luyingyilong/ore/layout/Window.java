package io.github.luyingyilong.ore.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.event.*;
import java.util.*;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.ore.theme.OreFonts;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.window.*;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;

/**
 * Control-tree root shared by embedded and native WindowHost implementations.
 */
public final class Window extends Container {
    private final boolean fullscreen;
    private final FocusManager focusManager = new FocusManager(this);
    private final FocusOverlay focusOverlay = new FocusOverlay(focusManager);
    private WindowHost host;
    private boolean inspectable = true;

    private boolean closedNotified;
    private Runnable onClosed = () -> {
    };
    private Control content;
    private Overlay overlay;
    private int fontRevision = -1;
    private final Map<Integer, Control> pointerCaptures = new HashMap<>();
    private InputEvent dispatching;
    private Thread inputThread;
    private boolean wasPicking;
    private int lastPointerX=Integer.MIN_VALUE,lastPointerY=Integer.MIN_VALUE;
    private Runnable onBack = OreScreen::back;

    public Window(boolean fullscreen) {
        this.fullscreen = fullscreen;
        host = new EmbeddedWindowHost();
        host.context().owner(this);
    }

    public static Window embedded() {
        Window root = new Window(true);
        root.onBack = root::close;
        return root;
    }

    public static Window nativeWindow(WindowOptions options) {
        Window root = new Window(true);
        root.host = new NativeWindowHost(options);
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
        io.github.luyingyilong.ore.input.OreInputMethod.releaseWindow(context());
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

    public Overlay popupOverlay() {
        if(overlay==null)overlay(new Overlay());
        return overlay;
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

    /** The sole event entrance for embedded, native and business hosts. */
    public boolean dispatchInput(InputEvent event) {
        if (event.context() != context()) throw new IllegalArgumentException("Event belongs to another Window");
        if(event.dispatched())throw new IllegalStateException("InputEvent already dispatched");
        if (inputThread == null) inputThread = Thread.currentThread();
        if (Thread.currentThread() != inputThread) throw new IllegalStateException("Input must run on the client thread");
        if (dispatching != null) throw new IllegalStateException("Reentrant input dispatch");
        try (var ignored = context().enter()) {
            dispatching = event;
            context().modifiers(event.modifiers());
            if(event instanceof KeyEvent key)context().key(key.keyCode(),key.action()==KeyEvent.Action.RELEASE?0:1,key.modifiers());
            context().popups().synchronize();
            validateCaptures();
            if (event instanceof CancelEvent) {
                event.begin(this); cancelInteractions(); event.accept(); return true;
            }
            boolean picking=inspectable && DebugBridge.backend().picking(this);
            if(picking!=wasPicking) {
                cancelPointer();focusManager.cancelActivation();
                if(picking && focusManager.focused() instanceof io.github.luyingyilong.ore.input.InputControl field)field.widget().cancelPreedit();
                wasPicking=picking;
            }
            boolean intercepted=picking;
            if(inspectable && event instanceof PointerEvent pointer && pointer.action()!=PointerEvent.Action.MOVE)
                intercepted|=DebugBridge.backend().pointer(this,pointer.windowX()+context().originX(),pointer.windowY()+context().originY(),pointer.button(),pointer.action()==PointerEvent.Action.PRESS);
            if(inspectable && event instanceof KeyEvent key)
                intercepted|=DebugBridge.backend().key(this,key.keyCode(),key.action()==KeyEvent.Action.RELEASE?GLFW.GLFW_RELEASE:GLFW.GLFW_PRESS);
            if(intercepted){event.begin(this);cancelPointer();event.accept();return true;}
            if(event instanceof KeyEvent key && focusManager.activationHeld(key.keyCode())) {
                event.begin(focusManager.focused());
                if(key.action()==KeyEvent.Action.RELEASE)focusManager.keyReleased(key.keyCode(),key.modifiers(),context().mui());
                event.accept();return true;
            }
            if(event instanceof KeyEvent key && key.action()==KeyEvent.Action.REPEAT && key.keyCode()==GLFW.GLFW_KEY_ESCAPE) {
                event.begin(this);event.accept();return true;
            }
            Control target;
            if (event instanceof PointerEvent pointer) {
                lastPointerX=pointer.windowX();lastPointerY=pointer.windowY();
                if(pointer.action()!=PointerEvent.Action.MOVE)context().button(pointer.button(),pointer.action()==PointerEvent.Action.PRESS);
                if (pointer.action()==PointerEvent.Action.PRESS) {
                    Control previous=pointerCaptures.remove(pointer.button()); if(previous!=null)previous.cancelInteraction();
                    focusManager.cancelActivation();
                    target=pointerTarget(pointer.windowX(),pointer.windowY());
                    focusManager.pointerPressed(target,context().mui());
                } else target=pointerCaptures.get(pointer.button());
                if(target==null && pointer.action()==PointerEvent.Action.MOVE)target=pointerTarget(pointer.windowX(),pointer.windowY());
            } else if(event instanceof ScrollEvent scroll) target=pointerTarget(scroll.windowX(),scroll.windowY());
            else target=focusManager.focused();
            event.begin(target);
            if(target!=null) {
                List<Control> path=new ArrayList<>();
                for(Control node=target;node!=null && node!=this;node=node.parent())path.add(node);
                for(Control node:path) {
                    if(!attachedAndVisible(node))break;
                    int x=0,y=0;
                    if(event instanceof PointerEvent pointer){x=pointer.windowX();y=pointer.windowY();}
                    if(event instanceof ScrollEvent scroll){x=scroll.windowX();y=scroll.windowY();}
                    Rect point=EventGeometry.point(node,x,y);
                    event.deliver(node,point.x(),point.y());
                    if(event.accepted()) {
                        if(event instanceof PointerEvent pointer && pointer.action()==PointerEvent.Action.PRESS && !closedNotified && attachedAndVisible(node))
                            pointerCaptures.put(pointer.button(),event.captureRequest()==null?node:event.captureRequest());
                        break;
                    }
                }
            }
            if(!event.accepted())unhandledInput(event);
            if(event instanceof PointerEvent pointer && pointer.action()==PointerEvent.Action.RELEASE)pointerCaptures.remove(pointer.button());
            context().popups().synchronize();
            validateCaptures();
            return event.accepted();
        } catch(RuntimeException | Error error) {
            cancelPointer();
            if(event.target()!=null)event.target().cancelInteraction();
            throw error;
        } finally {
            if(event instanceof PointerEvent pointer && pointer.action()==PointerEvent.Action.RELEASE)pointerCaptures.remove(pointer.button());
            dispatching=null;
        }
    }

    public void unhandledInput(InputEvent event) {
        if(event instanceof KeyEvent key) {
            boolean handled=key.action()==KeyEvent.Action.RELEASE
                    ?focusManager.keyReleased(key.keyCode(),key.modifiers(),context().mui())
                    :focusManager.keyPressed(key.keyCode(),key.modifiers(),context().mui());
            if(handled)event.accept();
            else if(key.action()==KeyEvent.Action.PRESS && key.keyCode()==GLFW.GLFW_KEY_ESCAPE){onBack.run();event.accept();}
        }
    }

    private Control pointerTarget(int x,int y) {
        Control hit=overlay==null?null:overlay.hitTop(x,y);
        return hit==null && content!=null?content.hitTop(x,y):hit;
    }

    public boolean pointerOver(Control control,int x,int y) {
        for(Control node=pointerTarget(x,y);node!=null;node=node.parent())if(node==control)return true;
        return false;
    }

    public Control tooltipTarget(int rootX, int rootY) {
        if (context().popups().open()) return null;
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

    private boolean attachedAndVisible(Control target) {
        for(Control node=target;node!=null;node=node.parent()) {
            if(!node.visible())return false;
            if(node==this)return true;
        }
        return false;
    }

    public void cancelCaptures(Control subtree,CancelEvent.Reason reason) {
        for(var entry:List.copyOf(pointerCaptures.entrySet())) {
            for(Control node=entry.getValue();node!=null;node=node.parent()) {
                if(node==subtree) {
                    pointerCaptures.remove(entry.getKey());
                    cancelTarget(entry.getValue(),reason);
                    break;
                }
            }
        }
    }
    private void cancelTarget(Control target,CancelEvent.Reason reason) {
        CancelEvent cancel=new CancelEvent(context(),reason);cancel.begin(target);cancel.deliver(target,0,0);
    }

    private void validateCaptures() {
        for(var entry:List.copyOf(pointerCaptures.entrySet())) {
            Control target=entry.getValue();
            if(attachedAndVisible(target)&&focusManager.allowsNativeInput(target))continue;
            pointerCaptures.remove(entry.getKey());
            cancelTarget(target,CancelEvent.Reason.BLOCKED);
        }
    }
    private void cancelPointer() {
        var captured=List.copyOf(pointerCaptures.values());pointerCaptures.clear();
        for(Control target:captured)cancelTarget(target,CancelEvent.Reason.LOST_FOCUS);
    }
    @Override public void cancelInteraction() {
        context().popups().cancel();cancelPointer();focusManager.resetInput();context().clearInput();
    }

    public void layoutViewport(int width, int height, Font font) {
        validateCaptures();
        context().popups().synchronize();
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
        this.context().origin(screenX,screenY);
        if (host instanceof EmbeddedWindowHost embedded) {
            closedNotified = false;
            embedded.attached();
            WindowManager.embedded(this);
            this.context().dimensions(context.getScreenArea().width, context.getScreenArea().height,
                    context.getScreenArea().width, context.getScreenArea().height, 1);
        }
        try (var frame = this.context().enter()) {
            if(host instanceof EmbeddedWindowHost && dispatching==null) {
                int x=context.getAbsMouseX()-screenX,y=context.getAbsMouseY()-screenY;
                if(x!=lastPointerX||y!=lastPointerY)
                    dispatchInput(new PointerEvent(this.context(),PointerEvent.Action.MOVE,x,y,0,this.context().modifiers()));
            }
            DebugBridge.backend().begin(this, context, screenX, screenY);
            DebugBridge.backend().paint(this, context, screenX, screenY);
            Rect area = screenBounds(screenX, screenY);
            context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), OreTheme.OVERLAY_COLOR);
            if (content != null) content.draw(context, screenX, screenY);
            if (overlay != null) overlay.draw(context, screenX, screenY);

            focusManager.synchronizeNativeFocus(context);
            focusOverlay.draw(context, screenX, screenY);
            drawTooltip(context, screenX, screenY);
            DebugBridge.backend().end(this, context, screenX, screenY);
        }
    }
}
