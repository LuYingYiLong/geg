package io.github.luyingyilong.ore.compat.recipeviewer;

import brachy.modularui.integration.recipeviewer.handlers.RecipeViewerHandler;
import brachy.modularui.screen.ClientScreenHandler;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.screen.OreScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Layout exclusions do not stop recipe-viewer hotkeys or focus changes. Give protected
 * Ore input to MUI once, before JEI/Architectury screen listeners, then consume it.
 * EMI 1.20.1's earlier raw callbacks only handle AbstractContainerScreen; OreScreen
 * deliberately remains a non-container Screen, so no optional internal mixins are needed.
 */
public final class RecipeViewerInputGuard {
    private static final InputGestureOwners<Screen> GESTURES = new InputGestureOwners<>();

    private RecipeViewerInputGuard() { }

    public static boolean getKeyboardProtected(Window window) {
        return available(window) && (window.getFocusManager().getInputScope() != null
                || window.getFocusManager().getTextInputFocused());
    }

    public static boolean getKeyboardProtected(Screen screen) {
        return screen instanceof OreScreen ore && ore.getWindows().stream()
                .anyMatch(window -> window.getHost().isOpen() && getKeyboardProtected(window));
    }

    public static boolean getPointerProtected(Screen screen, double x, double y) {
        if (!(screen instanceof OreScreen ore)) return false;
        if (ore.getWindows().stream().anyMatch(window -> window.getHost().isOpen() && available(window)
                && window.getFocusManager().getInputScope() != null)) return true;
        return RecipeViewerExclusions.getAreas(screen).stream().anyMatch(area ->
                x >= area.x() && x < area.right() && y >= area.y() && y < area.bottom());
    }

    private static boolean available(Window window) {
        return window != null && window.isAlive() && window.getVisible() && !window.isSuspended()
                && !window.getContext().getNativeWindow();
    }

    private static boolean currentOreScreen(Screen screen) {
        return screen instanceof OreScreen && Minecraft.getInstance().screen == screen;
    }

    private static void unfocusViewer() {
        // MUI selects the active installed viewer, or its no-op handler when none is installed.
        RecipeViewerHandler.getCurrent().setSearchFocused(false);
    }

    private static void syncPointer(Screen screen, double x, double y) {
        // MUI can rebuild a panel's input viewport from its default context. An early
        // listener must synchronize both contexts from this event, not a previous frame.
        if (screen instanceof OreScreen ore) {
            var context = ore.screen().getContext();
            float partial = context.getRenderPartialTicks();
            ClientScreenHandler.getDefaultContext().updateState((int) x, (int) y, partial);
            context.updateState((int) x, (int) y, partial);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        var token = InputGestureOwners.Token.key(event.getKeyCode(), event.getScanCode());
        if (!GESTURES.contains(token) && !getKeyboardProtected(event.getScreen())) return;
        GESTURES.claim(token, event.getScreen());
        try {
            if (GESTURES.getOwner(token) == event.getScreen() && currentOreScreen(event.getScreen())) {
                unfocusViewer();
                ClientScreenHandler.onScreenKeyPressedHigh(event);
            }
        } finally { event.setCanceled(true); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keyReleased(ScreenEvent.KeyReleased.Pre event) {
        var token = InputGestureOwners.Token.key(event.getKeyCode(), event.getScanCode());
        boolean captured = GESTURES.contains(token);
        Screen owner = GESTURES.getOwner(token);
        if (!captured && !getKeyboardProtected(event.getScreen())) return;
        try {
            if ((!captured || owner == event.getScreen()) && currentOreScreen(event.getScreen()))
                ClientScreenHandler.onScreenKeyReleasedHigh(event);
            else ClientScreenHandler.getDefaultContext().updateKey(event.getKeyCode(), event.getScanCode(), event.getModifiers(), false);
        } finally { GESTURES.release(token); event.setCanceled(true); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void character(ScreenEvent.CharacterTyped.Pre event) {
        boolean protectedInput = getKeyboardProtected(event.getScreen());
        if (!protectedInput && !GESTURES.hasKeys()) return;
        try {
            if (protectedInput && currentOreScreen(event.getScreen())) {
                unfocusViewer();
                ClientScreenHandler.onScreenCharTyped(event);
            }
        } finally { event.setCanceled(true); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        var token = InputGestureOwners.Token.button(event.getButton());
        if (!GESTURES.contains(token) && !getPointerProtected(event.getScreen(), event.getMouseX(), event.getMouseY())) return;
        GESTURES.claim(token, event.getScreen());
        try {
            if (GESTURES.getOwner(token) == event.getScreen() && currentOreScreen(event.getScreen())) {
                unfocusViewer();
                syncPointer(event.getScreen(), event.getMouseX(), event.getMouseY());
                ClientScreenHandler.onScreenMousePressed(event);
            }
        } finally { event.setCanceled(true); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        var token = InputGestureOwners.Token.button(event.getButton());
        boolean captured = GESTURES.contains(token);
        Screen owner = GESTURES.getOwner(token);
        if (!captured && !getPointerProtected(event.getScreen(), event.getMouseX(), event.getMouseY())) return;
        try {
            if ((!captured || owner == event.getScreen()) && currentOreScreen(event.getScreen())) {
                syncPointer(event.getScreen(), event.getMouseX(), event.getMouseY());
                ClientScreenHandler.onScreenMouseReleased(event);
            } else ClientScreenHandler.getDefaultContext().updateMouseButton(event.getButton(), false);
        } finally { GESTURES.release(token); event.setCanceled(true); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dragged(ScreenEvent.MouseDragged.Pre event) {
        var token = InputGestureOwners.Token.button(event.getMouseButton());
        if (!GESTURES.contains(token) && !getPointerProtected(event.getScreen(), event.getMouseX(), event.getMouseY())) return;
        try {
            if ((!GESTURES.contains(token) || GESTURES.getOwner(token) == event.getScreen()) && currentOreScreen(event.getScreen())) {
                syncPointer(event.getScreen(), event.getMouseX(), event.getMouseY());
                ClientScreenHandler.onScreenMouseDragged(event);
            }
        } finally { event.setCanceled(true); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!getPointerProtected(event.getScreen(), event.getMouseX(), event.getMouseY())) return;
        try {
            if (currentOreScreen(event.getScreen())) {
                syncPointer(event.getScreen(), event.getMouseX(), event.getMouseY());
                ClientScreenHandler.onScreenMouseScrolled(event);
            }
        }
        finally { event.setCanceled(true); }
    }

    @SubscribeEvent public static void rawKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_RELEASE)
            GESTURES.release(InputGestureOwners.Token.key(event.getKey(), event.getScanCode()));
    }

    @SubscribeEvent public static void rawMouse(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_RELEASE)
            GESTURES.release(InputGestureOwners.Token.button(event.getButton()));
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isWindowActive()) GESTURES.clear();
        else if (getKeyboardProtected(minecraft.screen)) unfocusViewer();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { GESTURES.clear(); }
}
