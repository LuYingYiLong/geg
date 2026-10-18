package io.github.luyingyilong.geg.chat.client;

import io.github.luyingyilong.geg.client.ui.debug.DebugBridge;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.geg.chat.*;
import io.github.luyingyilong.geg.client.ui.*;
import io.github.luyingyilong.geg.client.ui.input.Input;
import io.github.luyingyilong.geg.client.ui.input.InputControl;
import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.screen.OreScreen;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import io.github.luyingyilong.geg.client.ui.util.OreToasts;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** The single bridge between MUI events/native editors and the Ore chat tree. */
final class ChatCanvasWidget extends Widget<ChatCanvasWidget> implements Interactable {
    private final Window window = (Window) new Window(true).debugName("Chat Window");
    private final Menu menu = new Menu(), compactMenu = new Menu(), contacts = new Menu();
    private final Scroll menuScroll = new Scroll().horizontal(Scroll.BarMode.DISABLED).content(menu);
    private final Scroll compactScroll = new Scroll().horizontal(Scroll.BarMode.DISABLED).content(compactMenu);
    private final Scroll contactsScroll = new Scroll().horizontal(Scroll.BarMode.DISABLED).content(contacts);
    private final Label title = new Label().overflow(Label.Overflow.ELLIPSIS);
    private final Label contactError = new Label().color(OreTheme.TEXT_ERROR).wrap(Label.Wrap.WORD);
    private final Label loading = new Label(Component.translatable("chat.geg.loading")).color(OreTheme.TEXT_SECONDARY);
    private final ChatMessageList messages = new ChatMessageList();
    private final ChatComposer composer = new ChatComposer().afterSend(() -> focusComposer = true);
    private final Button newMessages = new Button().size(Button.Size.SMALL).type(Button.Type.TEXT).onPressed(messages::bottom);
    private final Button chooseChannel = new Button(Component.translatable("chat.geg.channels")).size(Button.Size.SMALL).onPressed(this::showChannels);
    private final Button direct = new Button(Component.translatable("chat.geg.new_direct").withStyle(style -> style.withColor(OreTheme.TEXT_WHITE)))
            .type(Button.Type.TEXT).onPressed(this::showContacts);
    private final Box header = new Box().add(title);
    private final Box sidebar = new Box().gap(4).add(direct).add(menuScroll);
    private final Box conversation = new Box().gap(4).add(chooseChannel).add(header).add(loading).add(messages).add(newMessages).add(composer);
    private final Content content = new Content();
    private final Drawer drawer = new Drawer(Component.empty()).closable(false)
            .placement(Drawer.Placement.LEFT).padding(ChatLayout.DRAWER_PADDING)
            .content(content).onClose(OreScreen::back);
    private final Input target = new Input(410).placeholder(Component.translatable("chat.geg.target_hint"));
    private final InputControl targetControl = new InputControl(target);
    private final Modal channelModal = new Modal(Component.translatable("chat.geg.channels")).size(220, 220).content(compactScroll)
            .depth(400).onClose(this::closeChannels);
    private final Modal targetModal = new Modal(Component.translatable("chat.geg.new_direct")).size(260, 240)
            .content(new Box().gap(4).add(targetControl).add(contactError).add(contactsScroll)).depth(400)
            .onClose(this::closeContacts).action(Component.translatable("chat.geg.open_direct"), () -> ClientChatState.openDirect(target.getText()), Button.Type.PRIMARY);
    private ChatChannel selected;
    private UUID epoch = ChatChannel.NONE;
    private long revision = -1;
    private boolean focusComposer = true;
    private String reportedStatus = "";

