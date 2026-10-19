package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.event.CancelEvent;
import io.github.luyingyilong.ore.event.PointerEvent;
import io.github.luyingyilong.ore.input.OreInputMethod;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.render.Canvas;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.signal.DispatchGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One input/focus/overlay coordinator for all Ore surfaces on an external Screen. */
public final class ScreenWindowHost implements WindowHost {
    private static final Map<Screen, ScreenWindowHost> HOSTS = new java.util.WeakHashMap<>();
    private static final java.util.Set<ScreenWindowHost> ACTIVE = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final java.lang.ref.WeakReference<Screen> screen;
    private final WindowContext context = new WindowContext(false);
    private final Window root = Window.external(this);
    private final Container regions = new Container();
    private final Map<Canvas, CanvasRegion> canvases = new IdentityHashMap<>();
    private MuiInputHost inputs;
    private boolean closed, debugFrame, focused = true;
    private boolean prepared;
    private int layoutWidth = -1, layoutHeight = -1, fontRevision = -1;
    private io.github.luyingyilong.ore.layout.Control retainedFocus;
    private io.github.luyingyilong.ore.layout.Control navigationFocus;

    private ScreenWindowHost(Screen screen) {
        this.screen = new java.lang.ref.WeakReference<>(Objects.requireNonNull(screen));
        ACTIVE.add(this);
        root.setDebugName("External Screen: " + screen.getClass().getSimpleName());
        root.setContent(regions);
        root.getSignalScope().own(() -> {
            if (inputs != null) inputs.close();
            context.detach();
        });
    }

