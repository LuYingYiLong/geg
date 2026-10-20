package io.github.luyingyilong.geg.chat.client;

import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.ore.theme.OreTheme;

public final class ClientChatScreen {
    private static ChatCanvasWidget canvas;

    private ClientChatScreen() {
    }

    public static boolean isOpen() {
        return canvas != null && ModularScreen.isActive("geg", "chat");
    }

    public static void selected() {
        if (isOpen()) canvas.selectedByServer();
        else open("");
    }

    public static void open(String initial) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        // Also protect queued opens and server channel-selection replies while the player is in bed.
        if (minecraft.player != null && minecraft.player.isSleeping())
            return;
        if (isOpen())
            return;
        ChatCanvasWidget next = new ChatCanvasWidget(initial);
        canvas = next;
        ModularPanel<?> panel = new ChatPanel();
        panel.full()
                .disableThemeBackground(true);
        panel.child(next
                .full().
                disableThemeBackground(true));
        panel.child(next
                .composer()
                .input());
        panel.child(next.targetInput());
        panel.onCloseAction(() -> {
            next.closing();
            if (canvas == next)
                canvas = null;
        });
        OreScreen.open(new ModularScreen("geg", panel)

                .pausesGame(false));
    }

    private static final class ChatPanel extends ModularPanel<ChatPanel> {
        ChatPanel() {
            super("chat");
        }

        @Override
        public boolean shouldAnimate() {
            return false;
        }
    }
}
