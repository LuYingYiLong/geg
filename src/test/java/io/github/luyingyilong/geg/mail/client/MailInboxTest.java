package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.geg.mail.*;
import io.github.luyingyilong.geg.mail.MailActionPacket;
import io.github.luyingyilong.geg.mail.MailMessage;
import io.github.luyingyilong.geg.mail.MailState;
import io.github.luyingyilong.geg.mail.MailSyncPacket;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public final class MailInboxTest {
    public static void main(String[] args) {
        MailInbox inbox = new MailInbox();
        MailActionPacket first = inbox.refresh();
        check(first.offset() == 0 && inbox.loading() && inbox.nextBatch() == null);
        check(inbox.accept(packet(inbox.refresh(), 1, true, 0, 50)) == MailInbox.Update.UPDATED);
        check(inbox.rows().size() == 50 && !inbox.loading());
        check(inbox.accept(packet(first, 1, true, 0, 50)) == MailInbox.Update.IGNORED);
        MailActionPacket second = inbox.nextBatch();
        check(second.offset() == 50);
        check(inbox.accept(packet(second, 1, true, 50, 50)) == MailInbox.Update.UPDATED);
        check(inbox.rows().size() == 100);
        MailActionPacket refresh = inbox.refresh();
        check(inbox.accept(packet(refresh, 2, true, 0, 50)) == MailInbox.Update.CONTINUE);
        check(inbox.rows().size() == 100 && inbox.catchingUp());
        check(inbox.accept(packet(inbox.nextBatch(), 2, true, 50, 50)) == MailInbox.Update.UPDATED);
        check(inbox.rows().size() == 100 && !inbox.catchingUp());
        check(inbox.accept(packet(inbox.nextBatch(), 3, false, 100, 4)) == MailInbox.Update.RESTART);
        check(inbox.rows().size() == 100); // Never append two different server revisions.
        MailActionPacket old = inbox.refresh();
        MailActionPacket current = inbox.select(MailState.Filter.UNREAD, MailActionPacket.Scope.ORGANIZATION);
        check(inbox.rows().isEmpty() && current.scope() == MailActionPacket.Scope.ORGANIZATION);
        check(inbox.accept(packet(old, 3, true, 0, 50)) == MailInbox.Update.IGNORED);
        check(inbox.accept(packet(current, 3, false, 0, 2)) == MailInbox.Update.UPDATED);
        check(inbox.rows().size() == 2 && inbox.nextBatch() == null);
        current = inbox.select(MailState.Filter.PENDING, MailActionPacket.Scope.ALL);
        check(inbox.accept(packet(current, 4, false, 0, 0)) == MailInbox.Update.UPDATED);
        check(inbox.rows().isEmpty() && inbox.nextBatch() == null);
        current = inbox.refresh(); inbox.failed(current.id());
        check(!inbox.loading() && inbox.nextBatch() == null);
    }

    private static MailSyncPacket packet(MailActionPacket request, long revision, boolean more, int start, int size) {
        List<MailSyncPacket.Row> rows = IntStream.range(start, start + size).mapToObj(index ->
                new MailSyncPacket.Row(new UUID(0, index + 1), Component.literal("Mail " + index), "OP",
                        MailMessage.Kind.ADMIN, index, 0, false, false, request.scope())).toList();
        return new MailSyncPacket(MailSyncPacket.Kind.LIST, revision, 0, false, request.filter(), request.offset(),
                more, rows, null, false, request.id(), 0, 0, "", "");
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError("Mail batch regression"); }
}
