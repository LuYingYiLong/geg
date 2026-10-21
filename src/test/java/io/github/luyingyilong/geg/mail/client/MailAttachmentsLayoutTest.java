package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Scroll;

import io.github.luyingyilong.ore.control.inventory.Slot;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;

import java.util.List;

public final class MailAttachmentsLayoutTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        int[] claims = {0};
        MailAttachmentsView rewards = new MailAttachmentsView(() -> claims[0]++);
        rewards.attachments(List.of(), true, false);
        check(rewards.getVisible() && rewards.claimButton().getVisible() && !rewards.claimButton().getDisabled());
        Scroll scroll = rewards.scroll();
        Box items = (Box) scroll.getChildren().stream().filter(Box.class::isInstance).findFirst().orElseThrow();
        // Geometry is independent of item registries; the server GameTest verifies item binding and NBT.
        for (int i = 0; i < 100; i++) { items.addChild(new Control().setMinimum(Slot.SIZE, Slot.SIZE).setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER)); }
        scroll.setVisible(true);
        rewards.claimButton().setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END);
        int rowHeight=Math.max(Slot.SIZE,rewards.claimButton().getMinimumHeight());
        for (int width : new int[]{120, 127, 128, 129, 300, 600}) {
            rewards.layout(new Rect(0, 0, width, rowHeight), font);
            check(rewards.measureSize(font, new Size(width, 300)).equals(new Size(0, rowHeight)));
            check(scroll.getMaxScrollX() > 0 && scroll.getMaxScrollY() == 0);
            check(!scroll.getHorizontalVisible() && !scroll.getVerticalVisible());
            check(scroll.getBounds().right() + 4 == rewards.claimButton().getBounds().x());
            check(rewards.claimButton().getBounds().right() == width);
            check(scroll.getSize().height() == rowHeight && items.getSize().height() == rowHeight);
            check(rewards.claimButton().getBounds().height()<=rowHeight);
            check(items.getChildren().get(0).getBounds().height()==Slot.SIZE);
            check(items.getChildren().get(99).getBounds().right() == 100 * Slot.SIZE + 99 * 2);
            scroll.setScroll(0, 0);
            check(io.github.luyingyilong.ore.support.EventFixture.scroll(rewards,1, 1, -1, false) && scroll.getScrollX() > 0);
            scroll.setScroll(Integer.MAX_VALUE, 0);
            check(scroll.getScrollX() == scroll.getMaxScrollX());
        }
        // A completed attachment, including coin-only mail, retains its disabled claim button.
        rewards.attachments(List.of(), true, true);
        check(rewards.getVisible() && rewards.claimButton().getVisible() && rewards.claimButton().getDisabled());
        check(!rewards.claimButton().acceptsFocus());
        rewards.layout(new Rect(0, 0, 300, rowHeight), font);
        Rect button = rewards.claimButton().getAbsoluteBounds();
        io.github.luyingyilong.ore.support.EventFixture.press(rewards,button.x()+1,button.y()+1);
        check(!rewards.claimButton().activate() && claims[0] == 0);
        check(rewards.claimButton().getBounds().width() == 300);
        // Switching to an unclaimed or partially claimed mail re-enables the same button.
        rewards.attachments(List.of(), true, false);
        check(rewards.claimButton().getVisible() && !rewards.claimButton().getDisabled());
        rewards.attachments(List.of(), false, false);
        check(!rewards.getVisible() && !rewards.claimButton().getVisible() && items.getChildren().isEmpty());
    }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Mail attachment strip layout regression");
    }
}
