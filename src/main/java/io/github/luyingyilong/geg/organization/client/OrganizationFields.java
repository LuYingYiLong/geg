package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.ore.Checkbox;
import io.github.luyingyilong.ore.form.*;
import io.github.luyingyilong.ore.layout.Control;
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
    static FormField<Organization.JoinPolicy> policy(Control root, Checkbox unrestricted, Checkbox approval) {
        return FormFields.custom(Organization.JoinPolicy.class, root,
                () -> approval.getChecked() ? Organization.JoinPolicy.APPROVAL : Organization.JoinPolicy.UNRESTRICTED,
                value -> {
                    approval.setCheckedNoSignal(value == Organization.JoinPolicy.APPROVAL);
                    unrestricted.setCheckedNoSignal(value == Organization.JoinPolicy.UNRESTRICTED);
                }, unrestricted.toggled(), approval.toggled());
    }
}
