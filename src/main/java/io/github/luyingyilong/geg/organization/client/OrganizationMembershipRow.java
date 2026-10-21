package io.github.luyingyilong.geg.organization.client;


import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Reserve the action's width before measuring the two-line introduction. */
final class OrganizationMembershipRow extends Container {
    private final Box row = new Box().setOrientation(Box.Orientation.HORIZONTAL);
    private final Box details = new Box();
    private final Label description;
    private final Button open;

    OrganizationMembershipRow(OrganizationSyncPacket.Summary summary) {
        details.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Label name = new Label(Component.literal(summary.name()));
        name.setMinimum(0, 16).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        details.addChild(name);
        description = new Label(Component.literal(summary.description())).setThemeTypeVariation("LabelSecondary")
                .setWrap(Label.Wrap.CHARACTER).setMaxLines(2).setOverflow(Label.Overflow.ELLIPSIS);
        description.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        description.setVisible(!summary.description().isBlank());
        details.addChild(description);
        open = new Button(Component.translatable("organization.geg.details"));
        open.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        row.addChild(details);
        row.addChild(open);
        this.addChild(row);
    }

    Label descriptionNode() { return description; }
    io.github.luyingyilong.ore.signal.Signal0 detailsRequested() { return open.pressed(); }

    @Override public Size measureSize(Font font, Size available) {
        Size action = open.measureSize(font, available);
        Size text = details.measureSize(font, new Size(Math.max(0, available.width() - action.width()), available.height()));
        return new Size(Math.max(getMinimumWidth(), available.width()),
                Math.max(getMinimumHeight(), Math.max(action.height(), text.height())));
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(row, new Rect(0, 0, bounds.width(), bounds.height()), font);
    }
}
