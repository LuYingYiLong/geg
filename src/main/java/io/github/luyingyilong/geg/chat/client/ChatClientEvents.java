package io.github.luyingyilong.geg.chat.client;

import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.ore.theme.OreFonts;
import io.github.luyingyilong.ore.text.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class ChatClientEvents {
    private ChatClientEvents() {
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        // The sleeping subclass owns the vanilla leave-bed button and STOP_SLEEPING packet.
        if (!(event.getNewScreen() instanceof ChatScreen screen) || screen instanceof InBedChatScreen
                || !ClientChatState.enabled()) return;
        String initial = ((ChatScreenInitial) screen).geg$initial();
        event.setCanceled(true);
        Minecraft.getInstance().execute(() -> ClientChatScreen.open(initial));
    }

    @SubscribeEvent
    public static void originalHud(RenderGuiOverlayEvent.Pre event) {
        if (ClientChatState.enabled() && event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void received(ClientChatReceivedEvent event) {
        if (!ClientChatState.enabled()) return;
        if (event instanceof ClientChatReceivedEvent.System system && system.isOverlay()) return;
        ClientChatState.temporary(event.getMessage());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientChatState.enabled() || ClientChatScreen.isOpen() || mc.options.hideGui || mc.player == null) return;
        ChatVisiblity visibility = mc.options.chatVisibility().get();
        if (visibility == ChatVisiblity.HIDDEN) return;
        var graphics = event.getGuiGraphics();
        long now = System.nanoTime() / 1_000_000;
        int width = Math.max(40, Math.min(320, mc.getWindow().getGuiScaledWidth() - 8));
        int y = mc.getWindow().getGuiScaledHeight() - 44, rendered = 0;
        var rows = ClientChatState.hud();
        for (int index = rows.size() - 1; index >= 0 && rendered < 9; index--) {
            var row = rows.get(index);
            long age = now - row.time();
            if (age >= 10000 || !row.system() && visibility != ChatVisiblity.FULL) continue;
            int alpha = age < 8000 ? 255 : Math.max(0, (int) ((10000 - age) * 255 / 2000));
            var layout = OreText.layout(row.text(), OreFonts.BODY, 8, width - 4, TextLayoutEngine.Wrap.WORD,
                    Integer.MAX_VALUE, false, FontMetricsPolicy.DEFAULT, mc.font);
            var lines = layout.getLines();
            int lineHeight = (int)Math.ceil(layout.getMetrics().lineHeight());
            for (int line = lines.size() - 1; line >= 0 && rendered < 9; line--) {
                y -= lineHeight;
                if (y < 4) return;
                graphics.fill(2, y, width + 2, y + lineHeight, (alpha / 3) << 24);
                TextRenderer.draw(graphics, layout.getLineLayout(line), 4, y, (alpha << 24) | 0xFFFFFF, false);
                rendered++;
            }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientChatState.clear();
    }
}
