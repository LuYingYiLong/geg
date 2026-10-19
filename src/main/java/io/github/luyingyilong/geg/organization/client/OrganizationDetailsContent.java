package io.github.luyingyilong.geg.organization.client;


import io.github.luyingyilong.ore.Descriptions;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.Table;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Organization facts above the existing independently scrolling member table. */
final class OrganizationDetailsContent extends Container {
    private final Descriptions info = new Descriptions().setColumns(1)
            .add("name", Component.translatable("organization.geg.name"), Component.empty())
            .add("description", Component.translatable("organization.geg.description"), Component.empty())
            .add("joinPolicy", Component.translatable("organization.geg.join_policy"), Component.empty());
    private final Box column = new Box().setGap(8);
    private final Box profileRow = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(8);
    private final Button edit = new Button(Component.translatable("organization.geg.edit"));

    OrganizationDetailsContent(Table<OrganizationSyncPacket.Member> members) {
        info.getItem("description").getContentNode().setWrap(Label.Wrap.CHARACTER)
                .setMaxLines(Integer.MAX_VALUE).setOverflow(Label.Overflow.VISIBLE);
        members.setMinimum(0, 60).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        info.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        edit.setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        edit.setVisible(false);
        profileRow.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        profileRow.add(info).add(edit);
        column.add(profileRow).add(members);
        addChild(column);
    }

    void update(OrganizationSyncPacket.Summary summary) {
        info.getItem("name").setContent(Component.literal(summary.name()));
        info.getItem("description").setContent(summary.description().isBlank()
                ? Component.translatable("organization.geg.no_description") : Component.literal(summary.description()));
        info.getItem("joinPolicy").setContent(Component.translatable(summary.joinPolicy() == Organization.JoinPolicy.APPROVAL
                ? "organization.geg.join_approval" : "organization.geg.join_unrestricted"));
    }

    Descriptions descriptions() { return info; }
    io.github.luyingyilong.ore.signal.Signal0 editRequested() { return edit.pressed(); }
    void editable(boolean editable) { edit.setVisible(editable); }
    Button editButton() { return edit; }

    private void measureProfile(Font font, Size available) {
        Size action = edit.getVisible() ? edit.measureSize(font, available) : new Size(0, 0);
        int infoWidth = Math.max(0, available.width() - action.width() - (edit.getVisible() ? 8 : 0));
        int height = Math.max(action.height(), info.measureSize(font, new Size(infoWidth, available.height())).height());
        if (profileRow.getMinimumHeight() != height) profileRow.setMinimum(0, height);
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
