package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.ore.Slot;
import io.github.luyingyilong.ore.layout.*;
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
        check(rewards.visible() && rewards.claimButton().visible() && !rewards.claimButton().disabled());
        Scroll scroll = rewards.scroll();
        Box items = (Box) scroll.children().stream().filter(Box.class::isInstance).findFirst().orElseThrow();
        // Geometry is independent of item registries; the server GameTest verifies item binding and NBT.
        for (int i = 0; i < 100; i++) items.add(new Control().minimum(Slot.SIZE, Slot.SIZE)
                .sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER));
        scroll.visible(true);
        rewards.claimButton().sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END);
        for (int width : new int[]{120, 127, 128, 129, 300, 600}) {
            rewards.layout(new Rect(0, 0, width, Slot.SIZE), font);
            check(rewards.measureSize(font, new Size(width, 300)).equals(new Size(0, Slot.SIZE)));
            check(scroll.maxScrollX() > 0 && scroll.maxScrollY() == 0);
            check(!scroll.horizontalVisible() && !scroll.verticalVisible());
            check(scroll.bounds().right() + 4 == rewards.claimButton().bounds().x());
            check(rewards.claimButton().bounds().right() == width);
            check(scroll.size().height() == Slot.SIZE && items.size().height() == Slot.SIZE);
            check(items.children().get(99).bounds().right() == 100 * Slot.SIZE + 99 * 2);
            scroll.setScroll(0, 0);
            check(io.github.luyingyilong.ore.EventFixture.scroll(rewards,1, 1, -1, false) && scroll.scrollX() > 0);
            scroll.setScroll(Integer.MAX_VALUE, 0);
            check(scroll.scrollX() == scroll.maxScrollX());
        }
        // A completed attachment, including coin-only mail, retains its disabled claim button.
        rewards.attachments(List.of(), true, true);
        check(rewards.visible() && rewards.claimButton().visible() && rewards.claimButton().disabled());
        check(!rewards.claimButton().acceptsFocus());
        rewards.layout(new Rect(0, 0, 300, Slot.SIZE), font);
        Rect button = rewards.claimButton().absoluteBounds();
        io.github.luyingyilong.ore.EventFixture.press(rewards,button.x()+1,button.y()+1);
        check(!rewards.claimButton().activate() && claims[0] == 0);
        check(rewards.claimButton().bounds().width() == 300);
        // Switching to an unclaimed or partially claimed mail re-enables the same button.
        rewards.attachments(List.of(), true, false);
        check(rewards.claimButton().visible() && !rewards.claimButton().disabled());
        rewards.attachments(List.of(), false, false);
        check(!rewards.visible() && !rewards.claimButton().visible() && items.children().isEmpty());
    }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Mail attachment strip layout regression");
    }
}
