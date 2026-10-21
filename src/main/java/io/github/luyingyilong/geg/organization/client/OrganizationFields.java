package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.ore.control.form.FieldKey;
import io.github.luyingyilong.ore.control.form.FormField;
import io.github.luyingyilong.ore.control.form.FormFields;
import io.github.luyingyilong.ore.control.form.validation.Validator;
import io.github.luyingyilong.ore.control.form.validation.Validators;

import io.github.luyingyilong.ore.control.input.Radio;
import io.github.luyingyilong.ore.control.input.RadioGroup;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationSavedData;
import net.minecraft.network.chat.Component;

final class OrganizationFields {
    static final FieldKey<String> NAME = new FieldKey<>("name", String.class);
    static final FieldKey<String> DESCRIPTION = new FieldKey<>("description", String.class);
    static final FieldKey<Organization.JoinPolicy> POLICY = new FieldKey<>("join_policy", Organization.JoinPolicy.class);
    static final Validator<String> VALID_NAME = Validators.predicate(value -> OrganizationSavedData.validName(value.strip()),
            Component.translatable("organization.geg.invalid_name"));
    static final Validator<String> VALID_DESCRIPTION = Validators.predicate(OrganizationSavedData::validDescription,
            Component.translatable("organization.geg.invalid_description"));
    private OrganizationFields() { }
    static FormField<Organization.JoinPolicy> policy() {
        Box choices = new Box().setGap(2);
        choices.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Radio unrestricted = new Radio(Component.translatable("organization.geg.join_unrestricted"));
        Radio approval = new Radio(Component.translatable("organization.geg.join_approval"));
        unrestricted.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        approval.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        choices.addChild(unrestricted);
        choices.addChild(approval);
        RadioGroup<Organization.JoinPolicy> group = new RadioGroup<>(choices, Organization.JoinPolicy.class);
        group.addRadio(Organization.JoinPolicy.UNRESTRICTED, unrestricted)
                .addRadio(Organization.JoinPolicy.APPROVAL, approval)
                .setSelectedNoSignal(Organization.JoinPolicy.UNRESTRICTED);
        return FormFields.radioGroup(Organization.JoinPolicy.class, choices, group);
    }
}
