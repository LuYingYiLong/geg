package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Scroll;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.inventory.Slot;
import io.github.luyingyilong.geg.mail.MailItem;
import net.minecraft.network.chat.Component;

import java.util.List;

/** A fixed-height attachment strip; its natural item width must not widen the detail panel. */
final class MailAttachmentsView extends Container {
    private final Box items = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(2);
    private final Scroll scroll = new Scroll().setHorizontal(Scroll.BarMode.NEVER).setVertical(Scroll.BarMode.DISABLED).setContent(items);
    private final Button claim = new Button(Component.translatable("mail.geg.claim")).setThemeTypeVariation("PrimaryButton");
    private final Box row = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
    {
        row.addChild(scroll);
        row.addChild(claim);
    }

    MailAttachmentsView(Runnable action) {
        items.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        scroll.setMinimum(0, Slot.SIZE).setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        Signals.configure(claim, connected -> connected.pressed().connect(connected, action)).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        this.addChild(row);
        setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
    }

    void attachments(List<MailItem> values, boolean hasAttachments, boolean claimed) {
        for (Control child : items.getChildren()) { if (child.getParent() != null) child.getParent().removeChild(child); child.queueFree(); }
        for (MailItem value : values) { items.addChild(new Slot().setItem(value.item(), value.quantity())); }
        scroll.setVisible(!values.isEmpty());
        claim.setLabel(Component.translatable(claimed ? "mail.geg.claimed" : "mail.geg.claim"))
                .setDisabled(!hasAttachments || claimed).setVisible(hasAttachments);
        claim.setSizeFlagsHorizontal(values.isEmpty() ? SizeFlags.SIZE_EXPAND_FILL : SizeFlags.SIZE_SHRINK_END);
        setVisible(hasAttachments);
        requestLayout();
    }

    Scroll scroll() { return scroll; }
    Button claimButton() { return claim; }

    @Override public Size measureSize(net.minecraft.client.gui.Font font, Size available) {
        return new Size(0, getVisible() ? Math.max(Slot.SIZE, claim.getMinimumHeight()) : 0);
    }

    @Override public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
        super.layout(bounds, font);
        fitChild(row, new Rect(0, 0, bounds.width(), bounds.height()), font);
    }
}
