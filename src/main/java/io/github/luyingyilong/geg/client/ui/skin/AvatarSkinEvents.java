package io.github.luyingyilong.geg.client.ui.skin;

import io.github.luyingyilong.geg.GegMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Collect skins while players are online, even when no Ore avatar is currently displayed. */
@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class AvatarSkinEvents {
    private static ClientPacketListener connection;
    private static int ticks;

    private AvatarSkinEvents() { }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var current = Minecraft.getInstance().getConnection();
        if (current != connection) {
            connection = current;
            ticks = 0;
            AvatarSkins.clearOnlineObservations();
        }
        if (current == null || ticks++ % 20 != 0) return;
        int started = 0;
        for (var player : current.getOnlinePlayers()) {
            if (AvatarSkins.observeOnline(player.getProfile()) && ++started == 4) break;
        }
    }
}
