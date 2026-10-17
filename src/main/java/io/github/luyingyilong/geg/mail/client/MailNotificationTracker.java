package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.geg.mail.MailSyncPacket;

/** Quiet status updates advance the baseline without duplicating the associated operation's toast. */
final class MailNotificationTracker {
    private long revision = -1;
    private int unread;
    boolean accept(MailSyncPacket packet) {
        if (packet.revision() < revision) return false;
        boolean received = revision >= 0 && packet.kind() == MailSyncPacket.Kind.STATUS && packet.unread() > unread;
        revision = packet.revision(); unread = packet.unread();
        return received;
    }
    void reset() { revision = -1; unread = 0; }
}
