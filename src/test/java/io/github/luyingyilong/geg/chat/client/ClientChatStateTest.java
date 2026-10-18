package io.github.luyingyilong.geg.chat.client;

import io.github.luyingyilong.geg.chat.*;
import java.util.*;
import net.minecraft.network.chat.Component;

/** Exercise actual client cache with an injected network sink, without opening a native UI. */
public final class ClientChatStateTest {
    public static void main(String[] args) {
        List<ChatRequest> outgoing = new ArrayList<>(); ClientChatState.transport(outgoing::add); ClientChatState.clear();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), epoch = UUID.randomUUID();
        ChatChannel org = ChatChannel.organization(UUID.randomUUID());
        var summary = new ChatSyncPacket.Conversation(org, Component.literal("Builders"), epoch, 0, 0);
        accept(ChatSyncPacket.Kind.DIRECTORY, org, List.of(summary), List.of(), ChatChannel.NONE, "");
        ClientChatState.select(org); ChatRequest query = outgoing.get(outgoing.size() - 1);
        ChatMessage first = new ChatMessage(UUID.randomUUID(), 1, 1, a, "Alice", Component.literal("old"));
        accept(ChatSyncPacket.Kind.HISTORY, org, List.of(summary), List.of(first), query.request(), "");
        check(ClientChatState.messages(org).size() == 1 && ClientChatState.hud().isEmpty());
        ChatMessage second = new ChatMessage(UUID.randomUUID(), 2, 2, b, "Bob", Component.literal("live"));
        accept(ChatSyncPacket.Kind.MESSAGE, org, List.of(summary), List.of(second), b, "");
        accept(ChatSyncPacket.Kind.MESSAGE, org, List.of(summary), List.of(second), b, "");
        check(ClientChatState.messages(org).size() == 2 && ClientChatState.hud().size() == 1);
        ClientChatState.view(org).draft = "draft"; ClientChatState.send("draft");
        ChatRequest send = outgoing.get(outgoing.size() - 1); check(ClientChatState.pending(org));
        accept(ChatSyncPacket.Kind.RESULT, org, List.of(), List.of(), send.request(), "chat.geg.rate_limited");
        check(!ClientChatState.pending(org) && ClientChatState.view(org).draft.equals("draft"));
        ClientChatState.send("draft"); send = outgoing.get(outgoing.size() - 1);
        ClientChatState.view(org).draft = "new typing";
        accept(ChatSyncPacket.Kind.RESULT, org, List.of(), List.of(), send.request(), "chat.geg.sent");
        check(ClientChatState.view(org).draft.equals("new typing"));
        ClientChatState.send("new typing"); send = outgoing.get(outgoing.size() - 1);
        accept(ChatSyncPacket.Kind.RESULT, org, List.of(), List.of(), send.request(), "chat.geg.sent");
        check(ClientChatState.view(org).draft.isEmpty());
        ClientChatState.markRead(); int size = outgoing.size(); ClientChatState.markRead(); check(outgoing.size() == size);
        ClientChatState.view(org).draft = "keep on removal";
        accept(ChatSyncPacket.Kind.DIRECTORY, org, List.of(), List.of(), ChatChannel.NONE, "");
        check(!ClientChatState.view(org).available && ClientChatState.messages(org).isEmpty());
        check(ClientChatState.selected().equals(org) && ClientChatState.view(org).draft.equals("keep on removal"));
        var rejoined = new ChatSyncPacket.Conversation(org, Component.literal("Builders"), UUID.randomUUID(), 0, 0);
        accept(ChatSyncPacket.Kind.DIRECTORY, org, List.of(rejoined), List.of(), ChatChannel.NONE, "");
        check(ClientChatState.view(org).available && ClientChatState.messages(org).isEmpty());
        ClientChatState.clear(); check(!ClientChatState.enabled() && ClientChatState.hud().isEmpty());
    }
    private static void accept(ChatSyncPacket.Kind kind, ChatChannel channel, List<ChatSyncPacket.Conversation> conversations,
                               List<ChatMessage> messages, UUID request, String result) {
        ClientChatState.accept(new ChatSyncPacket(kind, conversations, List.of(), channel, messages, false, request, result));
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Chat cache, draft, or notification regression"); }
}
