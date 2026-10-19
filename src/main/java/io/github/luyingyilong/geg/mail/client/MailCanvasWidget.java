package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.debug.DebugBridge;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.Avatar;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Drawer;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.FormItem;
import io.github.luyingyilong.ore.Navbar;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.Menu;
import io.github.luyingyilong.ore.Segmented;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.NumberInput;
import io.github.luyingyilong.ore.input.TextArea;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Padding;
import io.github.luyingyilong.ore.layout.Panel;
import io.github.luyingyilong.ore.layout.Scroll;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.util.OreToasts;
import io.github.luyingyilong.geg.mail.*;
import io.github.luyingyilong.geg.mail.MailActionPacket;
import io.github.luyingyilong.geg.mail.MailMessage;
import io.github.luyingyilong.geg.mail.MailNetwork;
import io.github.luyingyilong.geg.mail.MailPolicy;
import io.github.luyingyilong.geg.mail.MailState;
import io.github.luyingyilong.geg.mail.MailSyncPacket;
import io.github.luyingyilong.geg.money.MoneyCurrency;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Ore owns one geometry/focus tree; MUI Input widgets are synchronized siblings of this canvas. */
final class MailCanvasWidget extends Widget<MailCanvasWidget> implements Interactable {
    private final Window window = (Window) new Window(true).setDebugName("Mail Window");
    private final Overlay overlay = new Overlay();
    private final Navbar navbar = new Navbar(tr("mail.geg.title"));
    private final Button send = Signals.configure(new Button(tr("mail.geg.compose")), connected -> connected.pressed().connect(connected, () -> compose(null)));
    private final Segmented audiences = new Segmented(List.of(tr("mail.geg.player"), tr("mail.geg.organization"), tr("mail.geg.everyone")));
    private final MailInbox inbox = new MailInbox();
    private final Menu mailMenu = new Menu().setEmpty(tr("mail.geg.loading"));
    private final Scroll mailScroll = new Scroll().setContent(mailMenu).setHorizontal(Scroll.BarMode.DISABLED);
    private final Label detailTitle = new Label().setHero(true).setWrap(Label.Wrap.WORD).setOverflow(Label.Overflow.ELLIPSIS);
    private final Avatar detailAvatar = new Avatar();
    private final Box detailAuthor = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(6);
    private final Label detailSender = new Label().setOverflow(Label.Overflow.ELLIPSIS);
    private final Label detailDate = new Label().setColor(OreTheme.TEXT_SECONDARY).setOverflow(Label.Overflow.ELLIPSIS);
    private boolean revealPreview;
    private final Label detailText = new Label().setColor(OreTheme.TEXT_WHITE).setWrap(Label.Wrap.WORD);
    private final Label attachment = new Label().setColor(OreTheme.TEXT_SECONDARY).setWrap(Label.Wrap.WORD);
    private final Divider attachmentDivider = new Divider();
    private final MailAttachmentsView rewards = new MailAttachmentsView(() -> query(MailActionPacket.Action.CLAIM, this.selected));
    private final Box detailActions = new Box().setGap(2);
    private final Scroll detailScroll;
    private final Scroll columnsScroll;
    private final Drawer editor = new Drawer(tr("mail.geg.compose")).setDepth(300);
    private final Input title;
    private final TextArea body;
    private final Input target;
    private final NumberInput coin;
    private final Form form = new Form();
    private final FormItem targetItem;
    private final FormItem scopeItem;
    private final FormItem coinItem;
    private final Segmented scopes = new Segmented(List.of(tr("mail.geg.player"), tr("mail.geg.organization"), tr("mail.geg.everyone")), Button.Size.SMALL);
    private final Label previewText = new Label().setColor(OreTheme.TEXT_WHITE).setWrap(Label.Wrap.WORD);
    private final UUID initialOrganization;
    private UUID organization;
    private UUID selected;
    private String selectedItemKey;
    private UUID token;
    private UUID deleting;
    private MailMessage selectedMessage;
    private boolean selectedClaimed;
    private boolean busy;
    private String previewSignature;
    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }
    private static void feedback(String key) {
        if (key.isEmpty()) return;
        Toast.Type type = switch (key) {
            case "mail.geg.success", "mail.geg.sent" -> Toast.Type.PRIMARY;
            case "mail.geg.partial" -> Toast.Type.NOTICE;
            default -> Toast.Type.WARNING;
        };
        OreToasts.show("geg:mail:feedback", new Toast(tr(key)).setType(type));
    }

    MailCanvasWidget(Input title, TextArea body, Input target, NumberInput coin, UUID initialOrganization) {
        this.title = title; this.body = body; this.target = target; this.coin = coin; this.initialOrganization = initialOrganization;
        send.setMinimum(56, Button.HEIGHT);
        send.setVisible(ClientMailScreen.admin());
        mailMenu.itemActivated().connect(mailMenu, item -> { selectedItemKey = item.key(); showDetail(MailListItem.messageId(item)); });
        navbar.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        audiences.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND | SizeFlags.SIZE_SHRINK_BEGIN)
                .setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        send.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        audiences.selectionChanged().connect(audiences, index -> selectInbox(MailActionPacket.Scope.values()[index]));
        Box audienceRow = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4).add(audiences).add(send);
        audienceRow.setMinimum(0, Button.HEIGHT).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        for (Control label : List.of(detailTitle, detailSender, detailDate, detailText, attachment))
            label.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Box authorInfo = new Box().setGap(2).add(detailSender).add(detailDate);
        authorInfo.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        detailAuthor.add(detailAvatar).add(authorInfo);
        detailAuthor.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        detailScroll = new Scroll().setHorizontal(Scroll.BarMode.DISABLED).setContent(new MailDetailBody(
                detailText, attachmentDivider, attachment, rewards, detailActions));
        detailScroll.setMinimum(0, 16).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        detailActions.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Panel detailPanel = new Panel(null).setChild(new Padding(Padding.Insets.all(4))
                .setChild(new Box().setGap(4).add(detailTitle).add(detailAuthor).add(detailScroll)));
        Panel menuPanel = new Panel(OreTheme.PANEL)
                .setChild(new Padding(new Padding.Insets(8, 1, 8, 1)).setChild(mailScroll));
        columnsScroll = new Scroll().setVertical(Scroll.BarMode.DISABLED).setContent(new MailColumns(menuPanel, detailPanel));
        columnsScroll.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL); columnsScroll.setMinimum(0, 41);
        Padding padding = new Padding(Padding.Insets.all(4));
        padding.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        window.setContent(new Box().setGap(0).add(navbar).add(
                padding.setChild(new Box().setGap(4).add(audienceRow).add(columnsScroll))
        )).setOverlay(overlay);
        clearDetail();
        renderInbox();
        scopeItem = new FormItem("scope", tr("mail.geg.scope"), scopes, () -> scope().name());
        targetItem = new FormItem("target", tr("mail.geg.target"), target);
        coinItem = new FormItem("coin", tr("mail.geg.coin_per_player"), coin);
        form.add(scopeItem).add(targetItem).add(new FormItem("title", tr("mail.geg.subject"), title))
                .add(new FormItem("body", tr("mail.geg.body"), body)).add(coinItem);
        form.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        previewText.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        editor.setContent(new Box().setGap(6).add(form).add(previewText));
        editor.closed().connect(editor, () -> { form.setVisible(false); busy = false; token = null; });
        scopes.selectionChanged().connect(scopes, index -> { targetItem.setActive(scope() != MailActionPacket.Scope.ALL); editAgain(); });
        overlay.add(editor);
    }
    private static String time(long millis) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }
    void initialize() { refreshInbox(); if (initialOrganization != null) compose(initialOrganization); }
    private void refreshInbox() {
        MailNetwork.send(inbox.refresh());
        if (inbox.rows().isEmpty()) renderInbox();
    }
    private void selectInbox(MailActionPacket.Scope scope) {
        mailScroll.scrollToTop(); clearDetail();
        MailNetwork.send(inbox.select(MailState.Filter.ALL, scope));
        renderInbox();
    }
    private void renderInbox() {
        mailMenu.setItems(MailListItem.groups(inbox.rows(), inbox.loading() || inbox.hasMore()))
                .setEmpty(tr(inbox.loading() ? "mail.geg.loading" : "mail.geg.empty"));
        if (selected != null) {
            if (selectedItemKey == null || mailMenu.getButton(selectedItemKey) == null)
                selectedItemKey = MailListItem.key(MailState.Filter.ALL, selected);
            mailMenu.setSelectedNoSignal(selectedItemKey);
        }
    }
    private void query(MailActionPacket.Action action, UUID id) {
        MailNetwork.send(MailActionPacket.query(action, id, inbox.filter(), 0, inbox.scope()));
    }
    private void showDetail(UUID id) {
        selected = id; selectedMessage = null; detailText.setText(tr("mail.geg.loading"));
        detailTitle.setText(Component.empty()); detailSender.setText(Component.empty()); detailDate.setText(Component.empty()); attachment.setText(Component.empty());
        detailAuthor.setVisible(false); detailAvatar.setInitials("");
        rewards.attachments(List.of(), false, false); attachmentDivider.setVisible(false);
        clearDetailActions(); detailScroll.scrollToTop(); query(MailActionPacket.Action.DETAIL, id);
    }
    private void clearDetailActions() {
        for (Control action : detailActions.getChildren()) { if (action.getParent() != null) action.getParent().removeChild(action); action.queueFree(); }
        detailActions.setMinimum(0, 0); detailActions.setVisible(false);
    }
    private void clearDetail() {
        selected = null; selectedItemKey = null; selectedMessage = null;
        detailTitle.setText(tr("mail.geg.detail")); detailSender.setText(Component.empty()); detailDate.setText(Component.empty());
        detailText.setText(tr("mail.geg.select_message")); attachment.setText(Component.empty());
        detailAuthor.setVisible(false); detailAvatar.setInitials("");
        rewards.attachments(List.of(), false, false); attachmentDivider.setVisible(false);
        clearDetailActions();
    }
    private void updateDetail() {
        if (selectedMessage == null) return;
        MailMessage mail = selectedMessage;
        detailTitle.setText(mail.titleText());
        Component source = mail.kind() == MailMessage.Kind.SYSTEM ? (mail.sender().isEmpty()
                ? tr("mail.geg.market.source") : tr("mail.geg.system_source", mail.sender()))
                : tr(mail.kind() == MailMessage.Kind.ADMIN ? "mail.geg.admin_source" : "mail.geg.organization_source", mail.sender());
        if (!mail.authorName().isBlank()) {
            detailSender.setText(Component.literal(mail.authorName()));
            detailAvatar.setPlayer(mail.authorId(), mail.authorName());
        } else {
            detailSender.setText(source);
            detailAvatar.setInitials(mail.sender());
        }
        detailAvatar.setVisible(mail.kind() != MailMessage.Kind.SYSTEM);
        detailAuthor.setVisible(true);
        detailDate.setText(Component.literal(time(mail.sentAt()))); detailText.setText(mail.bodyText());
        attachment.setText(mail.coin() == 0 ? Component.empty() : tr(selectedClaimed ? "mail.geg.claimed_amount" : "mail.geg.attachment_amount",
                MoneyCurrency.COIN.format(mail.coin())));
        clearDetailActions();
        boolean claimable = mail.hasAttachments() && !selectedClaimed;
        rewards.attachments(mail.items(), mail.hasAttachments(), selectedClaimed);
        attachmentDivider.setVisible(mail.hasAttachments());
        if (claimable) return;
        Button action = Signals.configure(new Button(tr("mail.geg.delete")).setType(Button.Type.DESTRUCTIVE), connected -> connected.pressed().connect(connected, () -> { deleting = selected; query(MailActionPacket.Action.DELETE, selected); }));
        action.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        detailActions.add(action); detailActions.setMinimum(0, action.getMinimumHeight()); detailActions.setVisible(true);
    }
    private MailActionPacket.Scope scope() { return organization != null ? MailActionPacket.Scope.ORGANIZATION : MailActionPacket.Scope.values()[scopes.getSelected()]; }
    private void compose(UUID organization) {
        this.organization = organization; busy = false; token = null; previewSignature = null;
        editor.setTitle(tr(organization == null ? "mail.geg.compose" : "mail.geg.announce"));
        scopeItem.setActive(organization == null); targetItem.setActive(organization == null && scope() != MailActionPacket.Scope.ALL);
        coinItem.setActive(organization == null); editor.open(); form.setVisible(true); editAgain();
    }
    private String signature() {
        return scope() + "\0" + organization + "\0" + title.getText() + "\0" + body.getText() + "\0" + target.getText() + "\0" + coin.getText();
    }
    private void editAgain() {
        busy = false; token = null; previewSignature = null; previewText.setText(Component.empty());
        editor.clearActions().addAction(Signals.configure(new Button(tr("mail.geg.preview")).setType(Button.Type.PRIMARY), actionButton -> actionButton.pressed().connect(actionButton, this::preview)));
    }
    private void preview() {
        if (busy) return;
        if (!form.validate()) {
            Component error = coin.getError();
            OreToasts.show("geg:mail:feedback", new Toast(error == null ? tr("mail.geg.invalid_coin") : error).setType(Toast.Type.WARNING));
            return;
        }
        if (!MailPolicy.validText(title.getText(), 64, false) || !MailPolicy.validText(body.getText(), 2048, true)) {
            feedback("mail.geg.invalid_text"); return;
        }
        long amount;
        try { amount = organization == null ? new BigDecimal(coin.getText()).movePointRight(2).longValueExact() : 0; }
        catch (ArithmeticException | NumberFormatException exception) { feedback("mail.geg.invalid_coin"); return; }
        if (amount < 0) { feedback("mail.geg.invalid_coin"); return; }
        token = UUID.randomUUID(); previewSignature = signature(); busy = true; previewText.setText(Component.empty());
        MailNetwork.send(new MailActionPacket(MailActionPacket.Action.PREVIEW, token, inbox.filter(), 0, scope(), organization != null,
                organization == null ? MailActionPacket.EMPTY : organization, target.getText(), title.getText(), body.getText(), amount));
    }
    private void confirm() {
        if (busy || token == null) return;
        if (!Objects.equals(previewSignature, signature())) { editAgain(); feedback("mail.geg.preview_again"); return; }
        busy = true; query(MailActionPacket.Action.CONFIRM, token);
    }
    void accept(MailSyncPacket packet) {
        send.setVisible(packet.admin());
        if (packet.kind() == MailSyncPacket.Kind.LIST) {
            MailInbox.Update update = inbox.accept(packet);
            if (update == MailInbox.Update.RESTART) refreshInbox();
            else if (update == MailInbox.Update.UPDATED) renderInbox();
        } else if (packet.kind().isStatus() && packet.revision() != inbox.revision()) refreshInbox();
        else if (packet.kind() == MailSyncPacket.Kind.DETAIL && packet.detail() != null && Objects.equals(selected, packet.detail().id())) {
            selectedMessage = packet.detail(); selectedClaimed = packet.claimed(); updateDetail(); refreshInbox();
        } else if (packet.kind() == MailSyncPacket.Kind.PREVIEW && editor.isOpen() && Objects.equals(token, packet.token())) {
            busy = false; revealPreview = true;
            if (!Objects.equals(previewSignature, signature())) { editAgain(); feedback("mail.geg.preview_again"); return; }
            previewText.setText(tr("mail.geg.preview_summary", packet.recipients(), MoneyCurrency.COIN.format(packet.coin()), packet.total()));
            feedback(packet.result());
            editor.clearActions().addAction(Signals.configure(new Button(tr("mail.geg.confirm_send")).setType(Button.Type.PRIMARY), actionButton -> actionButton.pressed().connect(actionButton, this::confirm)))
                    .addAction(Signals.configure(new Button(tr("mail.geg.edit")).setType(Button.Type.SECONDARY), actionButton -> actionButton.pressed().connect(actionButton, this::editAgain)));
        } else if (packet.kind() == MailSyncPacket.Kind.RESULT) {
            feedback(packet.result());
            if (editor.isOpen() && Objects.equals(token, packet.token())) {
                busy = false;
                if (packet.result().equals("mail.geg.sent")) { editor.close(); form.setVisible(false); refreshInbox(); }
            } else {
                inbox.failed(packet.token());
                if (Objects.equals(selected, packet.token())) {
                    if (packet.result().equals("mail.geg.not_found")) clearDetail();
                    if (packet.result().equals("mail.geg.success") && Objects.equals(deleting, selected)) {
                        deleting = null; clearDetail();
                    } else if (packet.result().equals("mail.geg.success") || packet.result().equals("mail.geg.partial"))
                        query(MailActionPacket.Action.DETAIL, selected);
                }
                if (packet.result().equals("mail.geg.success") || packet.result().equals("mail.geg.partial")) refreshInbox();
            }
        }
    }
    void close() { form.setVisible(false); window.cancelInteractions(); }
    @Override public void dispose() {
        window.hostDetached(); close(); super.dispose(); }
    @Override public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (!window.isAlive()) return;
        int width = getArea().width, height = getArea().height;
        int drawerWidth = width < 320 ? width : width / 2;
        editor.setWidth(drawerWidth);
        for (Label label : List.of(detailTitle, detailSender, detailDate, attachment, previewText))
            label.setVisible(!label.getText().getString().isEmpty());
        // Clear the previous minimum so a two-row navbar can return to one row on resize.
        navbar.setMinimum(0, 0);
        navbar.setMinimum(0, navbar.measureHeight(context.getFont(), width));
        window.prepareTree(context);
        window.layoutViewport(width, height, context.getFont());
        if (inbox.catchingUp() || mailScroll.getScrollY() + mailScroll.getViewport().height() * 2 >= mailMenu.getContentExtent().height()) {
            MailActionPacket next = inbox.nextBatch();
            if (next != null) MailNetwork.send(next);
        }
        if (!editor.getVisible()) form.setVisible(false);
        if (revealPreview && editor.isOpen()) {
            editor.getBodyScroll().scrollBy(0, editor.getBodyScroll().getMaxScrollY()); revealPreview = false;
        }
        window.draw(context, getArea().x, getArea().y);
    }
    @Override public @NotNull Interactable.Result onMousePressed(int button) {
        return io.github.luyingyilong.ore.event.WindowInputs.press(window,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,getContext(),button)
                ?Interactable.Result.SUCCESS:Interactable.Result.IGNORE;
    }
    @Override public boolean onMouseReleased(int button) {
        return io.github.luyingyilong.ore.event.WindowInputs.pointer(window,io.github.luyingyilong.ore.event.PointerEvent.Action.RELEASE,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,button);
    }
    @Override public void onMouseDrag(int button,double x,double y) {
        io.github.luyingyilong.ore.event.WindowInputs.pointer(window,io.github.luyingyilong.ore.event.PointerEvent.Action.MOVE,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,button);
    }
    @Override public @NotNull Interactable.Result onCharTyped(char character,int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.text(window,String.valueOf(character),modifiers,getContext())
                ?Interactable.Result.SUCCESS:Interactable.Result.IGNORE;
    }
    @Override public boolean onMouseScrolled(double delta) {
        return io.github.luyingyilong.ore.event.WindowInputs.scroll(window,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,delta,net.minecraft.client.gui.screens.Screen.hasShiftDown());
    }
    @Override public @NotNull Interactable.Result onKeyPressed(int keyCode,int scanCode,int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.key(window,io.github.luyingyilong.ore.event.KeyEvent.Action.PRESS,keyCode,scanCode,modifiers,getContext())
                ?Interactable.Result.SUCCESS:Interactable.Result.IGNORE;
    }
    @Override public boolean onKeyReleased(int keyCode,int scanCode,int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.key(window,io.github.luyingyilong.ore.event.KeyEvent.Action.RELEASE,keyCode,scanCode,modifiers,getContext());
    }
    private int mx() { return getContext().getAbsMouseX() - getArea().x; }
    private int my() { return getContext().getAbsMouseY() - getArea().y; }
}