    public static ScreenWindowHost getOrCreate(Screen screen) {
        checkThread();
        return HOSTS.computeIfAbsent(Objects.requireNonNull(screen), ScreenWindowHost::new);
    }
    public static ScreenWindowHost get(Screen screen) { return HOSTS.get(screen); }
    public static List<ScreenWindowHost> getHosts() { return List.copyOf(ACTIVE); }
    private static void checkThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Screen hosts run on the client thread");
    }

    public Screen getScreen() { return screen.get(); }
    public Window getWindow() { return root; }
    @Override public WindowContext getContext() { return context; }
    @Override public boolean getNativeWindow() { return false; }
    @Override public boolean isOpen() { return !closed && root.isAlive(); }
    @Override public void open(Window window) {
        if (window != root) throw new IllegalArgumentException("Screen host already owns its coordinating Window");
    }
    @Override public void requestFocus() { /* Never open or replace the borrowed Screen. */ }

    public void attach(Canvas canvas, CanvasRegion region) {
        checkThread();
        if (!isOpen()) throw new IllegalStateException("Screen host is closed");
        regions.addChild(region);
        canvases.put(canvas, region);
    }

    public void remove(Canvas canvas, CanvasRegion region) {
        checkThread();
        canvases.remove(canvas);
        if (region.getParent() == regions) regions.removeChild(region);
        if (canvases.isEmpty()) close();
    }

    public List<Rect> getExclusionAreas() {
        return canvases.values().stream().filter(region -> region.isAlive() && region.getVisible() && !region.isSuspended())
                .map(CanvasRegion::getHostBounds).filter(area -> area.width() > 0 && area.height() > 0).toList();
    }

    private void prepare() {
        var mc = Minecraft.getInstance();
        var screen = getScreen();
        if (screen == null) throw new IllegalStateException("Screen was released");
        int revision = io.github.luyingyilong.ore.theme.OreFonts.revision();
        if (prepared && !root.getLayoutDirty() && layoutWidth == screen.width && layoutHeight == screen.height && fontRevision == revision) return;
        var main = mc.getWindow();
        context.setDimensions(main.getScreenWidth(), main.getScreenHeight(), main.getWidth(), main.getHeight(), main.getGuiScale());
        if (inputs == null) inputs = new MuiInputHost(context, screen, () -> isOpen() && mc.screen == getScreen(), () -> { });
        inputs.resize(screen.width, screen.height);
        inputs.prepare(root);
        root.prepareTree();
        root.layoutViewport(screen.width, screen.height, mc.font);
        for (CanvasRegion region : List.copyOf(canvases.values())) if (region.isAlive())
            region.layout(region.getViewport(), mc.font);
        inputs.update();
        prepared = true;
        layoutWidth = screen.width; layoutHeight = screen.height; fontRevision = revision;
    }

    public void beginFrame() { prepared = false; debugFrame = false; }

    public void render(Canvas canvas, CanvasRegion region, GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        checkThread();
        if (!isOpen() || canvases.get(canvas) != region || Minecraft.getInstance().screen != getScreen()) return;
        graphics.flush();
        try (var state = new WindowRenderState(false); var phase = DispatchGuard.enter(); var active = context.enter()) {
            prepare();
            graphics.flush();
            graphics.pose().pushPose();
            float depth = graphics.pose().last().pose().m32();
            // The caller can be inside a recipe-local pose. Drawing starts in host GUI pixels.
            graphics.pose().mulPoseMatrix(new Matrix4f(graphics.pose().last().pose()).invert());
            graphics.pose().translate(0, 0, depth);
            try (var mui = inputs.begin(graphics, Minecraft.getInstance().font, mouseX, mouseY, partial);
                 var frame = io.github.luyingyilong.ore.window.MuiDrawing.begin(context.getMui(), context)) {
                if (!debugFrame) {
                    DebugBridge.backend().begin(root, frame, 0, 0);
                    DebugBridge.backend().paint(root, frame, 0, 0);
                    debugFrame = true;
                }
                region.draw(frame, 0, 0);
                graphics.flush();
            } finally { graphics.pose().popPose(); }
        }
    }

    public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        if (!isOpen() || canvases.isEmpty() || Minecraft.getInstance().screen != getScreen()) return;
        graphics.flush();
        try (var state = new WindowRenderState(false); var phase = DispatchGuard.enter(); var active = context.enter()) {
            prepare();
            try (var mui = inputs.begin(graphics, Minecraft.getInstance().font, mouseX, mouseY, partial);
                 var frame = io.github.luyingyilong.ore.window.MuiDrawing.begin(context.getMui(), context)) {
                root.dispatchInput(new PointerEvent(context, PointerEvent.Action.MOVE, mouseX, mouseY, 0, context.getModifiers()));
                root.drawExternalOverlay(frame);
                graphics.flush();
            } finally { debugFrame = false; }
        }
    }

    public void updatePointer(int x, int y, float partial) {
        if (inputs != null) context.getMui().updateState(x, y, partial);
    }

    public void suspend(boolean suspended) {
        if (root.isSuspended() == suspended) return;
        if (suspended) navigationFocus = root.getFocusManager().getFocused();
        root.setSuspended(suspended);
        if (suspended) { root.cancelInteractions(); OreInputMethod.releaseWindow(context); }
        else if (navigationFocus != null && context.getMui() != null) {
            root.getFocusManager().restoreFocus(navigationFocus, context.getMui());
            navigationFocus = null;
        }
    }

    public void updateFocus(boolean active) {
        if (active == focused) return;
        focused = active;
        if (!active) {
            retainedFocus = root.getFocusManager().getFocused();
            root.dispatchInput(new CancelEvent(context, CancelEvent.Reason.LOST_FOCUS));
            OreInputMethod.releaseWindow(context);
        } else if (retainedFocus != null) {
            root.getFocusManager().restoreFocus(retainedFocus, context.getMui());
            retainedFocus = null;
        }
    }

    @Override public void close() {
        if (closed) return;
        checkThread();
        closed = true;
        HOSTS.remove(getScreen(), this);
        ACTIVE.remove(this);
        screen.clear();
        root.queueFree("external Screen session ended");
        canvases.clear();
        OreInputMethod.releaseWindow(context);
    }
}
