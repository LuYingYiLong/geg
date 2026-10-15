package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.geg.GegMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds the organization entry to the lower-left corner of inventory screens. */
@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class InventoryOrganizationEntry {
    private InventoryOrganizationEntry() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)
                && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        int x = 4;
        int y = Math.max(0, event.getScreen().height - 24);
        event.addListener(Button.builder(Component.translatable("organization.geg.entry"),
                button -> ClientOrganizationScreen.open()).bounds(x, y, 26, 20).build());
    }
}
