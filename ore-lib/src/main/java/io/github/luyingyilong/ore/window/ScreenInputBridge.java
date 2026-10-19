package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.event.KeyEvent;
import io.github.luyingyilong.ore.event.PointerEvent;
import io.github.luyingyilong.ore.event.ScrollEvent;
import io.github.luyingyilong.ore.event.TextInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The sole external-Screen input adapter; viewer widgets are rendering-only adapters. */
public final class ScreenInputBridge {
    private record Stamp(String kind, int code, double x, double y) { }
    private static Stamp earlyUnhandled;
    private static final Map<Integer, UUID> KEYS = new HashMap<>();
    private static final Map<Integer, UUID> BUTTONS = new HashMap<>();
    private ScreenInputBridge() { }

    private static ScreenWindowHost host() {
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return null;
        var host = ScreenWindowHost.get(mc.screen);
        return host != null && host.isOpen() && !host.getWindow().isSuspended() ? host : null;
    }

    private static boolean skipped(Stamp stamp, boolean early) {
        if (early) { earlyUnhandled = null; return false; }
        boolean skipped = stamp.equals(earlyUnhandled);
        earlyUnhandled = null;
        return skipped;
    }
    private static boolean finish(Stamp stamp, boolean early, boolean handled) {
        if (early && !handled) earlyUnhandled = stamp;
        return handled;
    }

    public static boolean key(int key, int scan, int action, int modifiers, boolean early) {
        Stamp stamp = new Stamp(action == GLFW.GLFW_RELEASE ? "keyUp" : "keyDown", key, scan, modifiers);
        if (skipped(stamp, early)) return false;
        var host = host();
        boolean release = action == GLFW.GLFW_RELEASE;
        UUID owner = KEYS.get(key);
        boolean paired = owner != null;
        if (release) KEYS.remove(key);
        if (host == null) return paired && release;
        if (paired && !owner.equals(host.getContext().getId())) return true;
        var root = host.getWindow();
        boolean protectedKey = root.getFocusManager().getTextInputFocused() || root.getFocusManager().getInputScope() != null;
        if (host.getContext().getMui() != null) host.getContext().getMui().updateKey(key, scan, modifiers, !release);
        boolean handled = root.dispatchInput(new KeyEvent(host.getContext(), release ? KeyEvent.Action.RELEASE
                : action == GLFW.GLFW_REPEAT ? KeyEvent.Action.REPEAT : KeyEvent.Action.PRESS, key, scan, modifiers));
        handled |= protectedKey || paired;
        if (protectedKey) unfocusViewer();
        if (handled && !release) KEYS.put(key, host.getContext().getId());
        return finish(stamp, early, handled);
    }

    public static boolean text(String text, int modifiers, boolean early) {
        Stamp stamp = new Stamp("text", text.hashCode(), 0, modifiers);
        if (skipped(stamp, early)) return false;
        var host = host();
        if (host == null) return false;
        boolean owned = host.getWindow().getFocusManager().getTextInputFocused();
        if (owned) unfocusViewer();
        boolean handled = owned && host.getWindow().dispatchInput(new TextInputEvent(host.getContext(), text, modifiers));
        return finish(stamp, early, owned || handled);
    }

    public static boolean pointer(int x, int y, int button, boolean press, int modifiers, boolean early) {
        Stamp stamp = new Stamp(press ? "press" : "release", button, x, y);
        if (skipped(stamp, early)) return false;
        UUID owner = BUTTONS.get(button);
        boolean paired = owner != null;
        if (!press) BUTTONS.remove(button);
        var host = host();
        if (host == null) return paired && !press;
        if (paired && !owner.equals(host.getContext().getId())) return true;
        host.updatePointer(x, y, 0);
        boolean handled = host.getWindow().dispatchInput(new PointerEvent(host.getContext(), press ? PointerEvent.Action.PRESS
                : PointerEvent.Action.RELEASE, x, y, button, modifiers));
        handled |= paired;
        if (host.getWindow().getFocusManager().getTextInputFocused()
                || host.getWindow().getFocusManager().getInputScope() != null) unfocusViewer();
        if (handled && press) BUTTONS.put(button, host.getContext().getId());
        return finish(stamp, early, handled);
    }

    public static boolean scroll(int x, int y, double deltaX, double deltaY, boolean early) {
        Stamp stamp = new Stamp("scroll", 0, deltaX, deltaY);
        if (skipped(stamp, early)) return false;
        var host = host();
        if (host == null) return false;
        host.updatePointer(x, y, 0);
        int mods = modifiers();
        if ((mods & GLFW.GLFW_MOD_SHIFT) != 0 && deltaX == 0) { deltaX = deltaY; deltaY = 0; }
        return finish(stamp, early, host.getWindow().dispatchInput(new ScrollEvent(host.getContext(), x, y, deltaX, deltaY, mods)));
    }

    public static int modifiers() {
        return (Screen.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0) | (Screen.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0)
                | (Screen.hasAltDown() ? GLFW.GLFW_MOD_ALT : 0);
    }

    private static void unfocusViewer() {
        brachy.modularui.integration.recipeviewer.handlers.RecipeViewerHandler.getCurrent().setSearchFocused(false);
    }

    /** Viewer/container callbacks can precede ScreenEvent; dispatch once before those callbacks. */
    public static boolean getEarlyRouting(long handle) {
        if (handle != Minecraft.getInstance().getWindow().getWindow() || host() == null) return false;
        var mods = net.minecraftforge.fml.ModList.get();
        return mods != null && (mods.isLoaded("emi") || mods.isLoaded("jei") || mods.isLoaded("roughlyenoughitems"));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keyDown(ScreenEvent.KeyPressed.Pre event) {
        if (key(event.getKeyCode(), event.getScanCode(), GLFW.GLFW_PRESS, event.getModifiers(), false)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keyUp(ScreenEvent.KeyReleased.Pre event) {
        if (key(event.getKeyCode(), event.getScanCode(), GLFW.GLFW_RELEASE, event.getModifiers(), false)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void character(ScreenEvent.CharacterTyped.Pre event) {
        if (text(String.valueOf(event.getCodePoint()), event.getModifiers(), false)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void press(ScreenEvent.MouseButtonPressed.Pre event) {
        if (pointer((int) event.getMouseX(), (int) event.getMouseY(), event.getButton(), true, modifiers(), false)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (pointer((int) event.getMouseX(), (int) event.getMouseY(), event.getButton(), false, modifiers(), false)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void drag(ScreenEvent.MouseDragged.Pre event) {
        var host = host();
        if (host == null || !host.getWindow().getPointerCaptured(event.getMouseButton())) return;
        host.updatePointer((int) event.getMouseX(), (int) event.getMouseY(), 0);
        host.getWindow().dispatchInput(new PointerEvent(host.getContext(), PointerEvent.Action.MOVE,
                (int) event.getMouseX(), (int) event.getMouseY(), event.getMouseButton(), modifiers()));
        event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void wheel(ScreenEvent.MouseScrolled.Pre event) {
        if (scroll((int) event.getMouseX(), (int) event.getMouseY(), 0, event.getScrollDelta(), false)) event.setCanceled(true);
    }

    public static void endEarly() { earlyUnhandled = null; }

    public static void clear() { KEYS.clear(); BUTTONS.clear(); earlyUnhandled = null; }
}
