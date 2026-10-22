package io.github.luyingyilong.ore.window;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Uses the existing GLFW poll loop and render thread; no second event or rendering thread.
 */
public final class WindowManager {
    private static final Set<NativeWindowHost> WINDOWS = new LinkedHashSet<>();
    private static boolean previousPause;
    private static long lastRender;
    private static java.lang.ref.WeakReference<io.github.luyingyilong.ore.window.Window> embedded = new java.lang.ref.WeakReference<>(null);

    private WindowManager() {
    }

    public static void embedded(io.github.luyingyilong.ore.window.Window root) {
        io.github.luyingyilong.ore.window.screen.OreScreen.attachRoot(root);
        embedded = new java.lang.ref.WeakReference<>(root);
        synchronizeEmbedded(root);
    }

    private static void embeddedFocus() {
        var root = embedded.get();
        if (root != null) synchronizeEmbedded(root);
    }

    /** MUI's keyPressed callback loses GLFW's PRESS/REPEAT distinction. Reject stale repeats before that conversion. */
    public static boolean rejectEmbeddedRepeat(long handle, int key, int scan, int action, int modifiers) {
        var mc = Minecraft.getInstance();
        if (action != org.lwjgl.glfw.GLFW.GLFW_REPEAT || mc == null || handle != mc.getWindow().getWindow()
                || !(mc.screen instanceof io.github.luyingyilong.ore.window.screen.OreScreen screen)) return false;
        var roots = screen.getWindows().stream().filter(root -> root.isAlive() && !root.isSuspended()).toList();
        if (roots.isEmpty()) return false;
        for (Window root : roots) synchronizeEmbedded(root);
        if (roots.stream().anyMatch(root -> root.getContext().keyDown(key))) return false;
        for (Window root : roots)
            root.dispatchInput(new io.github.luyingyilong.ore.event.KeyEvent(root.getContext(),
                    io.github.luyingyilong.ore.event.KeyEvent.Action.REPEAT, key, scan, modifiers));
        return true;
    }

    static void synchronizeEmbedded(Window root) {
        var mc = Minecraft.getInstance();
        if (mc == null || !root.isAlive() || root.isSuspended()) return;
        var main = mc.getWindow();
        boolean focused = org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(main.getWindow(), org.lwjgl.glfw.GLFW.GLFW_FOCUSED) != 0;
        boolean drawable = main.getScreenWidth() > 0 && main.getScreenHeight() > 0 && main.getWidth() > 0 && main.getHeight() > 0
                && Double.isFinite(main.getGuiScale()) && main.getGuiScale() > 0
                && org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(main.getWindow(), org.lwjgl.glfw.GLFW.GLFW_ICONIFIED) == 0;
        root.hostInputState().update(focused, drawable, main.getScreenWidth(), main.getScreenHeight(), main.getWidth(), main.getHeight(),
                main.getGuiScale(), main.isFullscreen());
    }

    static void add(NativeWindowHost host) {
        if (WINDOWS.isEmpty()) {
            previousPause = Minecraft.getInstance().options.pauseOnLostFocus;
            Minecraft.getInstance().options.pauseOnLostFocus = false;
        }
        WINDOWS.add(host);
    }

    static void remove(NativeWindowHost host) {
        boolean removed = WINDOWS.remove(host);
        if (removed && WINDOWS.isEmpty()) Minecraft.getInstance().options.pauseOnLostFocus = previousPause;
    }

    public static int nativeWindows() {
        return WINDOWS.size();
    }

    public static boolean hasFocusedNativeWindow() {
        return WINDOWS.stream().anyMatch(w -> w.getContext().getFocused());
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        lastRender = System.nanoTime();
        io.github.luyingyilong.ore.text.TextRenderer.endFrame();
        for (NativeWindowHost host : List.copyOf(WINDOWS)) {
            try {
                host.frame();
            } catch (RuntimeException | LinkageError error) {
                org.slf4j.LoggerFactory.getLogger(WindowManager.class).error("Ore native window failed", error);
                host.getContext().getOwner().close();
                host.destroy();
            }
        }
        io.github.luyingyilong.ore.gui.UiRuntime.flush();
        io.github.luyingyilong.ore.control.base.LifecycleScheduler.flush();
    }

    @SubscribeEvent
    public static void fallback(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            embeddedFocus();
            io.github.luyingyilong.ore.gui.UiRuntime.flush();
            io.github.luyingyilong.ore.control.base.LifecycleScheduler.flush();
        }
        if (event.phase == TickEvent.Phase.END && !WINDOWS.isEmpty() && System.nanoTime() - lastRender > 100_000_000L)
            render(new TickEvent.RenderTickEvent(TickEvent.Phase.END, 0));
    }

    @SubscribeEvent
    public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        shutdown(null);
    }

    @SubscribeEvent
    public static void shutdown(GameShuttingDownEvent event) {
        io.github.luyingyilong.ore.gui.UiRuntime.closeAll();
        ScreenInputBridge.clear();
        RecipeCanvasFrames.clear();
        for (ScreenWindowHost host : ScreenWindowHost.getHosts()) host.close();
        for (NativeWindowHost host : List.copyOf(WINDOWS)) {
            host.getContext().getOwner().close();
            host.destroy();
        }
        io.github.luyingyilong.ore.window.screen.OreScreen.disposeSessions();
        io.github.luyingyilong.ore.control.base.LifecycleScheduler.flush();
        io.github.luyingyilong.ore.text.TextRenderer.clear();
        embedded.clear();
    }
}
