package io.github.luyingyilong.geg.organization.client;


import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.FormItem;
import io.github.luyingyilong.ore.form.*;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.TextArea;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.geg.organization.OrganizationNetwork;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.network.chat.Component;

public final class ClientOrganizationScreen {
    private static OrganizationCanvasWidget canvas;

    private ClientOrganizationScreen() {
    }

    public static void open() {
        if (canvas != null && ModularScreen.isActive("geg", "organizations")) return;
        Input name = new Input(250.0F).setPlaceholder(Component.translatable("organization.geg.name_hint"));
        name.setMaxLength(48);
        TextArea description = new TextArea(250.0F).setRows(4);
        description.setPlaceholder(Component.translatable("organization.geg.description_hint"));
        description.setMaxLength(120);
        Form form = new Form().addItem(new FormItem<>(OrganizationFields.NAME,
                Component.translatable("organization.geg.name"), FormFields.text(name)).addValidator(OrganizationFields.VALID_NAME))
                .addItem(new FormItem<>(OrganizationFields.DESCRIPTION,
                        Component.translatable("organization.geg.description"), FormFields.text(description)).addValidator(OrganizationFields.VALID_DESCRIPTION))
                .addItem(new FormItem<>(OrganizationFields.POLICY,
                        Component.translatable("organization.geg.join_policy"), OrganizationFields.policy()).addValidator(Validators.required()));
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
        OreScreen.open(new ModularScreen("geg", panel).pausesGame(false));
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
