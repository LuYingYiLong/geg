package io.github.luyingyilong.geg.organization.client;


import io.github.luyingyilong.ore.Checkbox;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.FormItem;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.input.TextArea;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Scroll;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationSavedData;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.network.chat.Component;

/** A separate edit session keeps the create form and its draft untouched. */
final class OrganizationEditForm {
    private final Label name = new Label().setOverflow(Label.Overflow.ELLIPSIS);
    private final TextArea description = new TextArea(0.0F).setRows(4);
    private final Checkbox unrestricted = new Checkbox(Component.translatable("organization.geg.join_unrestricted")).setCheckedNoSignal(true);
    private final Checkbox approval = new Checkbox(Component.translatable("organization.geg.join_approval"));
    private final Form form;
    private final Scroll scroll;

    OrganizationEditForm() {
        description.setPlaceholder(Component.translatable("organization.geg.description_hint"));
        description.setMaxLength(120);
        unrestricted.toggled().connect(unrestricted, checked -> {
            if (checked) approval.setCheckedNoSignal(false);
            else if (!approval.getChecked()) unrestricted.setCheckedNoSignal(true);
        });
        approval.toggled().connect(approval, checked -> {
            if (checked) unrestricted.setCheckedNoSignal(false);
            else if (!unrestricted.getChecked()) approval.setCheckedNoSignal(true);
        });
        unrestricted.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        approval.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Box choices = new Box().setGap(2);
        choices.addChild(unrestricted);
        choices.addChild(approval);
        choices.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        form = new Form()
                .addItem(new FormItem("name", Component.translatable("organization.geg.name"), name, () -> name.getText().getString()))
                .addItem(new FormItem("description", Component.translatable("organization.geg.description"), description))
                .addItem(new FormItem("join_policy", Component.translatable("organization.geg.join_policy"), choices,
                        () -> policy().name()));
        form.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        scroll = new Scroll().setHorizontal(Scroll.BarMode.DISABLED).setContent(form);
    }

    TextArea descriptionInput() { return description; }
    Scroll content() { return scroll; }
    Organization.JoinPolicy policy() { return approval.getChecked() ? Organization.JoinPolicy.APPROVAL : Organization.JoinPolicy.UNRESTRICTED; }
    String description() { return form.getValue("description").strip(); }

    void show(OrganizationSyncPacket.Summary summary) {
        name.setText(Component.literal(summary.name()));
        description.setTextNoSignal(summary.description());
        approval.setCheckedNoSignal(summary.joinPolicy() == Organization.JoinPolicy.APPROVAL);
        unrestricted.setCheckedNoSignal(summary.joinPolicy() == Organization.JoinPolicy.UNRESTRICTED);
        form.setVisible(true);
        scroll.scrollToTop();
    }

    void hide() { form.setVisible(false); }

    boolean validate() {
        if (!form.validate()) return false;
        if (!OrganizationSavedData.validDescription(form.getValue("description"))) {
            OrganizationFeedback.show("organization.geg.invalid_description");
            return false;
        }
        return true;
    }
}
