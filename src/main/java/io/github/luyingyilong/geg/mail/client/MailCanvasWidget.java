package io.github.luyingyilong.geg.mail.client;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.geg.client.ui.*;
import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.mail.*;
import io.github.luyingyilong.geg.money.MoneyCurrency;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Ore owns one geometry/focus tree; MUI Input widgets are synchronized siblings of this canvas. */
final class MailCanvasWidget extends Widget<MailCanvasWidget> implements Interactable {
    private final Window window = new Window(true);
    private final Overlay overlay = new Overlay();
    private final Header header = new Header(tr("mail.geg.title"));
    private final Label unread = new Label().color(OreTheme.TEXT_DARK);
    private final Label notice = new Label().color(OreTheme.TEXT_SECONDARY).wrap(Label.Wrap.WORD);
    private final Label pageLabel = new Label().color(OreTheme.TEXT_WHITE).align(Label.Horizontal.CENTER, Label.Vertical.CENTER);
    private final Button send = new Button(tr("mail.geg.compose")).onPressed(() -> compose(null));
    private final Button previous = new Button(tr("mail.geg.previous")).onPressed(() -> loadPage(Math.max(0, this.offset - 50)));
    private final Button next = new Button(tr("mail.geg.next")).onPressed(() -> loadPage(this.offset + 50));
    private final Tabs tabs = new Tabs(List.of(tr("mail.geg.all"), tr("mail.geg.unread"), tr("mail.geg.pending")));
    private final Table<MailSyncPacket.Row> table = new Table<>();
    private final Drawer detail = new Drawer(tr("mail.geg.detail"));
    private final Label detailNotice = new Label().color(OreTheme.TEXT_ERROR).wrap(Label.Wrap.WORD);
    private boolean revealPreview;
    private final Label detailText = new Label().color(OreTheme.TEXT_WHITE).wrap(Label.Wrap.WORD);
    private final Drawer editor = new Drawer(tr("mail.geg.compose")).depth(300);
    private final Input title;
    private final TextArea body;
    private final Input target;
    private final NumberInput coin;
    private final Form form = new Form();
    private final FormItem targetItem;
    private final FormItem scopeItem;
    private final FormItem coinItem;
    private final Tabs scopes = new Tabs(List.of(tr("mail.geg.player"), tr("mail.geg.organization"), tr("mail.geg.everyone")), Button.Size.SMALL);
    private final Label previewText = new Label().color(OreTheme.TEXT_WHITE).wrap(Label.Wrap.WORD);
    private final UUID initialOrganization;
    private UUID organization;
    private UUID selected;
    private UUID token;
    private UUID deleting;
    private MailMessage selectedMessage;
    private boolean selectedClaimed;
    private boolean busy;
    private boolean more;
    private int offset;
    private int requestedOffset;
    private MailState.Filter filter = MailState.Filter.ALL;
    private String previewSignature;
    private long revision = -1;
    private static Component tr(String key, Object... args) { return Component.translatable(key, args); }

