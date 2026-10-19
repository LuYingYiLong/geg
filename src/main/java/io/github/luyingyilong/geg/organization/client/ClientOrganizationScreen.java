package io.github.luyingyilong.geg.organization.client;


import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import io.github.luyingyilong.ore.Checkbox;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.FormItem;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.TextArea;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.geg.organization.OrganizationNetwork;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.network.chat.Component;

public final class ClientOrganizationScreen {
    private static OrganizationCanvasWidget canvas;

    private ClientOrganizationScreen() {
    }

    public static void open() {
        if (canvas != null && ModularScreen.isActive("geg", "organizations")) return;
        Input name = new Input(250.0F).placeholder(Component.translatable("organization.geg.name_hint"));
        name.setMaxLength(48);
        TextArea description = new TextArea(250.0F).rows(4);
        description.placeholder(Component.translatable("organization.geg.description_hint"));
        description.setMaxLength(120);
        Checkbox unrestricted = new Checkbox(Component.translatable("organization.geg.join_unrestricted"))
                .setCheckedNoSignal(true);
        Checkbox approval = new Checkbox(Component.translatable("organization.geg.join_approval"));
        unrestricted.toggled().connect(unrestricted, checked -> {
            if (checked) approval.setCheckedNoSignal(false);
            else if (!approval.checked()) unrestricted.setCheckedNoSignal(true);
        });
        approval.toggled().connect(approval, checked -> {
            if (checked) unrestricted.setCheckedNoSignal(false);
            else if (!unrestricted.checked()) approval.setCheckedNoSignal(true);
        });
        Box joinChoices = new Box().gap(2).add(unrestricted).add(approval);
        joinChoices.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        unrestricted.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        approval.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Form form = new Form().add(new FormItem("name",
                Component.translatable("organization.geg.name"), name))
                .add(new FormItem("description",
                        Component.translatable("organization.geg.description"), description))
                .add(new FormItem("join_policy",
                        Component.translatable("organization.geg.join_policy"), joinChoices,
                        () -> approval.checked() ? "approval" : "unrestricted"));
        OrganizationEditForm editor = new OrganizationEditForm();
        canvas = new OrganizationCanvasWidget(form, name, description, editor);
        canvas.showInitialState();

        ModularPanel<?> panel = new InstantOrganizationPanel();
        panel.disableThemeBackground(true);
        panel.full();
        panel.child(canvas.full().disableThemeBackground(true));
        panel.child(name);
        panel.child(description);
        panel.child(editor.descriptionInput());
        panel.onCloseAction(() -> {
            form.setVisible(false);
            editor.hide();
            canvas = null;
        });
        OreScreen.open(new ModularScreen("geg", panel).useTheme(OreTheme.NAME).pausesGame(false));
        OrganizationNetwork.request();
    }

    public static void acceptSnapshot(OrganizationSyncPacket packet) {
        ClientOrganizationState.acceptOrganizations(packet);
        if (canvas != null) canvas.acceptSnapshot(packet.resultKey());
        OrganizationFeedback.show(packet.resultKey());
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
