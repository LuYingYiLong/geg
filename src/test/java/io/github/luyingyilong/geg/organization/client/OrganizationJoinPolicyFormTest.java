package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.FormItem;
import io.github.luyingyilong.ore.Radio;
import io.github.luyingyilong.ore.event.PointerEvent;
import io.github.luyingyilong.ore.form.Validators;
import io.github.luyingyilong.ore.layout.LifecycleScheduler;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Exercise the shared create/edit field through validation, silent loading and Window input. */
public final class OrganizationJoinPolicyFormTest {
    public static void main(String[] args) {
        Form create = form(), edit = form();
        Font font = new Font(id -> null, false) {
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                int[] points = text.getString().codePoints().toArray();
                int count = Math.max(1, width / 6);
                for (int from = 0; from < points.length; from += count)
                    lines.add(Component.literal(new String(points, from, Math.min(count, points.length - from))).getVisualOrderText());
                return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
            }
            @Override public int width(String text) { return text.codePointCount(0, text.length()) * 6; }
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
            @Override public int width(FormattedCharSequence text) {
                int[] count = {0};
                text.accept((index, style, codepoint) -> { count[0]++; return true; });
                return count[0] * 6;
            }
        };
        Window window = Window.embedded().setContent(create);
        try {
            var createItem = create.getItem(OrganizationFields.POLICY);
            var editItem = edit.getItem(OrganizationFields.POLICY);
            var choices = createItem.getField().getControl();
            check(choices.getChildCount() == 2, "Exactly two joining options");
            Radio unrestricted = (Radio) choices.getChild(0);
            Radio approval = (Radio) choices.getChild(1);
            check(unrestricted.getChecked() && !approval.getChecked(), "Create defaults to unrestricted");
            check(create.validate().getValues().getValue(OrganizationFields.POLICY)
                    == Organization.JoinPolicy.UNRESTRICTED, "Create submits the existing enum");

            for (int width : new int[]{127, 128, 129, 427}) {
                window.layoutViewport(width, 180, font);
                click(window, approval);
                check(approval.getChecked() && !unrestricted.getChecked(), "Selection is exclusive");
                click(window, approval);
                check(approval.getChecked(), "Clicking the selected option keeps it selected");
                check(create.validate().getValues().getValue(OrganizationFields.POLICY)
                        == Organization.JoinPolicy.APPROVAL, "Selection reaches validated submission");
                click(window, unrestricted);
            }

            int[] notifications = {0};
            var editChoices = editItem.getField().getControl();
            for (var child : editChoices.getChildren())
                ((Radio) child).toggled().connect(edit, ignored -> notifications[0]++);
            for (var policy : Organization.JoinPolicy.values()) {
                editItem.setValueNoSignal(policy);
                edit.captureInitialValues();
                check(edit.getValue(OrganizationFields.POLICY) == policy && !edit.getDirty(),
                        "Editing loads the saved condition as its clean baseline");
                check(edit.validate().getValues().getValue(OrganizationFields.POLICY) == policy,
                        "Unchanged save retains the loaded condition");
            }
            check(notifications[0] == 0, "Backfill does not trigger selection notifications");
            check(create.getValue(OrganizationFields.POLICY) == Organization.JoinPolicy.UNRESTRICTED,
                    "Create and edit sessions have independent choices");

            editItem.setValueNoSignal(Organization.JoinPolicy.APPROVAL);
            edit.captureInitialValues();
            ((Radio) editChoices.getChild(0)).activate();
            check(edit.getDirty() && edit.getValue(OrganizationFields.POLICY) == Organization.JoinPolicy.UNRESTRICTED,
                    "User changes retain dirty tracking");
            edit.reset();
            check(!edit.getDirty() && edit.getValue(OrganizationFields.POLICY) == Organization.JoinPolicy.APPROVAL,
                    "Reset restores the loaded condition");
            editItem.setValueNoSignal(null);
            check(!edit.validate().getValid(), "An empty programmatic selection cannot be submitted");
        } finally {
            window.queueFree();
            edit.queueFree();
            LifecycleScheduler.flush();
        }
        System.out.println("ORGANIZATION_JOIN_POLICY_FORM_PASS");
    }

    private static Form form() {
        Form form = new Form().addItem(new FormItem<>(OrganizationFields.POLICY,
                Component.translatable("organization.geg.join_policy"), OrganizationFields.policy())
                .addValidator(Validators.required()));
        form.setVisible(true);
        return form;
    }

    private static void click(Window window, Radio radio) {
        var bounds = radio.getAbsoluteBounds();
        int x = bounds.x() + bounds.width() / 2, y = bounds.y() + bounds.height() / 2;
        window.dispatchInput(new PointerEvent(window.getContext(), PointerEvent.Action.PRESS, x, y, 0, 0));
        window.dispatchInput(new PointerEvent(window.getContext(), PointerEvent.Action.RELEASE, x, y, 0, 0));
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
