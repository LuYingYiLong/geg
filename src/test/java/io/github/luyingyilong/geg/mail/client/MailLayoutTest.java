package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.geg.client.theme.GegThemes;
import io.github.luyingyilong.ore.render.TextureSource;

import io.github.luyingyilong.ore.control.display.avatar.Avatar;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.navigation.Navbar;
import io.github.luyingyilong.ore.control.navigation.MenuItem;
import io.github.luyingyilong.ore.control.input.Segmented;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.layout.Padding;
import io.github.luyingyilong.ore.control.layout.Panel;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.window.Window;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.geg.mail.*;
import io.github.luyingyilong.geg.mail.MailActionPacket;
import io.github.luyingyilong.geg.mail.MailMessage;
import io.github.luyingyilong.geg.mail.MailState;
import io.github.luyingyilong.geg.mail.MailSyncPacket;
import io.github.luyingyilong.geg.money.MoneySavedData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.List;
import java.util.UUID;

public final class MailLayoutTest {
    public static void main(String[] args) {
        Scroll messages = new Scroll().setContent(new Control().setPreferred(100, 1200));
        Scroll detail = new Scroll().setContent(new Control().setPreferred(120, 1600));
        detail.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Control detailTitle = new Control().setPreferred(40, 12);
        Avatar avatar = new Avatar().setInitials("Sender");
        Box senderInfo = new Box().setGap(2);
        senderInfo.addChild(new Control().setMinimum(0, 10));
        senderInfo.addChild(new Control().setMinimum(0, 10));
        senderInfo.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Box senderRow = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(6);
        senderRow.addChild(avatar);
        senderRow.addChild(senderInfo);
        var childContainer95 = new Box().setGap(4);
        childContainer95.addChild(detailTitle);
        childContainer95.addChild(senderRow);
        childContainer95.addChild(detail);
        Panel detailPanel = new Panel(null).setChild(new Padding(Padding.Insets.all(4)).setChild(childContainer95));
        Panel menuPanel = new Panel(null).setChild(new Padding(new Padding.Insets(8, 1, 8, 1)).setChild(messages));
        Scroll horizontal = new Scroll().setContent(new MailColumns(menuPanel, detailPanel)).setVertical(Scroll.BarMode.DISABLED);
        horizontal.setMinimum(0, 41).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        Button send = new Button(Component.literal("Compose"));
        send.setMinimum(56, Button.HEIGHT);
        Navbar header = new Navbar(Component.literal("Mail"));
        Segmented audiences = new Segmented(List.of(Component.literal("Player"), Component.literal("Organization"), Component.literal("Server")));
        audiences.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND | SizeFlags.SIZE_SHRINK_BEGIN)
                .setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        send.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Box audienceRow = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
        audienceRow.addChild(audiences);
        audienceRow.addChild(send);
        audienceRow.setMinimum(0, Button.HEIGHT).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        var childContainer97 = new Box().setGap(4);
        childContainer97.addChild(audienceRow);
        childContainer97.addChild(horizontal);
        Padding content = new Padding(Padding.Insets.all(4)).setChild(childContainer97);
        content.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        var childContainer98 = new Box().setGap(0);
        childContainer98.addChild(header);
        childContainer98.addChild(content);
        Window window = new Window(true).setContent(childContainer98);
        for (int height : new int[]{128, 240, 480}) for (int width : new int[]{127, 128, 129, 640, 854}) {
            header.setMinimum(0, 0);
            header.setMinimum(0, header.measureHeight(font, width));
            window.layoutViewport(width, height, font);
            check(header.getBounds().y() == 0 && header.getSize().height() >= Navbar.ROW_HEIGHT);
            check(header.getSize().height() == Navbar.ROW_HEIGHT + Navbar.PADDING_BOTTOM.defaultValue());
            check(audiences.getAbsoluteBounds().x() == 4);
            check(audiences.getSize().width() == Math.min(width - 12 - send.getSize().width(), audiences.minimumRowWidth(font)));
            check(send.getAbsoluteBounds().right() == width - 4 && send.getParent() == audiences.getParent());
            check(send.getAbsoluteBounds().y() == audiences.getAbsoluteBounds().y());
            check(content.getAbsoluteBounds().y() == header.getBounds().bottom());
            check(content.getAbsoluteBounds().bottom() == height);
            check(menuPanel.getSize().width() >= 100 && detailPanel.getSize().width() >= 120);
            check(detail.getSize().height() == detailPanel.getSize().height() - 52);
            check(avatar.getSize().equals(new Size(24, 24)) && senderInfo.getSize().width() == senderRow.getSize().width() - 30);
            int titleTop = detailTitle.getAbsoluteBounds().y(), senderTop = senderRow.getAbsoluteBounds().y();
            detail.scrollBy(0, 100);
            check(detailTitle.getAbsoluteBounds().y() == titleTop && senderRow.getAbsoluteBounds().y() == senderTop);
            check(detail.getAbsoluteBounds().y() == senderRow.getAbsoluteBounds().bottom() + 4);
            check(messages.getMaxScrollY() > 0 && detail.getMaxScrollY() > 0);
            check(messages.getSize().height() == horizontal.getViewport().height() - 16);
            check(messages.getSize().width() == menuPanel.getSize().width() - 2);
            check(messages.getAbsoluteBounds().y() == menuPanel.getAbsoluteBounds().y() + 8);
            check(horizontal.getMaxScrollX() == Math.max(0, 224 - (width - 8)));
            check(horizontal.getAbsoluteBounds().y() >= header.getBounds().bottom() + audienceRow.getBounds().height());
            check(horizontal.getAbsoluteBounds().bottom() == height - 4);
            check(menuPanel.getAbsoluteBounds().bottom() == horizontal.getAbsoluteBounds().y() + horizontal.getViewport().height());
            check(detailPanel.getAbsoluteBounds().bottom() == messages.getAbsoluteBounds().bottom() + 8);
            horizontal.setScroll(horizontal.getMaxScrollX(), 0);
            check(horizontal.getScrollX() == horizontal.getMaxScrollX());
            send.setVisible(false);
            window.layoutViewport(width, height, font);
            check(audiences.getSize().width() == Math.min(width - 8, audiences.minimumRowWidth(font)));
            send.setVisible(true);
        }
        for (boolean read : new boolean[]{false, true}) for (long coin : new long[]{0, 100})
                for (boolean hasItems : new boolean[]{false, true}) {
            var row = new MailSyncPacket.Row(UUID.randomUUID(), Component.literal("Only title"), "Sender",
                    MailMessage.Kind.ADMIN, 123, coin, read, false, MailActionPacket.Scope.PLAYER, hasItems);
            MenuItem item = MailListItem.create(row);
            check(item.label().getString().equals("Only title") && item.key().equals(row.id().toString()));
            check(item.icon().equals(TextureSource.themed(coin > 0 || hasItems ? read ? GegThemes.MAIL_REWARD_READ : GegThemes.MAIL_REWARD_UNREAD
                    : read ? GegThemes.MAIL_READ : GegThemes.MAIL_UNREAD)));
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
                new MailSyncPacket.Row(read, Component.literal("Read"), "OP", MailMessage.Kind.ADMIN, 1, 0, true, false, MailActionPacket.Scope.PLAYER),
                new MailSyncPacket.Row(UUID.randomUUID(), Component.literal("Market items"), "", MailMessage.Kind.SYSTEM,
                        0, 0, false, false, MailActionPacket.Scope.PLAYER, true));
        List<MenuItem> groups = MailListItem.groups(rows, false);
        check(groups.size() == 3 && groups.stream().allMatch(group -> group.type() == MenuItem.Type.GROUP));
        check(groups.get(0).children().size() == 4 && groups.get(1).children().size() == 3 && groups.get(2).children().size() == 2);
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
