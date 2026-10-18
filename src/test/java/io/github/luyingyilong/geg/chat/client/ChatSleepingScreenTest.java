package io.github.luyingyilong.geg.chat.client;

import io.github.luyingyilong.geg.chat.ChatChannel;
import io.github.luyingyilong.geg.chat.ChatSyncPacket;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.List;

/** Sleep screens must retain vanilla's leave-bed/escape handling even when custom chat is enabled. */
public final class ChatSleepingScreenTest {
    public static void main(String[] args) {
        ClientChatState.clear();
        try {
            ClientChatState.accept(new ChatSyncPacket(ChatSyncPacket.Kind.DIRECTORY, List.of(), List.of(),
                    ChatChannel.WORLD, List.of(), false, ChatChannel.NONE, ""));
            if (!ClientChatState.enabled()) throw new AssertionError("Custom chat must be enabled in this test");
            InBedChatScreen bed = new InBedChatScreen();
            if (!(bed instanceof ChatScreen)) throw new AssertionError("Sleep screen must exercise the ChatScreen subclass path");
            for (int i = 0; i < 100; i++) {
                ScreenEvent.Opening event = new ScreenEvent.Opening(null, bed);
                ChatClientEvents.opening(event);
                if (event.isCanceled() || event.getNewScreen() != bed)
                    throw new AssertionError("Custom chat intercepted the vanilla sleeping screen");
            }
            ClientChatState.clear();
            ScreenEvent.Opening event = new ScreenEvent.Opening(null, new ChatScreen("/"));
            ChatClientEvents.opening(event);
            if (event.isCanceled()) throw new AssertionError("Disabled custom chat must not intercept ordinary chat either");
            System.out.println("GEG_CHAT_SLEEP_SCREEN_PASS");
        } finally { ClientChatState.clear(); }
    }
}
