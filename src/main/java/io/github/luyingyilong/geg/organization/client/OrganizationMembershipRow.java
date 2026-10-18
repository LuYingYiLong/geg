package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Reserve the action's width before measuring the two-line introduction. */
final class OrganizationMembershipRow extends Container {
    private final Box row = new Box().orientation(Box.Orientation.HORIZONTAL);
    private final Box details = new Box();
    private final Label description;
    private final Button open;

    OrganizationMembershipRow(OrganizationSyncPacket.Summary summary, Runnable showDetails) {
        details.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Label name = new Label(Component.literal(summary.name())).color(OreTheme.TEXT_WHITE);
        name.minimum(0, 16).sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        details.add(name);
        description = new Label(Component.literal(summary.description())).color(OreTheme.TEXT_SECONDARY)
                .wrap(Label.Wrap.CHARACTER).maxLines(2).overflow(Label.Overflow.ELLIPSIS);
        description.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        description.visible(!summary.description().isBlank());
        details.add(description);
        open = new Button(Component.translatable("organization.geg.details")).onPress(showDetails);
        open.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        row.add(details).add(open);
        addChild(row);
    }

    Label descriptionNode() { return description; }

    @Override public Size measureSize(Font font, Size available) {
        Size action = open.measureSize(font, available);
        Size text = details.measureSize(font, new Size(Math.max(0, available.width() - action.width()), available.height()));
        return new Size(Math.max(minimumWidth(), available.width()),
                Math.max(minimumHeight(), Math.max(action.height(), text.height())));
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(row, new Rect(0, 0, bounds.width(), bounds.height()), font);
    }
}
