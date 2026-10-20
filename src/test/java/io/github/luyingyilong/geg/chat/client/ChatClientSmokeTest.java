package io.github.luyingyilong.geg.chat.client;

import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.chat.*;
import io.github.luyingyilong.ore.Drawer;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.geg.organization.*;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Opt-in live client check, compiled only for tests and never included in the published mod. */
@Mod.EventBusSubscriber(modid = GegMod.MODID, value = Dist.CLIENT)
public final class ChatClientSmokeTest {
    private static int ticks, phase, waiting;
    private static volatile boolean offlineVerified;
    private static final UUID OFFLINE = UUID.fromString("b2b09ef7-d7e9-4b1a-935b-b09f37178f3f");
    private static final String MULTILINE = "Hello 中文😀\n第二行";
    private static final String OFFLINE_MESSAGE = "离线私信 " + UUID.randomUUID();
    private static final String NATIVE_MESSAGE = "Native Forge bridge smoke " + UUID.randomUUID();
    private static volatile int offlineUnreadBefore;
    private static com.mojang.blaze3d.platform.InputConstants.Key previousChatKey;
    private ChatClientSmokeTest() { }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("geg.chat.smoke") || event.phase != TickEvent.Phase.END || phase == 99) return;
        Minecraft mc = Minecraft.getInstance(); ticks++; waiting++;
        try {
            if (ticks > 1800) throw new AssertionError("Live chat smoke test timed out at phase " + phase);
            if (mc.player == null || mc.level == null) return;
            if (phase == 0) {
                mc.options.pauseOnLostFocus = false;
                UUID self = mc.player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer(); var data = ChatSavedData.get(server);
                    data.remember(OFFLINE, "OfflineBob");
                    offlineUnreadBefore = data.unread(OFFLINE, ChatChannel.direct(self, OFFLINE), 1);
                    var org = OrganizationSavedData.get(server); org.create(self, "Chat Smoke " + System.currentTimeMillis(), "", Organization.JoinPolicy.UNRESTRICTED);
                    for (int i = 0; i < 100; i++) data.append(OFFLINE, UUID.randomUUID(), ChatChannel.WORLD,
                            "OfflineBob", Component.literal("历史消息 " + i + " · 中文换行与虚拟列表测试"), System.currentTimeMillis() + i);
                    ChatService.syncDirectory(server.getPlayerList().getPlayer(self));
                });
                next();
            } else if (phase == 1 && ClientChatState.channels().size() >= 2) {
                mc.setScreen(new ChatScreen("/")); next();
            } else if (phase == 2 && ClientChatScreen.isOpen() && waiting > 30) {
                ChatCanvasWidget canvas = canvas();
                check(canvas.composer().input().isFocused(), "Composer did not receive native focus");
                check(canvas.composer().input().getText().equals("/"), "Command shortcut lost its initial slash");
                check(canvas.composer().input().getCursorOffset() == 1, "Command cursor must start after the slash");
                check(canvas.composer().input().getPreferredInputHeight() == 20, "Composer should occupy one row");
                canvas.composer().input().setText(MULTILINE);
                canvas.composer().input().onKeyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); next();
            } else if (phase == 3 && ClientChatState.messages(ChatChannel.WORLD).stream().anyMatch(m -> m.text().getString().equals(MULTILINE))) {
                check(ClientChatScreen.isOpen(), "Sending closed Drawer");
                if (!ClientChatState.view(ChatChannel.WORLD).draft.isEmpty()) return;
                ClientChatState.openDirect(OFFLINE.toString()); next();
            } else if (phase == 4 && ClientChatState.selected().type() == ChatChannel.Type.DIRECT && waiting > 20) {
                canvas().composer().input().setText(OFFLINE_MESSAGE);
                canvas().composer().input().onKeyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); next();
            } else if (phase == 5 && ClientChatState.messages(ClientChatState.selected()).stream().anyMatch(m -> m.text().getString().equals(OFFLINE_MESSAGE))) {
                ChatChannel channel = ClientChatState.selected();
                mc.getSingleplayerServer().execute(() -> {
                    offlineVerified = ChatSavedData.get(mc.getSingleplayerServer()).unread(OFFLINE, channel, 1) == offlineUnreadBefore + 1;
                }); next();
            } else if (phase == 6 && offlineVerified && waiting > 15) {
                ChatCanvasWidget canvas = canvas(); Window window = field(canvas, "window", Window.class);
                Drawer drawer = field(canvas, "drawer", Drawer.class);
                check(drawer.getPadding().equals(ChatLayout.DRAWER_PADDING), "Chat Drawer lost its four local padding overrides");
                for (var key : java.util.List.of(Drawer.PADDING_TOP, Drawer.PADDING_RIGHT, Drawer.PADDING_BOTTOM, Drawer.PADDING_LEFT))
                    check(drawer.getThemeResolution(key).sources().get(0).kind().equals("local override"), "Chat padding is not resolved from a local theme override");
                check(drawer.getPlacement() == Drawer.Placement.LEFT, "Chat Drawer must open from the left");
                check(!drawer.getClosable() && !drawer.getChildren().get(0).getChildren().get(0).getChildren().get(0).getVisible(),
                        "Chat Drawer header should be hidden");
                var inputRenderer = field(canvas.composer().input(),
                        brachy.modularui.widgets.textfield.BaseTextFieldWidget.class, "renderer",
                        brachy.modularui.widgets.textfield.TextFieldRenderer.class);
                check(inputRenderer.getFontHeight() == new io.github.luyingyilong.ore.Label().lineHeight(mc.font),
                        "Editor and Label line heights differ");
                Float caretTop = null;
                for (String text : new String[]{"", "abc", "中文", "abc中文gjpq", "ÉÅ"}) {
                    canvas.composer().input().setText(text);
                    var point = inputRenderer.getPosOf(inputRenderer.measureStringLines(java.util.List.of(text)),
                            new brachy.modularui.utils.Point(0, 0));
                    if (caretTop == null) caretTop = point.y;
                    check(point.y == caretTop, "Editing mixed text moved the caret vertically");
                }
                for (int width : new int[]{127, 128, 129, 640}) for (int height : new int[]{127, 240}) {
                    drawer.setWidth(ChatLayout.drawerWidth(width)); window.layoutViewport(width, height, mc.font);
                    var input = canvas.composer().editor().getAbsoluteBounds(); var panel = drawer.getPanelBounds();
                    check(panel.x() == 0, "Left Drawer is not anchored to the left edge");
                    check(input.x() >= panel.x() && input.right() <= panel.right(), "Composer horizontally overflowed " + width);
                    check(input.bottom() <= panel.bottom(), "Composer vertically overflowed " + width + "x" + height);
                    check(input.height() == 20 && canvas.composer().getBounds().height() == 20,
                            "Composer must stay one row without an action/footer row");
                }
                drawer.setWidth(ChatLayout.drawerWidth(mc.getWindow().getGuiScaledWidth()));
                window.layoutViewport(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), mc.font);
                canvas.composer().input().setText("keep me"); canvas.composer().store();
                io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, canvas.getContext());
                check(drawer.isOpen(), "First Escape must blur the editor");
                io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, canvas.getContext());
                check(!drawer.isOpen(), "Second Escape must close Drawer"); next();
            } else if (phase == 7 && !ClientChatScreen.isOpen() && waiting > 20) {
                mc.setScreen(new ChatScreen("")); next();
            } else if (phase == 8 && ClientChatScreen.isOpen() && waiting > 25) {
                check(canvas().composer().input().getText().equals("keep me"), "Closing lost the conversation draft");
                Screenshot.grab(mc.gameDirectory, "chat-smoke.png", mc.getMainRenderTarget(), message -> { });
                canvas().composer().input().setText(""); canvas().composer().store();
                canvas().composer().input().onKeyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
                mc.getConnection().sendChat(NATIVE_MESSAGE); next();
            } else if (phase == 9 && ClientChatState.messages(ChatChannel.WORLD).stream()
                    .anyMatch(m -> m.text().getString().equals(NATIVE_MESSAGE))) {
                check(ClientChatState.messages(ChatChannel.WORLD).stream().filter(m -> m.text().getString().equals(NATIVE_MESSAGE)).count() == 1,
                        "Native chat bridge duplicated a message");
                check(ClientChatState.hud().stream().filter(m -> m.text().getString().contains(NATIVE_MESSAGE)).count() == 1,
                        "Native chat bridge duplicated the HUD entry");
                Screenshot.grab(mc.gameDirectory, "chat-empty-composer.png", mc.getMainRenderTarget(), message -> { });
                canvas().composer().input().setText("/chat w"); canvas().composer().input().setCursorOffset(7); next();
            } else if (phase == 10 && canvas().composer().suggestionsVisible()) {
                check(canvas().composer().suggestions().stream().anyMatch(s -> s.getText().equals("world")), "Missing server command completion");
                canvas().composer().input().onKeyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                check(canvas().composer().input().getText().equals("/chat world"), "Tab did not apply completion");
                canvas().composer().input().onKeyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); next();
            } else if (phase == 11 && ClientChatState.selected().equals(ChatChannel.WORLD) && waiting > 15) {
                check(ClientChatScreen.isOpen(), "Command closed the chat Drawer");
                check(mc.screen.handleComponentClicked(net.minecraft.network.chat.Style.EMPTY.withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                                "https://example.invalid/ore-chat-smoke"))), "Rich text link was not handled");
                check(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmLinkScreen, "Expected the standard link confirmation");
                next();
            } else if (phase == 12 && waiting > 5) {
                mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); next();
            } else if (phase == 13 && waiting > 10) {
                check(ClientChatScreen.isOpen(), "Returning from a link confirmation lost the chat session");
                check(mc.screen instanceof io.github.luyingyilong.ore.screen.OreScreen,
                        "Link confirmation did not return to the actual Ore screen");
                ChatCanvasWidget canvas = canvas(); Drawer drawer = field(canvas, "drawer", Drawer.class);
                Window window = field(canvas, "window", Window.class);
                window.getFocusManager().requestFocus(canvas.composer().editor(), canvas.getContext());
                canvas.composer().input().setText("");
                canvas.composer().input().onKeyPressed(GLFW.GLFW_KEY_ENTER, 0, GLFW.GLFW_MOD_SHIFT);
                check(drawer.isOpen(), "Shift+Enter must insert a newline, not close chat");
                check(canvas.composer().input().getText().equals("\n") && canvas.composer().input().getPreferredInputHeight() == 20,
                        "Shift+Enter should preserve multiline editing inside the one-row viewport");
                canvas.composer().input().setText(" \n ");
                canvas.composer().input().onKeyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                check(!drawer.isOpen(), "Blank Enter should close the chat Drawer"); next();
            } else if (phase == 14 && !ClientChatScreen.isOpen() && waiting > 20) {
                System.out.println("GEG_CHAT_ENTER_REOPEN screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
                check(mc.screen == null, "Closing chat returned to a stale vanilla dialog");
                previousChatKey = mc.options.keyChat.getKey();
                var enter = com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_ENTER);
                mc.options.keyChat.setKey(enter); net.minecraft.client.KeyMapping.resetMapping();
                net.minecraft.client.KeyMapping.click(enter); next();
            } else if (phase == 15 && ClientChatScreen.isOpen() && waiting > 25) {
                check(canvas().composer().input().isFocused(), "Enter shortcut did not focus composer");
                canvas().composer().input().onKeyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                check(!field(canvas(), "drawer", Drawer.class).isOpen(), "Second Enter did not close chat");
                mc.options.keyChat.setKey(previousChatKey); net.minecraft.client.KeyMapping.resetMapping(); previousChatKey = null;
                System.out.println("GEG_CHAT_SMOKE_PASS"); next();
            } else if (phase == 16 && waiting > 25) { phase = 99; mc.stop(); }
        } catch (Throwable failure) {
            if (previousChatKey != null) { mc.options.keyChat.setKey(previousChatKey); net.minecraft.client.KeyMapping.resetMapping(); previousChatKey = null; }
            System.err.println("GEG_CHAT_SMOKE_FAIL phase=" + phase); phase = 99; failure.printStackTrace(); mc.stop();
        }
    }
    private static void next() { phase++; waiting = 0; System.out.println("GEG_CHAT_SMOKE_PHASE " + phase); }
    private static ChatCanvasWidget canvas() throws ReflectiveOperationException { return field(null, ClientChatScreen.class, "canvas", ChatCanvasWidget.class); }
    private static <T> T field(Object owner, String name, Class<T> type) throws ReflectiveOperationException { return field(owner, owner.getClass(), name, type); }
    private static <T> T field(Object owner, Class<?> declaring, String name, Class<T> type) throws ReflectiveOperationException {
        var field = declaring.getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(owner));
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
