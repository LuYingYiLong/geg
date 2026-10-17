package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.geg.mail.*;
import java.util.List;

public final class MailNotificationTrackerTest {
    public static void main(String[] args) {
        MailNotificationTracker tracker = new MailNotificationTracker();
        check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS, 10, 8))); // Existing offline mail is a baseline.
        check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS, 10, 8)));
        check(!tracker.accept(packet(MailSyncPacket.Kind.DETAIL, 11, 7)));
        check(tracker.accept(packet(MailSyncPacket.Kind.STATUS, 12, 8)));
        check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS, 12, 8)));
        check(!tracker.accept(packet(MailSyncPacket.Kind.LIST, 12, 8)));
        check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS, 9, 100)));
        check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS, 12, 8)));
        check(tracker.accept(packet(MailSyncPacket.Kind.STATUS, 13, 10)));
        check(!tracker.accept(packet(MailSyncPacket.Kind.RESULT, 14, 9)));
        check(tracker.accept(packet(MailSyncPacket.Kind.STATUS, 15, 10)));
        tracker.reset(); check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS, 20, 100)));
        check(tracker.accept(packet(MailSyncPacket.Kind.STATUS, 21, 101)));
        // Market mail is covered by a purchase/sale toast but still updates the unread baseline.
        check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS_SILENT, 22, 102)));
        check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS, 22, 102)));
        check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS_SILENT, 22, 102)));
        check(tracker.accept(packet(MailSyncPacket.Kind.STATUS, 23, 103))); // Unrelated mail still notifies.
        check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS_SILENT, 21, 200))); // Ignore stale quiet updates.
        check(tracker.accept(packet(MailSyncPacket.Kind.STATUS, 24, 104)));
        tracker.reset();
        check(!tracker.accept(packet(MailSyncPacket.Kind.STATUS_SILENT, 30, 5)));
        check(tracker.accept(packet(MailSyncPacket.Kind.STATUS, 31, 6)));
    }
    private static MailSyncPacket packet(MailSyncPacket.Kind kind, long revision, int unread) {
        return new MailSyncPacket(kind, revision, unread, false, MailState.Filter.ALL, 0, false,
                List.of(), null, false, MailActionPacket.EMPTY, 0, 0, "", "");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("New-mail toast notification regression"); }
}
