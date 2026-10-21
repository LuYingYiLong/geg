package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.ore.control.form.FormFields;
import io.github.luyingyilong.ore.control.form.FormValidation;
import io.github.luyingyilong.ore.control.form.FormValues;
import io.github.luyingyilong.ore.control.form.validation.Validators;


import io.github.luyingyilong.ore.control.form.Form;
import io.github.luyingyilong.ore.control.form.FormItem;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.input.TextArea;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.network.chat.Component;

/** A separate edit session keeps the create form and its draft untouched. */
final class OrganizationEditForm {
    private final Label name = new Label().setOverflow(Label.Overflow.ELLIPSIS);
    private final TextArea description = new TextArea(0.0F).setRows(4);
    private final Form form;
    private final Scroll scroll;
    private FormValues validated;

    OrganizationEditForm() {
        description.setPlaceholder(Component.translatable("organization.geg.description_hint"));
        description.setMaxLength(120);
        form = new Form()
                .addItem(new FormItem<>(OrganizationFields.NAME, Component.translatable("organization.geg.name"), FormFields.readOnly(String.class, name, () -> name.getText().getString())))
                .addItem(new FormItem<>(OrganizationFields.DESCRIPTION, Component.translatable("organization.geg.description"), FormFields.text(description)).addValidator(OrganizationFields.VALID_DESCRIPTION))
                .addItem(new FormItem<>(OrganizationFields.POLICY, Component.translatable("organization.geg.join_policy"), OrganizationFields.policy()).addValidator(Validators.required()));
        form.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        scroll = new Scroll().setHorizontal(Scroll.BarMode.DISABLED).setContent(form);
    }

    TextArea descriptionInput() { return description; }
    Scroll content() { return scroll; }
    Organization.JoinPolicy policy() { return validated.getValue(OrganizationFields.POLICY); }
    String description() { return validated.getValue(OrganizationFields.DESCRIPTION).strip(); }

    void show(OrganizationSyncPacket.Summary summary) {
        name.setText(Component.literal(summary.name()));
        description.setTextNoSignal(summary.description());
        form.getItem(OrganizationFields.POLICY).setValueNoSignal(summary.joinPolicy());
        form.captureInitialValues();
        validated = null;
        form.setVisible(true);
        scroll.scrollToTop();
    }

    void hide() { form.setVisible(false); }

    boolean validate() {
        FormValidation result = form.validate();
        validated = result.getValid() ? result.getValues() : null;
        return result.getValid();
    }
}
