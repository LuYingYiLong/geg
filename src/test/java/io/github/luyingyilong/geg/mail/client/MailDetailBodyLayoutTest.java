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
        Label body = new Label(Component.literal(TEXT)).setWrap(Label.Wrap.WORD);
        int[] claims = {0};
        MailAttachmentsView rewards = new MailAttachmentsView(() -> claims[0]++);
        rewards.attachments(List.of(), true, false);
        rewards.claimButton().setLabel(Component.literal("领取附件")).setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_END);
        Scroll itemScroll = rewards.scroll();
        Box items = (Box) itemScroll.getChildren().stream().filter(Box.class::isInstance).findFirst().orElseThrow();
        for (int i = 0; i < 100; i++) items.add(new Control().setMinimum(Slot.SIZE, Slot.SIZE));
        itemScroll.setVisible(true);
        MailDetailBody content = new MailDetailBody(body, new Control().setMinimum(0, 2), rewards);
        Scroll detail = new Scroll().setHorizontal(Scroll.BarMode.DISABLED).setContent(content);
        detail.setMinimum(0, 16).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Control title = new Control().setMinimum(0, 12).setPreferred(0, 12);
        Control author = new Control().setMinimum(0, 24).setPreferred(0, 24);
        Panel panel = new Panel(null).setChild(new Padding(Padding.Insets.all(4))
                .setChild(new Box().setGap(4).add(title).add(author).add(detail)));
        Scroll columns = new Scroll().setVertical(Scroll.BarMode.DISABLED).setContent(new MailColumns(
                new Scroll().setContent(new Control()), panel));
        Window window = new Window(true).setContent(columns);
        for (String text : new String[]{TEXT, TEXT.repeat(20), TEXT}) {
            body.setText(Component.literal(text));
            for (int width : new int[]{127, 128, 129, 480, 854}) {
                window.layoutViewport(width, 240, font);
                check(content.getSize().width() == detail.getViewport().width());
                check(content.getContentExtent().width() == detail.getViewport().width() && detail.getMaxScrollX() == 0);
                if (font.width(body.getText()) > body.getSize().width()) check(body.lines(font, body.getSize().width()).size() > 1);
                check(body.lines(font, body.getSize().width()).stream().allMatch(line -> font.width(line) <= body.getSize().width()));
                int naturalHeight = body.lines(font, body.getSize().width()).size() * body.lineHeight(font);
                check(body.getSize().height() >= naturalHeight && SizeFlags.expands(body.getSizeFlagsVertical()));
                check(rewards.getAbsoluteBounds().bottom() == content.getAbsoluteBounds().y() + content.getContentExtent().height());
                check(rewards.claimButton().getAbsoluteBounds().right() == content.getAbsoluteBounds().right());
                check(itemScroll.getMaxScrollX() > 0 && !itemScroll.getHorizontalVisible());
                if (detail.getMaxScrollY() == 0) check(rewards.getAbsoluteBounds().bottom() == detail.getAbsoluteBounds().bottom());
                // The reward remains reachable after scrolling a long body or a narrow two-column page.
                columns.setScroll(columns.getMaxScrollX(), 0);
                detail.setScroll(0, detail.getMaxScrollY());
                Rect button = rewards.claimButton().getAbsoluteBounds().translated(-columns.getScrollX(), -detail.getScrollY());
                check(button.x() >= 0 && button.right() <= width && button.bottom() <= 240);
                check(io.github.luyingyilong.ore.event.WindowInputs.press(window,button.x() + 2, button.y() + 2, null));
                check(io.github.luyingyilong.ore.event.WindowInputs.release(window,button.x() + 2, button.y() + 2));
            }
        }
        check(claims[0] == 15);
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Mail detail wrapping or claim visibility regression");
    }
}
