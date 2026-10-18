package io.github.luyingyilong.geg.chat.client;

import io.github.luyingyilong.geg.chat.*;
import io.github.luyingyilong.geg.client.ui.layout.*;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;

public final class ChatLayoutTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                String value = text.getString(); List<FormattedCharSequence> lines = new ArrayList<>();
                for (int at = 0; at < value.length(); at += Math.max(1, width / 6))
                    lines.add(FormattedCharSequence.forward(value.substring(at, Math.min(value.length(), at + Math.max(1, width / 6))), Style.EMPTY));
                return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
            }
        };
        List<ChatMessage> rows = new ArrayList<>(); UUID user = UUID.randomUUID();
        for (int i = 1; i <= 100; i++) rows.add(new ChatMessage(UUID.randomUUID(), i, i, user, "Alice", Component.literal("消息".repeat(i % 8 + 5))));
        ChatMessageList list = new ChatMessageList(); list.messages(rows);
        for (int width : new int[]{127, 128, 129, 320, 854}) {
            int drawer = ChatLayout.drawerWidth(width); check(drawer <= width && drawer > 0);
            int contentWidth = drawer - ChatLayout.DRAWER_PADDING.left() - ChatLayout.DRAWER_PADDING.right();
            check(ChatLayout.collapsed(contentWidth) == (contentWidth < 172));
            list.layout(new Rect(0, 0, Math.max(1, contentWidth), 150), font);
            check(list.activeViews() < 100 && list.scroll().maxScrollY() > 0);
            list.bottom(); list.layout(new Rect(0, 0, Math.max(1, contentWidth), 150), font); check(list.atBottom());
        }
        list.scroll().setScroll(0, 50);
        ChatMessage more = new ChatMessage(UUID.randomUUID(), 101, 101, user, "Alice", Component.literal("new"));
        List<ChatMessage> next = new ArrayList<>(rows); next.remove(0); next.add(more);
        list.messages(next); list.layout(new Rect(0, 0, 200, 150), font);
        check(!list.atBottom() && list.newMessages() == 1);
        list.bottom(); list.layout(new Rect(0, 0, 200, 150), font); check(list.atBottom() && list.newMessages() == 0);
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Chat layout/virtualization regression"); }
}
