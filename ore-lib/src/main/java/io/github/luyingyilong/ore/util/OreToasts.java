package io.github.luyingyilong.ore.util;

import io.github.luyingyilong.ore.Toast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * One client-wide host, painted once after either the HUD or the active screen.
 */
public final class OreToasts {
    private static final ToastQueue QUEUE = new ToastQueue();

    private OreToasts() {
    }

    /**
     * Call on the Minecraft client thread; network handlers already enqueue onto it.
     */
    public static Toast show(Toast toast) {
        return show(UUID.randomUUID().toString(), toast);
    }

    public static Toast show(String key, Toast toast) {
        return QUEUE.show(key, toast);
    }

    public static void clear() {
        QUEUE.clear();
    }

    private static void draw(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        var frames = QUEUE.layout(minecraft.font, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        if (frames.isEmpty()) return;
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0.0F, 0.0F, 1000.0F);
            for (var frame : frames) frame.toast().drawGui(graphics, minecraft.font, frame.area());
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) draw(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void screen(ScreenEvent.Render.Post event) {
        draw(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
