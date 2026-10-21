package io.github.luyingyilong.ore.control.overlay.toast;

import io.github.luyingyilong.ore.control.overlay.Toast;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.signal.DispatchGuard;
import io.github.luyingyilong.ore.window.WindowContext;
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
    private static final WindowContext CONTEXT = new WindowContext(false);

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

    private static void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        if (!CONTEXT.trySetDimensions(window.getScreenWidth(), window.getScreenHeight(), window.getWidth(), window.getHeight(), window.getGuiScale())) return;
        try (var phase = DispatchGuard.enter(); var scope = CONTEXT.enter();
             var context = new DrawContext(graphics, minecraft.font, CONTEXT,
                     new Rect(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight()), mouseX, mouseY, partialTick)) {
            var frames = QUEUE.layout(context.getFont(), context.getViewport().width(), context.getViewport().height());
            if (frames.isEmpty()) return;
            graphics.flush();
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0.0F, 0.0F, 1000.0F);
                for (var frame : frames) frame.toast().draw(context, 0, 0);
            } finally {
                try { graphics.flush(); }
                finally { graphics.pose().popPose(); }
            }
        }
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) draw(event.getGuiGraphics(), -1, -1, event.getPartialTick());
    }

    @SubscribeEvent
    public static void screen(ScreenEvent.Render.Post event) {
        draw(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