    MailCanvasWidget(Input title, TextArea body, Input target, NumberInput coin, UUID initialOrganization) {
        this.title = title; this.body = body; this.target = target; this.coin = coin; this.initialOrganization = initialOrganization;
        unread.minimum(48, Button.HEIGHT);
        send.minimum(56, Button.HEIGHT);
        send.visible(ClientMailScreen.admin());
        header.extra(unread, send);
        table.rowKey(MailSyncPacket.Row::id).onRowClick(row -> showDetail(row.id())).empty(tr("mail.geg.loading"));
        table.column(column("title", 80, 150, 2, row -> row.title()));
        table.column(column("source", 60, 100, 1, row -> tr("mail.geg.kind." + row.kind().name().toLowerCase(Locale.ROOT)).copy().append(Component.literal(" · " + row.sender()))));
        table.column(column("time", 56, 76, 0, row -> Component.literal(time(row.sentAt()))));
        table.column(column("state", 64, 100, 0, row -> tr(row.coin() > 0 && !row.claimed()
                ? "mail.geg.pending" : row.read() ? "mail.geg.read" : "mail.geg.unread")));
        table.policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND); table.minimum(0, 35);
        header.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        tabs.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        tabs.onSelect(index -> { filter = MailState.Filter.values()[index]; table.scrollToTop(); loadPage(0); });
        pageLabel.policy(Control.SizePolicy.EXPAND, Control.SizePolicy.FILL);
        Box paging = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4).add(previous).add(pageLabel).add(next);
        paging.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        notice.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        window.content(new Box().gap(4).add(header).add(tabs).add(notice).add(table).add(paging)).overlay(overlay);
        detailText.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        detailNotice.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        detail.content(new Box().gap(4).add(detailNotice).add(detailText));
        scopeItem = new FormItem("scope", tr("mail.geg.scope"), scopes, () -> scope().name());
        targetItem = new FormItem("target", tr("mail.geg.target"), target);
        coinItem = new FormItem("coin", tr("mail.geg.coin_per_player"), coin);
        form.add(scopeItem).add(targetItem).add(new FormItem("title", tr("mail.geg.subject"), title))
                .add(new FormItem("body", tr("mail.geg.body"), body)).add(coinItem);
        form.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        previewText.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        editor.content(new Box().gap(6).add(form).add(previewText));
        editor.onClose(() -> { form.setVisible(false); busy = false; token = null; });
        scopes.onSelect(index -> { targetItem.setActive(scope() != MailActionPacket.Scope.ALL); editAgain(); });
        overlay.add(detail).add(editor);
        previous.disabled(true); next.disabled(true);
    }
    private Table.Column<MailSyncPacket.Row> column(String key, int minimum, int width, int grow,
            java.util.function.Function<MailSyncPacket.Row, Component> value) {
        return new Table.Column<>(key, tr("mail.geg." + key), minimum, width, grow, w -> 18,
                new Table.TextCell<>(value, OreTheme.TEXT_WHITE).overflow(Label.Overflow.ELLIPSIS));
    }
    private static String time(long millis) {
        return DateTimeFormatter.ofPattern("MM-dd HH:mm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }
    void initialize() { loadPage(0); if (initialOrganization != null) compose(initialOrganization); }
    private void loadPage(int start) {
        requestedOffset = start;
        MailNetwork.send(MailActionPacket.query(MailActionPacket.Action.LIST, MailActionPacket.EMPTY, filter, start));
    }
    private void query(MailActionPacket.Action action, UUID id) {
        MailNetwork.send(MailActionPacket.query(action, id, filter, offset));
    }
    private void showDetail(UUID id) {
        selected = id; selectedMessage = null; detailNotice.text(Component.empty()); detailText.text(tr("mail.geg.loading")); detail.clearActions();
        detail.open(); query(MailActionPacket.Action.DETAIL, id);
    }
    private void updateDetail() {
        if (selectedMessage == null) return;
        MailMessage mail = selectedMessage;
        detail.title(mail.titleText());
        Component source = mail.kind() == MailMessage.Kind.SYSTEM ? tr("mail.geg.system_source", mail.sender())
                : tr(mail.kind() == MailMessage.Kind.ADMIN ? "mail.geg.admin_source" : "mail.geg.organization_source", mail.sender());
        detailText.text(source.copy().append(Component.literal("\n" + time(mail.sentAt()) + "\n\n"))
                .append(mail.bodyText()).append(Component.literal("\n\n"))
                .append(mail.coin() == 0 ? Component.empty() : tr(selectedClaimed ? "mail.geg.claimed_amount" : "mail.geg.attachment_amount",
                        MoneyCurrency.COIN.format(mail.coin()))));
        detail.clearActions();
        if (mail.coin() > 0 && !selectedClaimed) detail.action(tr("mail.geg.claim"), () -> query(MailActionPacket.Action.CLAIM, selected), Button.Type.PRIMARY);
        else detail.action(tr("mail.geg.delete"), () -> { deleting = selected; query(MailActionPacket.Action.DELETE, selected); }, Button.Type.DESTRUCTIVE);
    }
    private MailActionPacket.Scope scope() { return organization != null ? MailActionPacket.Scope.ORGANIZATION : MailActionPacket.Scope.values()[scopes.selected()]; }
    private void compose(UUID organization) {
        this.organization = organization; busy = false; token = null; previewSignature = null;
        detail.close(); editor.title(tr(organization == null ? "mail.geg.compose" : "mail.geg.announce"));
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
        if (!form.validate()) { revealPreview = true; return; }
        if (!MailPolicy.validText(title.getText(), 64, false) || !MailPolicy.validText(body.getText(), 2048, true)) {
            previewText.text(tr("mail.geg.invalid_text")); revealPreview = true; return;
        }
        long amount;
        try { amount = organization == null ? new BigDecimal(coin.getText()).movePointRight(2).longValueExact() : 0; }
        catch (ArithmeticException | NumberFormatException exception) { previewText.text(tr("mail.geg.invalid_coin")); revealPreview = true; return; }
        if (amount < 0) { previewText.text(tr("mail.geg.invalid_coin")); revealPreview = true; return; }
        token = UUID.randomUUID(); previewSignature = signature(); busy = true; previewText.text(tr("mail.geg.loading"));
        MailNetwork.send(new MailActionPacket(MailActionPacket.Action.PREVIEW, token, filter, offset, scope(), organization != null,
                organization == null ? MailActionPacket.EMPTY : organization, target.getText(), title.getText(), body.getText(), amount));
    }
    private void confirm() {
        if (busy || token == null) return;
        if (!Objects.equals(previewSignature, signature())) { editAgain(); previewText.text(tr("mail.geg.preview_again")); return; }
        busy = true; query(MailActionPacket.Action.CONFIRM, token);
    }
    void accept(MailSyncPacket packet) {
        unread.text(tr("mail.geg.unread_count", packet.unread())); send.visible(packet.admin());
        if (packet.kind() == MailSyncPacket.Kind.LIST && packet.filter() == filter && packet.offset() <= requestedOffset) {
            table.rows(packet.rows()); offset = packet.offset(); more = packet.more();
            table.empty(tr("mail.geg.empty")); previous.disabled(offset == 0); next.disabled(!more);
            pageLabel.text(Component.literal(Integer.toString(offset / 50 + 1)));
            revision = packet.revision();
        } else if (packet.kind() == MailSyncPacket.Kind.STATUS && packet.revision() != revision) loadPage(offset);
        else if (packet.kind() == MailSyncPacket.Kind.DETAIL && packet.detail() != null && Objects.equals(selected, packet.detail().id())) {
            selectedMessage = packet.detail(); selectedClaimed = packet.claimed(); updateDetail(); loadPage(offset);
        } else if (packet.kind() == MailSyncPacket.Kind.PREVIEW && editor.isOpen() && Objects.equals(token, packet.token())) {
            busy = false; revealPreview = true;
            if (!Objects.equals(previewSignature, signature())) { editAgain(); return; }
            previewText.text(tr("mail.geg.preview_summary", packet.recipients(), MoneyCurrency.COIN.format(packet.coin()), packet.total())
                    .copy().append(packet.result().isEmpty() ? Component.empty() : Component.literal("\n").append(tr(packet.result()))));
            editor.clearActions().action(tr("mail.geg.confirm_send"), this::confirm, Button.Type.PRIMARY)
                    .action(tr("mail.geg.edit"), this::editAgain);
        } else if (packet.kind() == MailSyncPacket.Kind.RESULT) {
            if (editor.isOpen() && Objects.equals(token, packet.token())) {
                busy = false;
                if (packet.result().equals("mail.geg.sent")) { editor.close(); form.setVisible(false); notice.text(tr(packet.result())); loadPage(offset); }
                else { previewText.text(tr(packet.result())); revealPreview = true; }
            } else {
                notice.text(tr(packet.result())); loadPage(offset);
                if (detail.isOpen() && Objects.equals(selected, packet.token())) {
                    detailNotice.text(packet.result().equals("mail.geg.success") ? Component.empty() : tr(packet.result()));
                    if (packet.result().equals("mail.geg.not_found")) detail.close();
                    if (packet.result().equals("mail.geg.success") && Objects.equals(deleting, selected)) {
                        deleting = null; selected = null; detail.close();
                    } else if (packet.result().equals("mail.geg.success")) query(MailActionPacket.Action.DETAIL, selected);
                }
            }
        }
    }
    void close() { form.setVisible(false); window.cancelInteractions(); }
    @Override public void dispose() { close(); super.dispose(); }
    @Override public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        int width = getArea().width, height = getArea().height;
        int drawerWidth = width < 320 ? width : width / 2;
        detail.width(drawerWidth); editor.width(drawerWidth);
        window.layoutViewport(width, height, context.getFont());
        if (!editor.visible()) form.setVisible(false);
        if (revealPreview && editor.isOpen()) {
            editor.bodyScroll().scrollBy(0, editor.bodyScroll().maxScrollY()); revealPreview = false;
        }
        window.draw(context, getArea().x, getArea().y);
    }
    @Override public @NotNull Interactable.Result onMousePressed(int button) {
        window.focusManager().pointerActivity();
        return button == 0 && window.pointerPressed(mx(), my(), getContext()) ? Interactable.Result.STOP : Interactable.Result.IGNORE;
    }
    @Override public boolean onMouseReleased(int button) {
        if (button != 0) return false;
        return window.pointerReleased(mx(), my()) | detail.bodyScroll().endDrag() | editor.bodyScroll().endDrag();
    }
    @Override public void onMouseDrag(int button, double x, double y) {
        if (button == 0) { if (editor.isOpen()) editor.bodyScroll().dragTo(mx(), my()); else if (detail.isOpen()) detail.bodyScroll().dragTo(mx(), my()); }
    }
    @Override public boolean onMouseScrolled(double delta) {
        if (editor.visible()) return editor.dispatchScroll(mx(), my(), delta, false);
        if (detail.visible()) return detail.dispatchScroll(mx(), my(), delta, false);
        if (table.hit(mx(), my())) { table.scrollBy(-(int) Math.signum(delta) * (table.compact() ? 40 : 23)); return true; }
        if (tabs.hit(mx(), my())) return tabs.scrollBy(-(int) Math.signum(delta) * 24, getContext().getFont());
        return false;
    }
    @Override public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (window.keyPressed(keyCode, modifiers, getContext())) return Interactable.Result.STOP;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (editor.visible()) { editor.close(); return Interactable.Result.STOP; }
            if (detail.visible()) { detail.close(); return Interactable.Result.STOP; }
        }
        return Interactable.Result.IGNORE;
    }
    @Override public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) { return window.keyReleased(keyCode, modifiers, getContext()); }
    private int mx() { return getContext().getAbsMouseX() - getArea().x; }
    private int my() { return getContext().getAbsMouseY() - getArea().y; }
}
