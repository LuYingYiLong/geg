package io.github.luyingyilong.geg.mail.client;

import brachy.modularui.screen.*;
import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.client.ui.*;
import io.github.luyingyilong.geg.client.ui.input.Input;
import io.github.luyingyilong.geg.client.ui.input.NumberInput;
import io.github.luyingyilong.geg.client.ui.input.TextArea;
import io.github.luyingyilong.geg.client.ui.screen.OreScreen;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import io.github.luyingyilong.geg.mail.*;
import io.github.luyingyilong.geg.mail.MailSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class ClientMailScreen {
    private static MailCanvasWidget canvas;
    private static int unread;
    private static boolean admin;
    public static int unread() { return unread; }
    public static boolean admin() { return admin; }
    private ClientMailScreen() { }
    public static void open() { open(null); }
    public static void open(UUID organization) {
        if (canvas != null && ModularScreen.isActive("geg", "mail")) return;
        Input title = new Input(250).placeholder(Component.translatable("mail.geg.title_hint")); title.setMaxLength(128);
        TextArea body = new TextArea(250); body.placeholder(Component.translatable("mail.geg.body_hint"));
        Input target = new Input(250).placeholder(Component.translatable("mail.geg.target_hint")); target.setMaxLength(64);
        NumberInput coin = new NumberInput(250, NumberInput.Kind.COIN).allowZero(true); coin.value(new brachy.modularui.value.StringValue("0.00")); coin.setText("0.00");
        MailCanvasWidget next = new MailCanvasWidget(title, body, target, coin, organization);
        canvas = next;
        ModularPanel<?> panel = new MailPanel();
        panel.disableThemeBackground(true); panel.full();
        panel.child(next.full().disableThemeBackground(true));
        panel.child(title); panel.child(body); panel.child(target); panel.child(coin);
        panel.onCloseAction(() -> { next.close(); if (canvas == next) canvas = null; });
        OreScreen.open(new ModularScreen("geg", panel).useTheme(OreTheme.NAME).pausesGame(false));
        next.initialize();
    }
    public static void accept(MailSyncPacket packet) {
        unread = packet.unread(); admin = packet.admin();
        if (canvas != null) canvas.accept(packet);
    }
    private static final class MailPanel extends ModularPanel<MailPanel> {
        private MailPanel() { super("mail"); }
        @Override public boolean shouldAnimate() { return false; }
    }
    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (canvas != null) canvas.close(); canvas = null; unread = 0; admin = false;
    }
}
