package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.geg.client.ui.MenuItem;
import io.github.luyingyilong.geg.client.ui.Header;
import io.github.luyingyilong.geg.client.ui.Button;
import io.github.luyingyilong.geg.client.ui.Segmented;
import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.util.OreIcons;
import io.github.luyingyilong.geg.mail.*;
import io.github.luyingyilong.geg.money.MoneySavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.UUID;

public final class MailLayoutTest {
    public static void main(String[] args) {
        Scroll messages = new Scroll().content(new Control().preferred(100, 1200));
        Scroll detail = new Scroll().content(new Control().preferred(120, 1600));
        detail.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Panel detailPanel = new Panel(null).child(new Padding(Padding.Insets.all(4)).child(new Box().add(detail)));
        Scroll horizontal = new Scroll().content(new MailColumns(messages, detailPanel)).vertical(Scroll.BarMode.DISABLED);
        horizontal.minimum(0, 41).sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        Button send = new Button(Component.literal("Compose"));
        send.minimum(56, Button.HEIGHT);
        Header header = new Header(Component.literal("Mail"));
        Segmented audiences = new Segmented(List.of(Component.literal("Player"), Component.literal("Organization"), Component.literal("Server")));
        audiences.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND | SizeFlags.SIZE_SHRINK_BEGIN)
                .sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        send.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Box audienceRow = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4).add(audiences).add(send);
        audienceRow.minimum(0, Button.HEIGHT).sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Padding content = new Padding(Padding.Insets.all(4))
                .child(new Box().gap(4).add(audienceRow).add(horizontal));
        content.sizeFlagsHorizontal(SizeFlags.SIZE_FILL).sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Window window = new Window(true).content(new Box().gap(0).add(header).add(content));
        for (int height : new int[]{128, 240, 480}) for (int width : new int[]{127, 128, 129, 640, 854}) {
            header.minimum(0, 0);
            header.minimum(0, header.measureHeight(font, width));
            window.layoutViewport(width, height, font);
            check(header.bounds().y() == 0 && header.size().height() >= Header.ROW_HEIGHT);
            check(header.size().height() == 25);
            check(audiences.absoluteBounds().x() == 4);
            check(audiences.size().width() == Math.min(width - 12 - send.size().width(), audiences.minimumRowWidth(font)));
            check(send.absoluteBounds().right() == width - 4 && send.parent() == audiences.parent());
            check(send.absoluteBounds().y() == audiences.absoluteBounds().y());
            check(content.absoluteBounds().y() == header.bounds().bottom());
            check(content.absoluteBounds().bottom() == height);
            check(messages.size().width() >= 100 && detailPanel.size().width() >= 120);
            check(detail.size().height() == detailPanel.size().height() - 8);
            check(messages.maxScrollY() > 0 && detail.maxScrollY() > 0);
            check(messages.size().height() == horizontal.viewport().height());
            check(horizontal.maxScrollX() == Math.max(0, 224 - (width - 8)));
            check(horizontal.absoluteBounds().y() >= header.bounds().bottom() + audienceRow.bounds().height());
            check(horizontal.absoluteBounds().bottom() == height - 4);
            check(messages.absoluteBounds().bottom() == horizontal.absoluteBounds().y() + horizontal.viewport().height());
            check(detailPanel.absoluteBounds().bottom() == messages.absoluteBounds().bottom());
            horizontal.setScroll(horizontal.maxScrollX(), 0);
            check(horizontal.scrollX() == horizontal.maxScrollX());
            send.visible(false);
            window.layoutViewport(width, height, font);
            check(audiences.size().width() == Math.min(width - 8, audiences.minimumRowWidth(font)));
            send.visible(true);
        }
        for (boolean read : new boolean[]{false, true}) for (long coin : new long[]{0, 100}) {
            var row = new MailSyncPacket.Row(UUID.randomUUID(), Component.literal("Only title"), "Sender",
                    MailMessage.Kind.ADMIN, 123, coin, read, false, MailActionPacket.Scope.PLAYER);
            MenuItem item = MailListItem.create(row);
            check(item.label().getString().equals("Only title") && item.key().equals(row.id().toString()));
            check(item.icon().texture().equals(coin > 0 ? read ? OreIcons.GIFT_OPENED : OreIcons.GIFT
                    : read ? OreIcons.EMAIL_READED : OreIcons.EMAIL));
        }
        for (MailActionPacket.Scope scope : MailActionPacket.Scope.values()) {
            MailMessage mail = new MailMessage(UUID.randomUUID(), 123, MailMessage.Kind.ADMIN, "OP", "Title", "Body", List.of(), 100, scope);
            check(MailMessage.load(mail.save()).equals(mail));
            var legacy = mail.save(); legacy.remove("scope");
            check(MailMessage.load(legacy).scope() == MailActionPacket.Scope.ALL);
        }
        MailMessage system = new MailMessage(UUID.randomUUID(), 123, MailMessage.Kind.SYSTEM, "Org", "title", "body", List.of(), 0);
        check(system.scope() == MailActionPacket.Scope.ORGANIZATION);
        scopeFiltersSurviveReload();
        groupedMailItems();
    }

    private static void groupedMailItems() {
        UUID gift = UUID.randomUUID(), plain = UUID.randomUUID(), read = UUID.randomUUID();
        List<MailSyncPacket.Row> rows = List.of(
                new MailSyncPacket.Row(gift, Component.literal("Gift"), "OP", MailMessage.Kind.ADMIN, 3, 100, false, false, MailActionPacket.Scope.PLAYER),
                new MailSyncPacket.Row(plain, Component.literal("Notice"), "OP", MailMessage.Kind.ADMIN, 2, 0, false, false, MailActionPacket.Scope.PLAYER),
                new MailSyncPacket.Row(read, Component.literal("Read"), "OP", MailMessage.Kind.ADMIN, 1, 0, true, false, MailActionPacket.Scope.PLAYER));
        List<MenuItem> groups = MailListItem.groups(rows, false);
        check(groups.size() == 3 && groups.stream().allMatch(group -> group.type() == MenuItem.Type.GROUP));
        check(groups.get(0).children().size() == 3 && groups.get(1).children().size() == 2 && groups.get(2).children().size() == 1);
        List<MenuItem> children = groups.stream().flatMap(group -> group.children().stream()).toList();
        check(children.stream().map(MenuItem::key).distinct().count() == children.size());
        check(groups.get(0).children().get(0).label().getString().equals("Gift"));
        for (MenuItem group : groups) check(MailListItem.messageId(group.children().get(0)).equals(gift));
        for (MenuItem group : MailListItem.groups(List.of(), true)) {
            check(group.children().size() == 1 && group.children().get(0).type() == MenuItem.Type.EMPTY);
        }
    }

    private static void scopeFiltersSurviveReload() {
        MoneySavedData data = new MoneySavedData();
        UUID player = UUID.randomUUID();
        for (MailActionPacket.Scope scope : MailActionPacket.Scope.values()) {
            UUID id = UUID.randomUUID();
            MailMessage message = new MailMessage(id, scope.ordinal(), MailMessage.Kind.ADMIN,
                    "OP", "Title", "Body", List.of(), scope == MailActionPacket.Scope.PLAYER ? 100 : 0, scope);
            data.mail().deliver(data, id.toString(), message, List.of(player));
            if (scope == MailActionPacket.Scope.ORGANIZATION) data.mail().markRead(data, player, id);
        }
        MailState restored = MailState.load(data.mail().save());
        for (MailActionPacket.Scope scope : MailActionPacket.Scope.values()) {
            check(restored.list(player, MailState.Filter.ALL, scope).size() == 1);
            check(restored.list(UUID.randomUUID(), MailState.Filter.ALL, scope).isEmpty());
        }
        check(restored.list(player, MailState.Filter.UNREAD, MailActionPacket.Scope.ORGANIZATION).isEmpty());
        check(restored.list(player, MailState.Filter.PENDING, MailActionPacket.Scope.PLAYER).size() == 1);
        check(restored.list(player, MailState.Filter.PENDING, MailActionPacket.Scope.ALL).isEmpty());
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError("Mail presentation regression"); }
}
