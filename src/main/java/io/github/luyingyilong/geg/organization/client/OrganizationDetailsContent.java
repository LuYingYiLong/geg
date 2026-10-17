package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.geg.client.ui.Descriptions;
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

    OrganizationDetailsContent(Table<OrganizationSyncPacket.Member> members) {
        members.minimum(0, 60).sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        column.add(info).add(members);
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

    @Override public Size measureSize(Font font, Size available) { return column.measureSize(font, available); }
    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(column, new Rect(0, 0, bounds.width(), bounds.height()), font);
    }
}
