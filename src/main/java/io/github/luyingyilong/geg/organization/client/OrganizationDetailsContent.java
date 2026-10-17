package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.geg.client.ui.Descriptions;
import io.github.luyingyilong.geg.client.ui.Button;
import io.github.luyingyilong.geg.client.ui.Label;
import io.github.luyingyilong.geg.client.ui.Table;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Organization facts above the existing independently scrolling member table. */
final class OrganizationDetailsContent extends Container {
    private final Descriptions info = new Descriptions().columns(1)
            .add("name", Component.translatable("organization.geg.name"), Component.empty())
            .add("description", Component.translatable("organization.geg.description"), Component.empty())
            .add("joinPolicy", Component.translatable("organization.geg.join_policy"), Component.empty());
    private final Box column = new Box().gap(8);
    private final Box profileRow = new Box().orientation(Box.Orientation.HORIZONTAL).gap(8);
    private final Button edit = new Button(Component.translatable("organization.geg.edit"));

    OrganizationDetailsContent(Table<OrganizationSyncPacket.Member> members) {
        info.item("description").contentNode().wrap(Label.Wrap.CHARACTER)
                .maxLines(Integer.MAX_VALUE).overflow(Label.Overflow.VISIBLE);
        members.minimum(0, 60).sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        info.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        edit.sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        edit.visible(false);
        profileRow.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        profileRow.add(info).add(edit);
        column.add(profileRow).add(members);
        addChild(column);
    }

    void update(OrganizationSyncPacket.Summary summary) {
        info.item("name").content(Component.literal(summary.name()));
        info.item("description").content(summary.description().isBlank()
                ? Component.translatable("organization.geg.no_description") : Component.literal(summary.description()));
        info.item("joinPolicy").content(Component.translatable(summary.joinPolicy() == Organization.JoinPolicy.APPROVAL
                ? "organization.geg.join_approval" : "organization.geg.join_unrestricted"));
    }

    Descriptions descriptions() { return info; }
    void onEdit(Runnable action) { edit.onPress(action); }
    void editable(boolean editable) { edit.visible(editable); }
    Button editButton() { return edit; }

    private void measureProfile(Font font, Size available) {
        Size action = edit.visible() ? edit.measureSize(font, available) : new Size(0, 0);
        int infoWidth = Math.max(0, available.width() - action.width() - (edit.visible() ? 8 : 0));
        int height = Math.max(action.height(), info.measureSize(font, new Size(infoWidth, available.height())).height());
        if (profileRow.minimumHeight() != height) profileRow.minimum(0, height);
    }

    @Override public Size measureSize(Font font, Size available) {
        measureProfile(font, available);
        return column.measureSize(font, available);
    }
    @Override public void layout(Rect bounds, Font font) {
        measureProfile(font, new Size(bounds.width(), bounds.height()));
        super.layout(bounds, font);
        fitChild(column, new Rect(0, 0, bounds.width(), bounds.height()), font);
    }
}
