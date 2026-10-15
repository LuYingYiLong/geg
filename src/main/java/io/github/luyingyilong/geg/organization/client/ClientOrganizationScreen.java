package io.github.luyingyilong.geg.organization.client;

import brachy.modularui.factory.ClientGUI;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import io.github.luyingyilong.geg.client.ui.Form;
import io.github.luyingyilong.geg.client.ui.FormItem;
import io.github.luyingyilong.geg.client.ui.Input;
import io.github.luyingyilong.geg.client.ui.Checkbox;
import io.github.luyingyilong.geg.client.ui.OreTheme;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.organization.OrganizationNetwork;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientOrganizationScreen {
    private static OrganizationCanvasWidget canvas;

    private ClientOrganizationScreen() {
    }

    public static void open() {
        if (canvas != null && ModularScreen.isActive("geg", "organizations")) return;
        Input name = new Input(250.0F).placeholder(Component.translatable("organization.geg.name_hint"));
        name.setMaxLength(48);
        Input description = new Input(250.0F)
                .placeholder(Component.translatable("organization.geg.description_hint"));
        description.setMaxLength(120);
        Checkbox unrestricted = new Checkbox(Component.translatable("organization.geg.join_unrestricted"))
                .checked(true);
        Checkbox approval = new Checkbox(Component.translatable("organization.geg.join_approval"));
        unrestricted.onChange(checked -> {
            if (checked) approval.checked(false);
            else if (!approval.checked()) unrestricted.checked(true);
        });
        approval.onChange(checked -> {
            if (checked) unrestricted.checked(false);
            else if (!unrestricted.checked()) approval.checked(true);
        });
        Box joinChoices = new Box().gap(2).add(unrestricted).add(approval);
        joinChoices.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        unrestricted.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        approval.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Form form = new Form().add(new FormItem("name",
                Component.translatable("organization.geg.name"), name))
                .add(new FormItem("description",
                        Component.translatable("organization.geg.description"), description))
                .add(new FormItem("join_policy",
                        Component.translatable("organization.geg.join_policy"), joinChoices,
                        () -> approval.checked() ? "approval" : "unrestricted"));
        canvas = new OrganizationCanvasWidget(form, name, description);
        canvas.showInitialState();

        ModularPanel<?> panel = new InstantOrganizationPanel();
        panel.widgetTheme(OreTheme.BACKGROUND);
        panel.full();
        panel.child(canvas.full().disableThemeBackground(true));
        panel.child(name);
        panel.child(description);
        panel.onCloseAction(() -> {
            form.setVisible(false);
            canvas = null;
        });
        ClientGUI.open(new ModularScreen("geg", panel).useTheme(OreTheme.NAME).pausesGame(false));
        OrganizationNetwork.request();
    }

    public static void acceptSnapshot(OrganizationSyncPacket packet) {
        ClientOrganizationState.acceptOrganizations(packet.organizations());
        if (canvas != null) canvas.acceptSnapshot(packet.resultKey());
        if (!packet.resultKey().isEmpty() && Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(packet.resultKey()), false);
    }

    private static final class InstantOrganizationPanel extends ModularPanel<InstantOrganizationPanel> {
        private InstantOrganizationPanel() {
            super("organizations");
        }

        @Override public boolean shouldAnimate() {
            return false;
        }
    }
}
