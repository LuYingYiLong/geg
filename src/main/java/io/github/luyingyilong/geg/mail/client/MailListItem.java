package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.geg.client.theme.GegThemes;
import io.github.luyingyilong.ore.render.*;

import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.MenuItem;
import io.github.luyingyilong.geg.mail.MailState;
import io.github.luyingyilong.geg.mail.MailSyncPacket;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** A mail row displays only its title and read/reward icon. */
final class MailListItem {
    private MailListItem() { }

    static MenuItem create(MailSyncPacket.Row row) {
        var icon = row.hasAttachments() ? row.read() ? GegThemes.MAIL_REWARD_READ : GegThemes.MAIL_REWARD_UNREAD
                : row.read() ? GegThemes.MAIL_READ : GegThemes.MAIL_UNREAD;
        return MenuItem.item(row.id().toString(), row.title()).icon(TextureSource.themed(icon));
    }

    static String key(MailState.Filter group, UUID id) { return group.name() + ":" + id; }

    static UUID messageId(MenuItem item) { return UUID.fromString(item.key().substring(item.key().indexOf(':') + 1)); }

    static List<MenuItem> groups(List<MailSyncPacket.Row> rows, boolean loading) {
        return Arrays.stream(MailState.Filter.values()).map(group -> {
            List<MenuItem> children = rows.stream().filter(row -> switch (group) {
                case ALL -> true;
                case UNREAD -> !row.read();
                case PENDING -> row.hasAttachments() && !row.claimed();
            }).map(row -> {
                MenuItem item = create(row);
                return MenuItem.item(key(group, row.id()), item.label()).icon(item.icon());
            }).toList();
            if (children.isEmpty()) children = List.of(MenuItem.empty("empty:" + group.name(),
                    Component.translatable(loading ? "mail.geg.loading" : "mail.geg.empty")));
            return MenuItem.group("group:" + group.name(),
                    Component.translatable("mail.geg." + group.name().toLowerCase(Locale.ROOT)), children);
        }).toList();
    }
}
