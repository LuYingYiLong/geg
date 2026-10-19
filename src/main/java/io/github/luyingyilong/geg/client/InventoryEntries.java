package io.github.luyingyilong.geg.client;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.geg.organization.client.ClientOrganizationScreen;

import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.ore.Badge;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Tag;
import io.github.luyingyilong.ore.screen.OreButtonWidget;
import io.github.luyingyilong.geg.mail.client.ClientMailScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
    private static final java.util.Map<Screen, Entries> ENTRIES = new java.util.WeakHashMap<>();

    @SubscribeEvent public static void onRender(ScreenEvent.Render.Pre event) {
        Entries entries = ENTRIES.get(event.getScreen());
        if (entries != null) entries.layout(event.getScreen());
    }

    private InventoryEntries() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)
                && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        Entries entries = new Entries();
        Entries previous = ENTRIES.put(event.getScreen(), entries);
        if (previous != null) previous.cancel();
        entries.layout(event.getScreen());
        event.addListener(entries.organization);
        event.addListener(entries.mail);
    }

    // Consume only these buttons' input before container slot/drop handling. All other input stays native.
    @SubscribeEvent public static void onPress(ScreenEvent.MouseButtonPressed.Pre event) {
        Entries entries = ENTRIES.get(event.getScreen());
        if (entries == null) return;
        entries.layout(event.getScreen());
        for (OreButtonWidget widget : entries.widgets()) {
            if (!widget.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) continue;
            event.getScreen().setFocused(widget);
            event.getScreen().setDragging(event.getButton() == 0);
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        Entries entries = ENTRIES.get(event.getScreen());
        if (entries == null) return;
        for (OreButtonWidget widget : entries.widgets()) {
            if (!widget.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) continue;
            event.getScreen().setDragging(false);
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent public static void onDrag(ScreenEvent.MouseDragged.Pre event) {
        Entries entries = ENTRIES.get(event.getScreen());
        if (entries == null) return;
        for (OreButtonWidget widget : entries.widgets()) {
            if (widget.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent public static void onClose(ScreenEvent.Closing event) {
        Entries entries = ENTRIES.remove(event.getScreen());
        if (entries != null) entries.widgets().forEach(OreButtonWidget::queueFree);
    }

    private static final class Entries {
        private final Badge unread = new Badge().setType(Tag.Type.PRIMARY);
        private final OreButtonWidget organization = new OreButtonWidget(
                Signals.configure(new Button(Component.translatable("organization.geg.entry")), connected -> connected.pressed().connect(connected, ClientOrganizationScreen::open)),
                Component.translatable("organization.geg.entry"));
        private final OreButtonWidget mail = new OreButtonWidget(
                Signals.configure(new Button(Component.translatable("mail.geg.entry")).setBadge(unread), connected -> connected.pressed().connect(connected, ClientMailScreen::open)),
                Component.translatable("mail.geg.entry"));

        private java.util.List<OreButtonWidget> widgets() { return java.util.List.of(organization, mail); }
        private void cancel() { widgets().forEach(OreButtonWidget::cancelInput); }
        private void layout(Screen screen) {
            int count = ClientMailScreen.unread();
            unread.setCount(count).setVisible(count > 0);
            mail.setMessage(count == 0 ? Component.translatable("mail.geg.entry")
                    : Component.translatable("mail.geg.entry_badge", unread.getDisplayText()));
            var font = Minecraft.getInstance().font;
            int y = Math.max(0, screen.height - Button.HEIGHT - 4);
            organization.place(font, 4, y, ENTRY_WIDTH);
            mail.place(font, organization.getX() + organization.getWidth() + ENTRY_GAP, y, ENTRY_WIDTH);
        }
    }
}
