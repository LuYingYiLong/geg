package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.geg.client.ui.Checkbox;
import io.github.luyingyilong.geg.client.ui.Form;
import io.github.luyingyilong.geg.client.ui.FormItem;
import io.github.luyingyilong.geg.client.ui.Label;
import io.github.luyingyilong.geg.client.ui.input.TextArea;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Scroll;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationSavedData;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.network.chat.Component;

/** A separate edit session keeps the create form and its draft untouched. */
final class OrganizationEditForm {
    private final Label name = new Label().overflow(Label.Overflow.ELLIPSIS);
    private final TextArea description = new TextArea(0.0F).rows(4);
    private final Checkbox unrestricted = new Checkbox(Component.translatable("organization.geg.join_unrestricted")).checked(true);
    private final Checkbox approval = new Checkbox(Component.translatable("organization.geg.join_approval"));
    private final Form form;
    private final Scroll scroll;

    OrganizationEditForm() {
        description.placeholder(Component.translatable("organization.geg.description_hint"));
        description.setMaxLength(120);
        unrestricted.onChange(checked -> {
            if (checked) approval.checked(false);
            else if (!approval.checked()) unrestricted.checked(true);
        });
        approval.onChange(checked -> {
            if (checked) unrestricted.checked(false);
            else if (!unrestricted.checked()) approval.checked(true);
        });
        unrestricted.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        approval.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Box choices = new Box().gap(2).add(unrestricted).add(approval);
        choices.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        form = new Form()
                .add(new FormItem("name", Component.translatable("organization.geg.name"), name, () -> name.text().getString()))
                .add(new FormItem("description", Component.translatable("organization.geg.description"), description))
                .add(new FormItem("join_policy", Component.translatable("organization.geg.join_policy"), choices,
                        () -> policy().name()));
        form.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        scroll = new Scroll().horizontal(Scroll.BarMode.DISABLED).content(form);
    }

    TextArea descriptionInput() { return description; }
    Scroll content() { return scroll; }
    Organization.JoinPolicy policy() { return approval.checked() ? Organization.JoinPolicy.APPROVAL : Organization.JoinPolicy.UNRESTRICTED; }
    String description() { return form.value("description").strip(); }

    void show(OrganizationSyncPacket.Summary summary) {
        name.text(Component.literal(summary.name()));
        description.setText(summary.description());
        approval.checked(summary.joinPolicy() == Organization.JoinPolicy.APPROVAL);
        unrestricted.checked(summary.joinPolicy() == Organization.JoinPolicy.UNRESTRICTED);
        form.setVisible(true);
        scroll.scrollToTop();
    }

    void hide() { form.setVisible(false); }

    boolean validate() {
        if (!form.validate()) return false;
        if (!OrganizationSavedData.validDescription(form.value("description"))) {
            OrganizationFeedback.show("organization.geg.invalid_description");
            return false;
        }
        return true;
    }
}
