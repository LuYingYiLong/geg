package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.geg.client.ui.debug.DebugBridge;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.geg.client.ui.*;
import io.github.luyingyilong.geg.client.ui.Avatar;
import io.github.luyingyilong.geg.client.ui.Button;
import io.github.luyingyilong.geg.client.ui.Drawer;
import io.github.luyingyilong.geg.client.ui.Form;
import io.github.luyingyilong.geg.client.ui.FormItem;
import io.github.luyingyilong.geg.client.ui.Navbar;
import io.github.luyingyilong.geg.client.ui.Label;
import io.github.luyingyilong.geg.client.ui.Menu;
import io.github.luyingyilong.geg.client.ui.Segmented;
import io.github.luyingyilong.geg.client.ui.input.Input;
import io.github.luyingyilong.geg.client.ui.input.NumberInput;
import io.github.luyingyilong.geg.client.ui.input.TextArea;
import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Overlay;
import io.github.luyingyilong.geg.client.ui.layout.Padding;
import io.github.luyingyilong.geg.client.ui.layout.Panel;
import io.github.luyingyilong.geg.client.ui.layout.Scroll;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import io.github.luyingyilong.geg.client.ui.util.OreToasts;
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
    private final Window window = (Window) new Window(true).debugName("Mail Window");
    private final Overlay overlay = new Overlay();
    private final Navbar navbar = new Navbar(tr("mail.geg.title"));
    private final Button send = new Button(tr("mail.geg.compose")).onPressed(() -> compose(null));
    private final Segmented audiences = new Segmented(List.of(tr("mail.geg.player"), tr("mail.geg.organization"), tr("mail.geg.everyone")));
    private final MailInbox inbox = new MailInbox();
    private final Menu mailMenu = new Menu().empty(tr("mail.geg.loading"));
    private final Scroll mailScroll = new Scroll().content(mailMenu).horizontal(Scroll.BarMode.DISABLED);
    private final Label detailTitle = new Label().hero(true).wrap(Label.Wrap.WORD).overflow(Label.Overflow.ELLIPSIS);
    private final Avatar detailAvatar = new Avatar();
    private final Box detailAuthor = new Box().orientation(Box.Orientation.HORIZONTAL).gap(6);
    private final Label detailSender = new Label().overflow(Label.Overflow.ELLIPSIS);
    private final Label detailDate = new Label().color(OreTheme.TEXT_SECONDARY).overflow(Label.Overflow.ELLIPSIS);
    private boolean revealPreview;
    private final Label detailText = new Label().color(OreTheme.TEXT_WHITE).wrap(Label.Wrap.WORD);
    private final Label attachment = new Label().color(OreTheme.TEXT_SECONDARY).wrap(Label.Wrap.WORD);
    private final Divider attachmentDivider = new Divider();
    private final MailAttachmentsView rewards = new MailAttachmentsView(() -> query(MailActionPacket.Action.CLAIM, this.selected));
    private final Box detailActions = new Box().gap(2);
    private final Scroll detailScroll;
    private final Scroll columnsScroll;
    private final Drawer editor = new Drawer(tr("mail.geg.compose")).depth(300);
    private final Input title;
    private final TextArea body;
    private final Input target;
    private final NumberInput coin;
    private final Form form = new Form();
    private final FormItem targetItem;
    private final FormItem scopeItem;
    private final FormItem coinItem;
    private final Segmented scopes = new Segmented(List.of(tr("mail.geg.player"), tr("mail.geg.organization"), tr("mail.geg.everyone")), Button.Size.SMALL);
    private final Label previewText = new Label().color(OreTheme.TEXT_WHITE).wrap(Label.Wrap.WORD);
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
        OreToasts.show("geg:mail:feedback", new Toast(tr(key)).type(type));
    }

    MailCanvasWidget(Input title, TextArea body, Input target, NumberInput coin, UUID initialOrganization) {
        this.title = title; this.body = body; this.target = target; this.coin = coin; this.initialOrganization = initialOrganization;
        send.minimum(56, Button.HEIGHT);
        send.visible(ClientMailScreen.admin());
        mailMenu.onSelect(item -> { selectedItemKey = item.key(); showDetail(MailListItem.messageId(item)); });
        navbar.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        audiences.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND | SizeFlags.SIZE_SHRINK_BEGIN)
                .sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        send.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        audiences.onSelect(index -> selectInbox(MailActionPacket.Scope.values()[index]));
        Box audienceRow = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4).add(audiences).add(send);
        audienceRow.minimum(0, Button.HEIGHT).sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        for (Control label : List.of(detailTitle, detailSender, detailDate, detailText, attachment))
            label.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Box authorInfo = new Box().gap(2).add(detailSender).add(detailDate);
        authorInfo.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        detailAuthor.add(detailAvatar).add(authorInfo);
        detailAuthor.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        detailScroll = new Scroll().horizontal(Scroll.BarMode.DISABLED).content(new MailDetailBody(
                detailText, attachmentDivider, attachment, rewards, detailActions));
        detailScroll.minimum(0, 16).sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        detailActions.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Panel detailPanel = new Panel(null).child(new Padding(Padding.Insets.all(4))
                .child(new Box().gap(4).add(detailTitle).add(detailAuthor).add(detailScroll)));
        Panel menuPanel = new Panel(OreTheme.PANEL)
                .child(new Padding(new Padding.Insets(8, 1, 8, 1)).child(mailScroll));
        columnsScroll = new Scroll().vertical(Scroll.BarMode.DISABLED).content(new MailColumns(menuPanel, detailPanel));
        columnsScroll.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL); columnsScroll.minimum(0, 41);
        Padding padding = new Padding(Padding.Insets.all(4));
        padding.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        window.content(new Box().gap(0).add(navbar).add(
                padding.child(new Box().gap(4).add(audienceRow).add(columnsScroll))
        )).overlay(overlay);
        clearDetail();
        renderInbox();
        scopeItem = new FormItem("scope", tr("mail.geg.scope"), scopes, () -> scope().name());
        targetItem = new FormItem("target", tr("mail.geg.target"), target);
        coinItem = new FormItem("coin", tr("mail.geg.coin_per_player"), coin);
        form.add(scopeItem).add(targetItem).add(new FormItem("title", tr("mail.geg.subject"), title))
                .add(new FormItem("body", tr("mail.geg.body"), body)).add(coinItem);
        form.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        previewText.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        editor.content(new Box().gap(6).add(form).add(previewText));
        editor.onClose(() -> { form.setVisible(false); busy = false; token = null; });
        scopes.onSelect(index -> { targetItem.setActive(scope() != MailActionPacket.Scope.ALL); editAgain(); });
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
        mailMenu.items(MailListItem.groups(inbox.rows(), inbox.loading() || inbox.hasMore()))
                .empty(tr(inbox.loading() ? "mail.geg.loading" : "mail.geg.empty"));
        if (selected != null) {
            if (selectedItemKey == null || mailMenu.button(selectedItemKey) == null)
                selectedItemKey = MailListItem.key(MailState.Filter.ALL, selected);
            mailMenu.selectedKey(selectedItemKey);
        }
    }
    private void query(MailActionPacket.Action action, UUID id) {
        MailNetwork.send(MailActionPacket.query(action, id, inbox.filter(), 0, inbox.scope()));
    }
    private void showDetail(UUID id) {
        selected = id; selectedMessage = null; detailText.text(tr("mail.geg.loading"));
        detailTitle.text(Component.empty()); detailSender.text(Component.empty()); detailDate.text(Component.empty()); attachment.text(Component.empty());
        detailAuthor.visible(false); detailAvatar.initials("");
        rewards.attachments(List.of(), false, false); attachmentDivider.visible(false);
        clearDetailActions(); detailScroll.scrollToTop(); query(MailActionPacket.Action.DETAIL, id);
    }
    private void clearDetailActions() {
        for (Control action : detailActions.children()) detailActions.removeChild(action);
        detailActions.minimum(0, 0); detailActions.visible(false);
    }
    private void clearDetail() {
        selected = null; selectedItemKey = null; selectedMessage = null;
        detailTitle.text(tr("mail.geg.detail")); detailSender.text(Component.empty()); detailDate.text(Component.empty());
        detailText.text(tr("mail.geg.select_message")); attachment.text(Component.empty());
        detailAuthor.visible(false); detailAvatar.initials("");
        rewards.attachments(List.of(), false, false); attachmentDivider.visible(false);
        clearDetailActions();
    }
    private void updateDetail() {
        if (selectedMessage == null) return;
        MailMessage mail = selectedMessage;
        detailTitle.text(mail.titleText());
        Component source = mail.kind() == MailMessage.Kind.SYSTEM ? (mail.sender().isEmpty()
                ? tr("mail.geg.market.source") : tr("mail.geg.system_source", mail.sender()))
                : tr(mail.kind() == MailMessage.Kind.ADMIN ? "mail.geg.admin_source" : "mail.geg.organization_source", mail.sender());
        if (!mail.authorName().isBlank()) {
            detailSender.text(Component.literal(mail.authorName()));
            detailAvatar.player(mail.authorId(), mail.authorName());
        } else {
            detailSender.text(source);
            detailAvatar.initials(mail.sender());
        }
        detailAvatar.visible(mail.kind() != MailMessage.Kind.SYSTEM);
        detailAuthor.visible(true);
        detailDate.text(Component.literal(time(mail.sentAt()))); detailText.text(mail.bodyText());
        attachment.text(mail.coin() == 0 ? Component.empty() : tr(selectedClaimed ? "mail.geg.claimed_amount" : "mail.geg.attachment_amount",
                MoneyCurrency.COIN.format(mail.coin())));
        clearDetailActions();
        boolean claimable = mail.hasAttachments() && !selectedClaimed;
        rewards.attachments(mail.items(), mail.hasAttachments(), selectedClaimed);
        attachmentDivider.visible(mail.hasAttachments());
        if (claimable) return;
        Button action = new Button(tr("mail.geg.delete")).type(Button.Type.DESTRUCTIVE)
                .onPressed(() -> { deleting = selected; query(MailActionPacket.Action.DELETE, selected); });
        action.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        detailActions.add(action); detailActions.minimum(0, action.minimumHeight()); detailActions.visible(true);
    }
    private MailActionPacket.Scope scope() { return organization != null ? MailActionPacket.Scope.ORGANIZATION : MailActionPacket.Scope.values()[scopes.selected()]; }
    private void compose(UUID organization) {
        this.organization = organization; busy = false; token = null; previewSignature = null;
        editor.title(tr(organization == null ? "mail.geg.compose" : "mail.geg.announce"));
        scopeItem.setActive(organization == null); targetItem.setActive(organization == null && scope() != MailActionPacket.Scope.ALL);
        coinItem.setActive(organization == null); editor.open(); form.setVisible(true); editAgain();
    }
    private String signature() {
        return scope() + "\0" + organization + "\0" + title.getText() + "\0" + body.getText() + "\0" + target.getText() + "\0" + coin.getText();
    }
    private void editAgain() {
        busy = false; token = null; previewSignature = null; previewText.text(Component.empty());
        editor.clearActions().action(tr("mail.geg.preview"), this::preview, Button.Type.PRIMARY);
    }
    private void preview() {
        if (busy) return;
        if (!form.validate()) {
            Component error = coin.error();
            OreToasts.show("geg:mail:feedback", new Toast(error == null ? tr("mail.geg.invalid_coin") : error).type(Toast.Type.WARNING));
            return;
        }
        if (!MailPolicy.validText(title.getText(), 64, false) || !MailPolicy.validText(body.getText(), 2048, true)) {
            feedback("mail.geg.invalid_text"); return;
        }
        long amount;
        try { amount = organization == null ? new BigDecimal(coin.getText()).movePointRight(2).longValueExact() : 0; }
        catch (ArithmeticException | NumberFormatException exception) { feedback("mail.geg.invalid_coin"); return; }
        if (amount < 0) { feedback("mail.geg.invalid_coin"); return; }
        token = UUID.randomUUID(); previewSignature = signature(); busy = true; previewText.text(Component.empty());
        MailNetwork.send(new MailActionPacket(MailActionPacket.Action.PREVIEW, token, inbox.filter(), 0, scope(), organization != null,
                organization == null ? MailActionPacket.EMPTY : organization, target.getText(), title.getText(), body.getText(), amount));
    }
    private void confirm() {
        if (busy || token == null) return;
        if (!Objects.equals(previewSignature, signature())) { editAgain(); feedback("mail.geg.preview_again"); return; }
        busy = true; query(MailActionPacket.Action.CONFIRM, token);
    }
    void accept(MailSyncPacket packet) {
        send.visible(packet.admin());
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
            previewText.text(tr("mail.geg.preview_summary", packet.recipients(), MoneyCurrency.COIN.format(packet.coin()), packet.total()));
            feedback(packet.result());
            editor.clearActions().action(tr("mail.geg.confirm_send"), this::confirm, Button.Type.PRIMARY)
                    .action(tr("mail.geg.edit"), this::editAgain);
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
        DebugBridge.backend().close(window); close(); super.dispose(); }
    @Override public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        int width = getArea().width, height = getArea().height;
        int drawerWidth = width < 320 ? width : width / 2;
        editor.width(drawerWidth);
        for (Label label : List.of(detailTitle, detailSender, detailDate, attachment, previewText))
            label.visible(!label.text().getString().isEmpty());
        // Clear the previous minimum so a two-row navbar can return to one row on resize.
        navbar.minimum(0, 0);
        navbar.minimum(0, navbar.measureHeight(context.getFont(), width));
        window.layoutViewport(width, height, context.getFont());
        if (inbox.catchingUp() || mailScroll.scrollY() + mailScroll.viewport().height() * 2 >= mailMenu.contentExtent().height()) {
            MailActionPacket next = inbox.nextBatch();
            if (next != null) MailNetwork.send(next);
        }
        if (!editor.visible()) form.setVisible(false);
        if (revealPreview && editor.isOpen()) {
            editor.bodyScroll().scrollBy(0, editor.bodyScroll().maxScrollY()); revealPreview = false;
        }
        window.draw(context, getArea().x, getArea().y);
    }
    @Override public @NotNull Interactable.Result onMousePressed(int button) {
        window.focusManager().pointerActivity();
        // SUCCESS also registers this canvas as MUI's held target; STOP alone discards drag events.
        return button == 0 && window.pointerPressed(mx(), my(), getContext()) ? Interactable.Result.SUCCESS : Interactable.Result.IGNORE;
    }
    @Override public boolean onMouseReleased(int button) {
        if (button != 0) return false;
        return window.pointerReleased(mx(), my());
    }
    @Override public void onMouseDrag(int button, double x, double y) {
        if (button != 0) return;
        window.pointerDragged(mx(), my());
    }
    @Override public boolean onMouseScrolled(double delta) {
        if (editor.visible()) return editor.dispatchScroll(mx(), my(), delta, false);
        if (columnsScroll.hit(mx(), my())) return columnsScroll.dispatchScroll(mx(), my(), delta, net.minecraft.client.gui.screens.Screen.hasShiftDown());
        if (audiences.hit(mx(), my())) return audiences.scrollBy(-(int) Math.signum(delta) * 24, getContext().getFont());
        return false;
    }
    @Override public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        return window.keyPressed(keyCode, modifiers, getContext())
                ? Interactable.Result.STOP : Interactable.Result.IGNORE;
    }
    @Override public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) { return window.keyReleased(keyCode, modifiers, getContext()); }
    private int mx() { return getContext().getAbsMouseX() - getArea().x; }
    private int my() { return getContext().getAbsMouseY() - getArea().y; }
}
