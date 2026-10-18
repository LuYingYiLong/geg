package io.github.luyingyilong.ore.inspector;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.luyingyilong.ore.debug.DebugBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.lwjgl.glfw.GLFW;

/**
 * Initialized by the development source set service, never included in a production or server artifact.
 */

public final class OreInspector {
    static final KeyMapping OPEN = new KeyMapping("key.ore.inspector", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.ore.development");
    private static InspectorBackend backend;
    private static InspectorView view;
    private static boolean swallowRelease;

    private static boolean initialized;
    public static synchronized void initialize(net.minecraftforge.eventbus.api.IEventBus forgeBus) {
        if (initialized || FMLEnvironment.production) return;
        forgeBus.register(OreInspector.class);
        initialized = true;
    }

    private OreInspector() {
    }

    static InspectorBackend backend() {
        return backend;
    }

    static InspectorView view() {
        return view;
    }

    public static void open() {
        if (FMLEnvironment.production) return;
        try {
            if (view != null && view.window().host().isOpen()) {
                view.window().requestFocus();
                return;
            }
            if (view != null || backend != null) stop();
            backend = new InspectorBackend();
            DebugBridge.install(backend);
            view = new InspectorView(backend);
            view.open();
        } catch (RuntimeException | LinkageError error) {
            stop();
            org.slf4j.LoggerFactory.getLogger(OreInspector.class).error("Ore Inspector failed to open", error);
            var mc = Minecraft.getInstance();
            if (mc.player != null)
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("inspector.ore.open_failed", error.getMessage()), false);
        }
    }

    public static void stop() {
        if (backend == null && view == null) return;
        InspectorView previous = view;
        view = null;
        if (backend != null) backend.clear();
        backend = null;
        DebugBridge.install(null);
        swallowRelease = false;
        if (previous != null) previous.window().close();
    }

    private static boolean pickingEmbedded() {
        return backend != null && backend.picking() && backend.inspectedRoot() != null && !backend.inspectedRoot().host().nativeWindow();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (OPEN.consumeClick()) open();
        try {
            if (backend != null) backend.tick();
            if (view != null) view.update();
        } catch (RuntimeException | LinkageError error) {
            org.slf4j.LoggerFactory.getLogger(OreInspector.class).error("Ore Inspector frontend failed", error);
            stop();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (OPEN.matches(event.getKeyCode(), event.getScanCode())) {
            open();
            event.setCanceled(true);
            return;
        }
        if (pickingEmbedded()) {
            if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) backend.cancelPick();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void character(ScreenEvent.CharacterTyped.Pre event) {
        if (pickingEmbedded()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void press(ScreenEvent.MouseButtonPressed.Pre event) {
        if (pickingEmbedded()) {
            if (event.getButton() == 0) backend.pickClick((int) event.getMouseX(), (int) event.getMouseY());
            swallowRelease = true;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (swallowRelease || (pickingEmbedded())) {
            swallowRelease = false;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void drag(ScreenEvent.MouseDragged.Pre event) {
        if (swallowRelease || (pickingEmbedded())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void wheel(ScreenEvent.MouseScrolled.Pre event) {
        if (pickingEmbedded()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }

    @SubscribeEvent
    public static void shutdown(GameShuttingDownEvent event) {
        stop();
    }

    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            if (!FMLEnvironment.production) event.register(OPEN);
        }
    }
}
