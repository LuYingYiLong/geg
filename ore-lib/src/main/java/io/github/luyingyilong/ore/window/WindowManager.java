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
    private static java.lang.ref.WeakReference<io.github.luyingyilong.ore.layout.Window> embedded = new java.lang.ref.WeakReference<>(null);
    private static io.github.luyingyilong.ore.layout.Window suspended;
    private static io.github.luyingyilong.ore.layout.Control embeddedFocus;
    private static net.minecraft.client.gui.screens.Screen embeddedScreen;
    private static boolean mainFocused = true;

    private WindowManager() {
    }

    public static void embedded(io.github.luyingyilong.ore.layout.Window root) {
        embedded = new java.lang.ref.WeakReference<>(root);
        embeddedScreen = Minecraft.getInstance().screen;
    }

    private static void embeddedFocus() {
        var mc = Minecraft.getInstance();
        var root = embedded.get();
        boolean focused = org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_FOCUSED) != 0;
        if (!focused && root != null && (mainFocused || suspended != root) && root.context().mui() != null) {
            suspended = root;
            embeddedFocus = root.focusManager().focused();
            root.cancelInteractions();
            if (root.context().mui().isFocused()) root.context().mui().removeFocus();
            io.github.luyingyilong.ore.input.OreInputMethod.releaseWindow(root.context());
        } else if (focused && !mainFocused && root != null && root == suspended && mc.screen == embeddedScreen && embeddedFocus != null) {
            root.focusManager().requestFocus(embeddedFocus, root.context().mui());
            suspended = null;
            embeddedFocus = null;
        }
        mainFocused = focused;
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
        return WINDOWS.stream().anyMatch(w -> w.context().focused());
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        lastRender = System.nanoTime();
        for (NativeWindowHost host : List.copyOf(WINDOWS)) {
            try {
                host.frame();
            } catch (RuntimeException | LinkageError error) {
                org.slf4j.LoggerFactory.getLogger(WindowManager.class).error("Ore native window failed", error);
                host.context().owner().close();
                host.destroy();
            }
        }
    }

    @SubscribeEvent
    public static void fallback(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) embeddedFocus();
        if (event.phase == TickEvent.Phase.END && !WINDOWS.isEmpty() && System.nanoTime() - lastRender > 100_000_000L)
            render(new TickEvent.RenderTickEvent(TickEvent.Phase.END, 0));
    }

    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { shutdown(null); }

    @SubscribeEvent
    public static void shutdown(GameShuttingDownEvent event) {
        for (NativeWindowHost host : List.copyOf(WINDOWS)) { host.context().owner().close(); host.destroy(); }
        embedded.clear(); suspended=null; embeddedFocus=null; embeddedScreen=null;
    }
}
