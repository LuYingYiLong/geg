package io.github.luyingyilong.geg.chat.client;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.geg.chat.ChatChannel;
import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.ore.signal.Signals;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.util.OreToasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The single bridge between MUI events/native editors and the Ore chat tree.
 */
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
    private final Button newMessages = Signals.configure(new Button().size(Button.Size.SMALL).type(Button.Type.TEXT), connected -> connected.pressed().connect(connected, messages::bottom));
    private final Button chooseChannel = Signals.configure(new Button(Component.translatable("chat.geg.channels")).size(Button.Size.SMALL), connected -> connected.pressed().connect(connected, this::showChannels));
    private final Button direct = Signals.configure(new Button(Component.translatable("chat.geg.new_direct").withStyle(style -> style.withColor(OreTheme.TEXT_WHITE)))
            .type(Button.Type.TEXT), connected -> connected.pressed().connect(connected, this::showContacts));
    private final Box header = new Box().add(title);
    private final Box sidebar = new Box().gap(4).add(direct).add(menuScroll);
    private final Box conversation = new Box().gap(4).add(chooseChannel).add(header).add(loading).add(messages).add(newMessages).add(composer);
    private final Content content = new Content();
    private final Drawer drawer = Signals.configure(new Drawer(Component.empty()).closable(false)
            .placement(Drawer.Placement.LEFT).padding(ChatLayout.DRAWER_PADDING)
            .content(content), connected -> connected.closed().connect(connected, OreScreen::back));
    private final Input target = new Input(410).placeholder(Component.translatable("chat.geg.target_hint"));
    private final InputControl targetControl = new InputControl(target);
    private final Modal channelModal = Signals.configure(new Modal(Component.translatable("chat.geg.channels")).size(220, 220).content(compactScroll)
            .depth(400), connected -> connected.closeRequested().connect(connected, this::closeChannels));
    private final Modal targetModal = Signals.configure(new Modal(Component.translatable("chat.geg.new_direct")).size(260, 240)
            .content(new Box().gap(4).add(targetControl).add(contactError).add(contactsScroll)).depth(400), connected -> connected.closeRequested().connect(connected, this::closeContacts)).addAction(Signals.configure(new Button(Component.translatable("chat.geg.open_direct")).type(Button.Type.PRIMARY), actionButton -> actionButton.pressed().connect(actionButton, () -> ClientChatState.openDirect(target.getText()))));
    private ChatChannel selected;
    private UUID epoch = ChatChannel.NONE;
    private long revision = -1;
    private boolean focusComposer = true;
    private String reportedStatus = "";

    ChatCanvasWidget(String initial) {
        composer.emptySubmitted().connect(window, drawer::close);
        menu.itemActivated().connect(menu, this::selectItem);
        compactMenu.itemActivated().connect(compactMenu, item -> {
            selectItem(item);
            closeChannels();
        });
        contacts.itemActivated().connect(contacts, item -> ClientChatState.openDirect(item.key()));
        title.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        direct.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        header.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        chooseChannel.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        newMessages.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        loading.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        composer.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        menuScroll.sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        content.addChild(sidebar);
        content.addChild(conversation);
        drawer.bodyScroll()
                .horizontal(Scroll.BarMode.DISABLED)
                .vertical(Scroll.BarMode.DISABLED);
        contactsScroll.minimum(0, 40)
                .sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        compactScroll.minimum(0, 40)
                .sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        channelModal.addAction(Signals.configure(new Button(tr("chat.geg.new_direct")).type(Button.Type.SECONDARY), actionButton -> actionButton.pressed().connect(actionButton, () -> {
            closeChannels();
            showContacts();
        })));
        channelModal.visible(false);
        targetModal.visible(false);
        target.setMaxLength(40);
        target.setInputVisible(false);
        window.content(new Control()).overlay(new Overlay().add(drawer).add(channelModal).add(targetModal));
        window.onBack(() -> drawer.close());
        drawer.open();
        if (!initial.isEmpty()) ClientChatState.view(ClientChatState.selected()).draft = initial;
        composer.bind();
        ClientChatState.select(ClientChatState.selected());
        refresh();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    ChatComposer composer() {
        return composer;
    }

    Input targetInput() {
        return target;
    }

    private void selectItem(MenuItem item) {
        for (var value : ClientChatState.channels())
            if (value.channel().key().equals(item.key())) {
                remember();
                ClientChatState.select(value.channel());
                focusComposer = true;
                return;
            }
    }

    private void remember() {
        composer.store();
        if (selected != null) {
            ClientChatState.view(selected).scroll = messages.scroll().scrollY();
            ClientChatState.view(selected).opened = true;
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
        if (organizations.isEmpty())
            organizations.add(MenuItem.empty("no_organizations", tr("chat.geg.no_organizations")));
        if (directs.isEmpty()) directs.add(MenuItem.empty("no_contacts", tr("chat.geg.no_contact")));
        items.add(MenuItem.group("organizations", tr("chat.geg.organizations"), organizations));
        items.add(MenuItem.group("directs", tr("chat.geg.directs"), directs));
        return items;
    }

    private void refresh() {
        var channel = ClientChatState.selected();
        var view = ClientChatState.view(channel);
        boolean switching = !channel.equals(selected) || !epoch.equals(view.epoch);
        if (switching) {
            if (selected != null && !channel.equals(selected)) remember();
            selected = channel;
            epoch = view.epoch;
            composer.bind();
            messages.messages(ClientChatState.messages(channel));
            if (!view.opened || view.messages.isEmpty()) messages.bottom();
            else messages.restore(view.scroll);
            view.opened = true;
            closeContacts();
            closeChannels();
            focusComposer = true;
        } else messages.messages(ClientChatState.messages(channel));
        title.text(ClientChatState.title(channel));
        List<MenuItem> items = channelItems();
        for (Menu value : List.of(menu, compactMenu)) {
            value.items(items).setSelectedNoSignal(channel.key());
            for (var entry : ClientChatState.channels()) {
                Button button = value.button(entry.channel().key());
                if (button != null) {
                    Badge badge = button.badge();
                    if (badge == null) button.badge(badge = new Badge());
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

    private void showChannels() {
        composer.dismissSuggestions();
        channelModal.visible(true);
    }

    private void closeChannels() {
        channelModal.visible(false);
    }

    private void showContacts() {
        composer.dismissSuggestions();
        target.setInputVisible(true);
        targetModal.visible(true);
        ClientChatState.view(ClientChatState.selected()).status = "";
        getContext().focus(target);
    }

    private void closeContacts() {
        targetModal.visible(false);
        target.setInputVisible(false);
    }

    void selectedByServer() {
        closeContacts();
        closeChannels();
        focusComposer = true;
        revision = -1;
    }

    void closing() {
        remember();
        composer.input().setInputVisible(false);
        target.setInputVisible(false);
        window.cancelInteractions();
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (!window.isAlive()) return;
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
        window.prepareTree(context);
        window.layoutViewport(width, height, context.getFont());
        if (focusComposer && drawer.isOpen() && !channelModal.visible() && !targetModal.visible()) {
            window.focusManager().requestFocus(composer.editor(), context);
            focusComposer = false;
        }
        window.draw(context, getArea().x, getArea().y);
        if (drawer.isOpen() && !channelModal.visible() && !targetModal.visible()) {
            if (messages.atBottom()) ClientChatState.markRead();
            if (messages.scroll().scrollY() <= 8) ClientChatState.older();
        }
    }

    @Override
    public @NotNull Interactable.Result onMousePressed(int button) {
        return io.github.luyingyilong.ore.event.WindowInputs.press(window, getContext().getAbsMouseX() - getArea().x, getContext().getAbsMouseY() - getArea().y, getContext(), button)
                ? Interactable.Result.SUCCESS : Interactable.Result.IGNORE;
    }

    @Override
    public boolean onMouseReleased(int button) {
        return io.github.luyingyilong.ore.event.WindowInputs.pointer(window, io.github.luyingyilong.ore.event.PointerEvent.Action.RELEASE, getContext().getAbsMouseX() - getArea().x, getContext().getAbsMouseY() - getArea().y, button);
    }

    @Override
    public void onMouseDrag(int button, double x, double y) {
        io.github.luyingyilong.ore.event.WindowInputs.pointer(window, io.github.luyingyilong.ore.event.PointerEvent.Action.MOVE, getContext().getAbsMouseX() - getArea().x, getContext().getAbsMouseY() - getArea().y, button);
    }

    @Override
    public @NotNull Interactable.Result onCharTyped(char character, int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.text(window, String.valueOf(character), modifiers, getContext())
                ? Interactable.Result.SUCCESS : Interactable.Result.IGNORE;
    }

    @Override
    public boolean onMouseScrolled(double delta) {
        return io.github.luyingyilong.ore.event.WindowInputs.scroll(window, getContext().getAbsMouseX() - getArea().x, getContext().getAbsMouseY() - getArea().y, delta, net.minecraft.client.gui.screens.Screen.hasShiftDown());
    }

    @Override
    public @NotNull Interactable.Result onKeyPressed(int key, int scanCode, int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.key(window, io.github.luyingyilong.ore.event.KeyEvent.Action.PRESS, key, scanCode, modifiers, getContext())
                ? Interactable.Result.SUCCESS : Interactable.Result.IGNORE;
    }

    @Override
    public boolean onKeyReleased(int key, int scanCode, int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.key(window, io.github.luyingyilong.ore.event.KeyEvent.Action.RELEASE, key, scanCode, modifiers, getContext());
    }

    @Override
    public void dispose() {
        window.hostDetached();
        closing();
        super.dispose();
    }

    private final class Content extends Container {
        @Override
        public Size measureSize(Font font, Size available) {
            return minimumSize();
        }

        @Override
        public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            boolean narrow = ChatLayout.collapsed(bounds.width());
            boolean shortViewport = bounds.height() < 180;
            conversation.gap(shortViewport ? 2 : 4);
            chooseChannel.visible(narrow);
            sidebar.visible(!narrow);
            header.visible(!narrow);
            loading.visible(!shortViewport && (ClientChatState.view(ClientChatState.selected()).loading
                    || ClientChatState.messages(ClientChatState.selected()).isEmpty()));
            int left = narrow ? 0 : ChatLayout.MENU_MIN + ChatLayout.GAP;
            if (!narrow) fitChild(sidebar, new Rect(0, 0, ChatLayout.MENU_MIN, bounds.height()), font);
            fitChild(conversation, new Rect(left, 0, Math.max(1, bounds.width() - left), bounds.height()), font);
        }
    }
}
