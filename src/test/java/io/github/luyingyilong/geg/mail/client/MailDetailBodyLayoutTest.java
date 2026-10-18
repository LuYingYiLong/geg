package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.Slot;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Exercise the production detail tree, including the Scroll surrounding its body and reward row. */
public final class MailDetailBodyLayoutTest {
    private static final String TEXT = "你在自由市场购买、求购收到或撤单退回的物品已送达，请在下方领取附件。背包空间不足时，剩余物品将继续保管在邮件中。";

    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().codePointCount(0, text.getString().length()) * 6; }
            @Override public int width(FormattedCharSequence text) {
                int[] length = {0}; text.accept((index, style, codepoint) -> { length[0]++; return true; });
                return length[0] * 6;
            }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                int[] points = text.getString().codePoints().toArray();
                int perLine = Math.max(1, width / 6);
                for (int from = 0; from < points.length; from += perLine)
                    lines.add(Component.literal(new String(points, from, Math.min(perLine, points.length - from))).getVisualOrderText());
                return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
            }
        };
        Label body = new Label(Component.literal(TEXT)).wrap(Label.Wrap.WORD);
        int[] claims = {0};
        MailAttachmentsView rewards = new MailAttachmentsView(() -> claims[0]++);
        rewards.attachments(List.of(), true, false);
        rewards.claimButton().label(Component.literal("领取附件")).sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END);
        Scroll itemScroll = rewards.scroll();
        Box items = (Box) itemScroll.children().stream().filter(Box.class::isInstance).findFirst().orElseThrow();
        for (int i = 0; i < 100; i++) items.add(new Control().minimum(Slot.SIZE, Slot.SIZE));
        itemScroll.visible(true);
        MailDetailBody content = new MailDetailBody(body, new Control().minimum(0, 2), rewards);
        Scroll detail = new Scroll().horizontal(Scroll.BarMode.DISABLED).content(content);
        detail.minimum(0, 16).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Control title = new Control().minimum(0, 12).preferred(0, 12);
        Control author = new Control().minimum(0, 24).preferred(0, 24);
        Panel panel = new Panel(null).child(new Padding(Padding.Insets.all(4))
                .child(new Box().gap(4).add(title).add(author).add(detail)));
        Scroll columns = new Scroll().vertical(Scroll.BarMode.DISABLED).content(new MailColumns(
                new Scroll().content(new Control()), panel));
        Window window = new Window(true).content(columns);
        for (String text : new String[]{TEXT, TEXT.repeat(20), TEXT}) {
            body.text(Component.literal(text));
            for (int width : new int[]{127, 128, 129, 480, 854}) {
                window.layoutViewport(width, 240, font);
                check(content.size().width() == detail.viewport().width());
                check(content.contentExtent().width() == detail.viewport().width() && detail.maxScrollX() == 0);
                if (font.width(body.text()) > body.size().width()) check(body.lines(font, body.size().width()).size() > 1);
                check(body.lines(font, body.size().width()).stream().allMatch(line -> font.width(line) <= body.size().width()));
                int naturalHeight = body.lines(font, body.size().width()).size() * body.lineHeight(font);
                check(body.size().height() >= naturalHeight && SizeFlags.expands(body.sizeFlagsVertical()));
                check(rewards.absoluteBounds().bottom() == content.absoluteBounds().y() + content.contentExtent().height());
                check(rewards.claimButton().absoluteBounds().right() == content.absoluteBounds().right());
                check(itemScroll.maxScrollX() > 0 && !itemScroll.horizontalVisible());
                if (detail.maxScrollY() == 0) check(rewards.absoluteBounds().bottom() == detail.absoluteBounds().bottom());
                // The reward remains reachable after scrolling a long body or a narrow two-column page.
                columns.setScroll(columns.maxScrollX(), 0);
                detail.setScroll(0, detail.maxScrollY());
                Rect button = rewards.claimButton().absoluteBounds().translated(-columns.scrollX(), -detail.scrollY());
                check(button.x() >= 0 && button.right() <= width && button.bottom() <= 240);
                check(window.pointerPressed(button.x() + 2, button.y() + 2, null));
                check(window.pointerReleased(button.x() + 2, button.y() + 2));
            }
        }
        check(claims[0] == 15);
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Mail detail wrapping or claim visibility regression");
    }
}
