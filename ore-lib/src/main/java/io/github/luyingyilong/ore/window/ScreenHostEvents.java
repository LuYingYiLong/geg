package io.github.luyingyilong.ore.window;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Attachments borrow the Screen's frame and navigation, never install a replacement Screen. */
public final class ScreenHostEvents {
    private ScreenHostEvents() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void begin(ScreenEvent.Render.Pre event) {
        RecipeCanvasFrames.begin(event.getScreen());
        var host = ScreenWindowHost.get(event.getScreen());
        if (host != null && Minecraft.getInstance().screen == event.getScreen()) { host.suspend(false); host.beginFrame(); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void overlay(ScreenEvent.Render.Post event) {
        var host = ScreenWindowHost.get(event.getScreen());
        if (host != null) host.renderOverlay(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        RecipeCanvasFrames.end(event.getScreen());
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        var host = ScreenWindowHost.get(event.getScreen());
        if (host != null) host.suspend(true);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        RecipeCanvasFrames.collectStale();
        var mc = Minecraft.getInstance();
        if (!mc.isWindowActive()||mc.screen==null) ScreenInputBridge.clear();
        for (var host : ScreenWindowHost.getHosts()) {
            if(host.getScreen() instanceof io.github.luyingyilong.ore.menu.OreMenuScreen<?> menu
                    &&menu.getMenuSession()!=null&&!menu.getMenuSession().isValid()){host.close();continue;}
            // Viewers can retain a container for Back even while Minecraft.screen is null.
            // Only explicit disposal, unreachable Screens, or session end are permanent.
            if (host.getScreen() == null) { host.close(); continue; }
            if (host.getScreen() == mc.screen) host.updateFocus(mc.isWindowActive() && mc.getOverlay() == null);
            else host.suspend(true);
        }
    }
}