    ChatCanvasWidget(String initial) {
        composer.onEmptySubmit(drawer::close);
        menu.onSelect(this::selectItem); compactMenu.onSelect(item -> { selectItem(item); closeChannels(); });
        contacts.onSelect(item -> ClientChatState.openDirect(item.key()));
        title.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        direct.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER); header.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        chooseChannel.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        newMessages.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN); loading.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        composer.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        menuScroll.sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        content.addChild(sidebar); content.addChild(conversation);
        drawer.bodyScroll().horizontal(Scroll.BarMode.DISABLED).vertical(Scroll.BarMode.DISABLED);
        contactsScroll.minimum(0, 40).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        compactScroll.minimum(0, 40).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        channelModal.action(tr("chat.geg.new_direct"), () -> { closeChannels(); showContacts(); });
        channelModal.visible(false); targetModal.visible(false); target.setMaxLength(40); target.setInputVisible(false);
        window.content(new Control()).overlay(new Overlay().add(drawer).add(channelModal).add(targetModal));
        window.onBack(() -> drawer.close()); drawer.open();
        if (!initial.isEmpty()) ClientChatState.view(ClientChatState.selected()).draft = initial;
        composer.bind(); ClientChatState.select(ClientChatState.selected());
        refresh();
    }
    ChatComposer composer() { return composer; }
    Input targetInput() { return target; }
    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }
    private void selectItem(MenuItem item) {
        for (var value : ClientChatState.channels()) if (value.channel().key().equals(item.key())) {
            remember(); ClientChatState.select(value.channel()); focusComposer = true; return;
        }
    }
    private void remember() {
        composer.store(); if (selected != null) {
            ClientChatState.view(selected).scroll = messages.scroll().scrollY(); ClientChatState.view(selected).opened = true;
        }
    }
    private List<MenuItem> channelItems() {
        List<MenuItem> items = new ArrayList<>(), organizations = new ArrayList<>(), directs = new ArrayList<>();
        for (var channel : ClientChatState.channels()) {
            MenuItem item = MenuItem.item(channel.channel().key(), channel.title());
            switch (channel.channel().type()) {
                case WORLD -> items.add(item);
                case ORGANIZATION -> organizations.add(item);
                case DIRECT -> directs.add(item);
            }
        }
        if (organizations.isEmpty()) organizations.add(MenuItem.empty("no_organizations", tr("chat.geg.no_organizations")));
        if (directs.isEmpty()) directs.add(MenuItem.empty("no_contacts", tr("chat.geg.no_contact")));
        items.add(MenuItem.group("organizations", tr("chat.geg.organizations"), organizations));
        items.add(MenuItem.group("directs", tr("chat.geg.directs"), directs)); return items;
    }
    private void refresh() {
        var channel = ClientChatState.selected(); var view = ClientChatState.view(channel);
        boolean switching = !channel.equals(selected) || !epoch.equals(view.epoch);
        if (switching) {
            if (selected != null && !channel.equals(selected)) remember();
            selected = channel; epoch = view.epoch; composer.bind();
            messages.messages(ClientChatState.messages(channel));
            if (!view.opened || view.messages.isEmpty()) messages.bottom(); else messages.restore(view.scroll);
            view.opened = true; closeContacts(); closeChannels(); focusComposer = true;
        } else messages.messages(ClientChatState.messages(channel));
        title.text(ClientChatState.title(channel));
        List<MenuItem> items = channelItems();
        for (Menu value : List.of(menu, compactMenu)) {
            value.items(items).selectedKey(channel.key());
            for (var entry : ClientChatState.channels()) {
                Button button = value.button(entry.channel().key());
                if (button != null) {
                    Badge badge = button.badge(); if (badge == null) button.badge(badge = new Badge());
                    badge.count(entry.unread()).visible(entry.unread() > 0);
                }
            }
        }
        UUID self = Minecraft.getInstance().player == null ? ChatChannel.NONE : Minecraft.getInstance().player.getUUID();
        contacts.items(ClientChatState.players().stream().filter(p -> !p.id().equals(self))
                .map(p -> MenuItem.item(p.id().toString(), Component.literal(p.name())
                        .append(p.online() ? tr("chat.geg.online_suffix") : tr("chat.geg.offline_suffix")))).toList());
        loading.text(tr(view.loading ? "chat.geg.loading" : "chat.geg.empty"));
        loading.visible(view.loading || view.messages.isEmpty() && ClientChatState.messages(channel).isEmpty());
        revision = ClientChatState.revision();
    }
    private void showChannels() { composer.dismissSuggestions(); channelModal.visible(true); }
    private void closeChannels() { channelModal.visible(false); }
    private void showContacts() {
        composer.dismissSuggestions(); target.setInputVisible(true); targetModal.visible(true);
        ClientChatState.view(ClientChatState.selected()).status = "";
        getContext().focus(target);
    }
    private void closeContacts() { targetModal.visible(false); target.setInputVisible(false); }
    void selectedByServer() { closeContacts(); closeChannels(); focusComposer = true; revision = -1; }
    void closing() { remember(); composer.input().setInputVisible(false); target.setInputVisible(false); window.cancelInteractions(); }
    @Override public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        int width = getArea().width, height = getArea().height;
        drawer.width(ChatLayout.drawerWidth(width));
        channelModal.size(Math.min(220, width), Math.min(240, height));
        targetModal.size(Math.min(260, width), Math.min(260, height));
        composer.update();
        String status = ClientChatState.view(ClientChatState.selected()).status;
        if (!reportedStatus.equals(status)) {
            reportedStatus = status;
            if (!status.isEmpty() && !targetModal.visible())
                OreToasts.show("geg:chat:feedback", new Toast(tr(status)).type(Toast.Type.WARNING));
        }
        if (revision != ClientChatState.revision()) refresh();
        newMessages.visible(messages.newMessages() > 0);
        newMessages.label(tr("chat.geg.new_messages", messages.newMessages()).copy().withStyle(style -> style.withColor(OreTheme.TEXT_WHITE)));
        if (targetModal.visible()) {
            String error = ClientChatState.view(ClientChatState.selected()).status;
            contactError.text(error.isEmpty() ? Component.empty() : tr(error)).visible(!error.isEmpty());
        }
        window.layoutViewport(width, height, context.getFont());
        if (focusComposer && drawer.isOpen() && !channelModal.visible() && !targetModal.visible()) {
            window.focusManager().requestFocus(composer.editor(), context); focusComposer = false;
        }
        window.draw(context, getArea().x, getArea().y);
        if (drawer.isOpen() && !channelModal.visible() && !targetModal.visible()) {
            if (messages.atBottom()) ClientChatState.markRead();
            if (messages.scroll().scrollY() <= 8) ClientChatState.older();
        }
    }
    @Override public @NotNull Result onMousePressed(int button) {
        window.focusManager().pointerActivity();
        if (button != 0) return Result.IGNORE;
        return window.pointerPressed(getContext().getAbsMouseX() - getArea().x, getContext().getAbsMouseY() - getArea().y, getContext()) ? Result.SUCCESS : Result.IGNORE;
    }
    @Override public boolean onMouseReleased(int button) {
        return button == 0 && window.pointerReleased(getContext().getAbsMouseX() - getArea().x, getContext().getAbsMouseY() - getArea().y);
    }
    @Override public void onMouseDrag(int button, double x, double y) {
        if (button == 0) window.pointerDragged(getContext().getAbsMouseX() - getArea().x, getContext().getAbsMouseY() - getArea().y);
    }
    @Override public boolean onMouseScrolled(double delta) {
        return window.dispatchScroll(getContext().getAbsMouseX() - getArea().x, getContext().getAbsMouseY() - getArea().y, delta, false);
    }
    @Override public @NotNull Result onKeyPressed(int key, int scanCode, int modifiers) {
        if (composer.input().isFocused() && composer.handleKey(key, modifiers)) return Result.STOP;
        return window.keyPressed(key, modifiers, getContext()) ? Result.STOP : Result.IGNORE;
    }
    @Override public boolean onKeyReleased(int key, int scanCode, int modifiers) { return window.keyReleased(key, modifiers, getContext()); }
    @Override public void dispose() {
        DebugBridge.backend().close(window); closing(); super.dispose(); }

    private final class Content extends Container {
        @Override public Size measureSize(Font font, Size available) { return minimumSize(); }
        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font); boolean narrow = ChatLayout.collapsed(bounds.width());
            boolean shortViewport = bounds.height() < 180;
            conversation.gap(shortViewport ? 2 : 4);
            chooseChannel.visible(narrow); sidebar.visible(!narrow); header.visible(!narrow);
            loading.visible(!shortViewport && (ClientChatState.view(ClientChatState.selected()).loading
                    || ClientChatState.messages(ClientChatState.selected()).isEmpty()));
            int left = narrow ? 0 : ChatLayout.MENU_MIN + ChatLayout.GAP;
            if (!narrow) fitChild(sidebar, new Rect(0, 0, ChatLayout.MENU_MIN, bounds.height()), font);
            fitChild(conversation, new Rect(left, 0, Math.max(1, bounds.width() - left), bounds.height()), font);
        }
    }
}
