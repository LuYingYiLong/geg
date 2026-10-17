package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.geg.client.ui.Button;
import io.github.luyingyilong.geg.client.ui.Slot;
import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.mail.MailItem;
import net.minecraft.network.chat.Component;

import java.util.List;

/** A fixed-height attachment strip; its natural item width must not widen the detail panel. */
final class MailAttachmentsView extends Container {
    private final Box items = new Box().orientation(Box.Orientation.HORIZONTAL).gap(2);
    private final Scroll scroll = new Scroll().horizontal(Scroll.BarMode.NEVER).vertical(Scroll.BarMode.DISABLED).content(items);
    private final Button claim = new Button(Component.translatable("mail.geg.claim")).type(Button.Type.PRIMARY);
    private final Box row = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4).add(scroll).add(claim);

    MailAttachmentsView(Runnable action) {
        items.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_FILL);
        scroll.minimum(0, Slot.SIZE).sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        claim.onPressed(action).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        addChild(row);
        sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
    }

    void attachments(List<MailItem> values, boolean hasAttachments, boolean claimed) {
        for (Control child : items.children()) items.removeChild(child);
        for (MailItem value : values) items.add(new Slot().item(value.item(), value.quantity()));
        scroll.visible(!values.isEmpty());
        claim.label(Component.translatable(claimed ? "mail.geg.claimed" : "mail.geg.claim"))
                .disabled(!hasAttachments || claimed).visible(hasAttachments);
        claim.sizeFlagsHorizontal(values.isEmpty() ? SizeFlags.SIZE_EXPAND_FILL : SizeFlags.SIZE_SHRINK_END);
        visible(hasAttachments);
        requestLayout();
    }

    Scroll scroll() { return scroll; }
    Button claimButton() { return claim; }

    @Override public Size measureSize(net.minecraft.client.gui.Font font, Size available) {
        return new Size(0, visible() ? Math.max(Slot.SIZE, claim.minimumHeight()) : 0);
    }

    @Override public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
        super.layout(bounds, font);
        fitChild(row, new Rect(0, 0, bounds.width(), bounds.height()), font);
    }
}
