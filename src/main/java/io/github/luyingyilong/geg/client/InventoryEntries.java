package io.github.luyingyilong.geg.client;

import io.github.luyingyilong.geg.organization.client.ClientOrganizationScreen;

import io.github.luyingyilong.geg.GegMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds the organization and mail entries to the lower-left corner of inventory screens. */
@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class InventoryEntries {
    private static final int ENTRY_WIDTH = 32;
    private static final int ENTRY_GAP = 4;
    private static final java.util.Map<net.minecraft.client.gui.screens.Screen, Button> MAIL_BUTTONS = new java.util.WeakHashMap<>();
    private static Component mailLabel() {
        int unread = io.github.luyingyilong.geg.mail.client.ClientMailScreen.unread();
        return unread == 0 ? Component.translatable("mail.geg.entry")
                : Component.translatable("mail.geg.entry_badge", unread > 99 ? "99+" : Integer.toString(unread));
    }
    @SubscribeEvent public static void onRender(ScreenEvent.Render.Pre event) {
        Button button = MAIL_BUTTONS.get(event.getScreen());
        if (button != null) button.setMessage(mailLabel());
    }

    private InventoryEntries() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)
                && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        int x = 4;
        int y = Math.max(0, event.getScreen().height - 24);
        event.addListener(Button.builder(Component.translatable("organization.geg.entry"),
                button -> ClientOrganizationScreen.open()).bounds(x, y, ENTRY_WIDTH, 20).build());
        Button mail = Button.builder(mailLabel(), button -> io.github.luyingyilong.geg.mail.client.ClientMailScreen.open())
                .bounds(x + ENTRY_WIDTH + ENTRY_GAP, y, ENTRY_WIDTH, 20).build();
        event.addListener(mail); MAIL_BUTTONS.put(event.getScreen(), mail);
    }
}
