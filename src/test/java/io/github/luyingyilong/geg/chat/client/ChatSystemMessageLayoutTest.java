package io.github.luyingyilong.geg.chat.client;

import io.github.luyingyilong.geg.chat.ChatChannel;
import io.github.luyingyilong.geg.chat.ChatMessage;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.Avatar;
import io.github.luyingyilong.ore.layout.Rect;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.UUID;

public final class ChatSystemMessageLayoutTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            private final StringSplitter splitter = new StringSplitter((codepoint, style) -> 6.0F);
            @Override public int width(FormattedText text) { return text.getString().codePointCount(0, text.getString().length()) * 6; }
            @Override public int width(FormattedCharSequence text) {
                int[] width = {0};
                text.accept((index, style, codepoint) -> { width[0] += 6; return true; });
                return width[0];
            }
            @Override public StringSplitter getSplitter() { return splitter; }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                return splitter.splitLines(text, width, Style.EMPTY).stream()
                        .map(line -> Language.getInstance().getVisualOrder(line)).toList();
            }
        };
        Component feedback = Component.literal("命令执行结果：" + "支持自动换行的消息 😀 ".repeat(8))
                .withStyle(style -> style.withColor(0xFFAA00));
        ChatMessage system = message(ChatChannel.NONE, "", feedback);
        ChatMessage player = message(UUID.randomUUID(), "Alice", feedback);
        ChatMessageList.MessageView view = new ChatMessageList.MessageView();
        for (int width : new int[]{127, 128, 129, 320}) {
            view.bind(system);
            int height = ChatMessageList.messageHeight(system, width, font);
            view.layout(new Rect(0, 0, width, height), font);
            check(view.getChildren().stream().filter(child -> child.getVisible()).count() == 1, "Only the body is visible");
            check(view.text.getBounds().equals(new Rect(0, 0, width, height)), "Full-width system body without header or avatar inset");
            check(view.text.label.getWrap() == Label.Wrap.WORD && view.text.label.lines(font, width).size() > 1, "Wrap command feedback");
            check(height == view.text.label.measureHeight(font, width), "Virtual row height equals wrapped Label height");
            check(ChatMessageList.messageHeight(player, width, font) > height, "Player rows retain author/portrait spacing");
            view.bind(player);
            view.layout(new Rect(0, 0, width, ChatMessageList.messageHeight(player, width, font)), font);
            check(view.getChildren().stream().filter(child -> child.getVisible()).count() == 3 && view.text.getBounds().x() == 28,
                    "Recycled view restores player layout");
        }
        Component link = Component.literal("Run").withStyle(style -> style.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/help")));
        view.bind(message(ChatChannel.NONE, "", link));
        view.text.label.setFontSize(Label.DEFAULT_FONT_SIZE);
        int height = view.text.label.lineHeight(font);
        view.layout(new Rect(0, 0, 120, height), font);
        Style center = view.text.style(font, 52, 2, new Rect(0, 0, 120, height));
        check(center != null && center.getClickEvent() != null
                && center.getClickEvent().getValue().equals("/help"), "Centered links keep their hit position and style");
        check(view.text.style(font, 1, 2, new Rect(0, 0, 120, height)) == null, "Centered whitespace is not a link");
        view.bind(message(UUID.randomUUID(), "Alice", link));
        view.layout(new Rect(0, 0, 148, 40), font);
        check(view.text.style(font, 29, 16, view.text.getAbsoluteBounds()) != null, "Player link returns to left alignment on reuse");
        mirroredMessages(font, link);
        System.out.println("GEG_CHAT_SYSTEM_MESSAGE_LAYOUT_PASS");
    }

    private static void mirroredMessages(Font font, Component link) {
        UUID self = UUID.randomUUID();
        ChatMessage outgoing = message(self, "Alice", link);
        ChatMessage incoming = message(UUID.randomUUID(), "Alice", link);
        ChatMessage command = message(ChatChannel.NONE, "", link);
        ChatMessageList.MessageView view = new ChatMessageList.MessageView(() -> self);
        view.text.label.setFontSize(Label.DEFAULT_FONT_SIZE);
        for (int width : new int[]{127, 128, 129, 320}) {
            view.bind(outgoing);
            view.layout(new Rect(0, 0, width, 40), font);
            Avatar avatar = view.getChildren().stream().filter(Avatar.class::isInstance).map(Avatar.class::cast).findFirst().orElseThrow();
            check(avatar.getBounds().x() == width - 20 && view.text.getBounds().x() == 0, "Own portrait is right, text is left of it");
            Rect area = view.text.getAbsoluteBounds();
            Style ownLink = view.text.style(font, area.right() - 2, area.y() + 2, area);
            check(ownLink != null && ownLink.getClickEvent() != null, "Right-aligned own link retains its hit region");
            check(view.text.style(font, area.x() + 1, area.y() + 2, area) == null, "Own text's left whitespace is not clickable");
            check(!io.github.luyingyilong.ore.EventFixture.press(view,avatar.getAbsoluteBounds().x() + 1, avatar.getAbsoluteBounds().y() + 1),
                    "Own portrait does not open a direct conversation");

            view.bind(incoming);
            view.layout(new Rect(0, 0, width, 40), font);
            check(avatar.getBounds().x() == 0 && view.text.getBounds().x() == 28, "Same-name other player stays on the left");
            area = view.text.getAbsoluteBounds();
            check(view.text.style(font, area.x() + 1, area.y() + 2, area) != null, "Rebinding restores incoming link alignment");
            check(view.text.style(font, area.right() - 2, area.y() + 2, area) == null, "Incoming right whitespace is not clickable");

            view.bind(command);
            view.layout(new Rect(0, 0, width, 40), font);
            check(!avatar.getVisible() && view.getChildren().stream().filter(child -> child.getVisible()).count() == 1,
                    "Rebinding to command feedback hides all player decoration");
            check(view.text.getBounds().width() == width && view.text.getBounds().x() == 0, "Command feedback remains full width");
            area = view.text.getAbsoluteBounds();
            check(view.text.style(font, width / 2, 2, area) != null, "Command feedback returns to centered alignment");
            check(ChatMessageList.messageHeight(outgoing, width, font) == ChatMessageList.messageHeight(incoming, width, font),
                    "Mirroring keeps virtual row height unchanged");
        }
    }

    private static ChatMessage message(UUID sender, String name, Component text) {
        return new ChatMessage(UUID.randomUUID(), 1, 1, sender, name, text);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
