package io.github.luyingyilong.ore.gallery;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.luyingyilong.ore.development.DevelopmentOpenRequest;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.lwjgl.glfw.GLFW;

/**
 * Development-only embedded component explorer; never mounted on production/server classpaths.
 */
public final class OreGallery {
    private static final KeyMapping OPEN = new KeyMapping("key.ore.gallery", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9, "key.categories.ore.development");
    private static final DevelopmentOpenRequest REQUEST = new DevelopmentOpenRequest(
            run -> Minecraft.getInstance().tell(run), OreGallery::open);
    private static GalleryView view;
    private static boolean initialized;

    private OreGallery() {
    }

    public static void initialize(IEventBus forgeBus) {
        if (initialized || FMLEnvironment.production) return;
        initialized = true;
        forgeBus.register(OreGallery.class);
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        if (!FMLEnvironment.production) event.register(OPEN);
    }

    public static boolean handleShortcut(int key, int scan) {
        if (!OPEN.matches(key, scan)) return false;
        REQUEST.request();
        return true;
    }

    public static void open() {
        if (FMLEnvironment.production) return;
        if (io.github.luyingyilong.ore.signal.DispatchGuard.active()) {
            REQUEST.request();
            return;
        }
        if (view != null && view.getWindow().isAlive()) {
            if (view.getWindow().getHost().isOpen()) view.getWindow().requestFocus();
            return;
        }
        GalleryView created = new GalleryView();
        view = created;
        created.getWindow().closed().connect(created.getWindow(), () -> {
            if (view == created) view = null;
        });
        created.getWindow().open();
    }

    public static GalleryView getView() {
        return view;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (handleShortcut(event.getKeyCode(), event.getScanCode())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) while (OPEN.consumeClick()) REQUEST.request();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }

    @SubscribeEvent
    public static void shutdown(GameShuttingDownEvent event) {
        stop();
    }

    public static void stop() {
        REQUEST.cancel();
        GalleryView previous = view;
        view = null;
        if (previous != null && previous.getWindow().isAlive()) {
            previous.getWindow().close();
            previous.getWindow().queueFree();
        }
    }
}

