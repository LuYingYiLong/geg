package io.github.luyingyilong.geg.mail;

import java.util.UUID;

public final class MailNotificationCoverageTest {
    public static void main(String[] args) {
        MailNotificationCoverage<Object> coverage = new MailNotificationCoverage<>();
        Object recipient = new Object(), other = new Object();
        UUID approval = UUID.randomUUID(), rejection = UUID.randomUUID(), removal = UUID.randomUUID();
        coverage.cover(approval, recipient);
        coverage.cover(rejection, other);
        check(!coverage.consume(UUID.randomUUID(), recipient)); // Unrelated mail cannot consume the coverage.
        check(coverage.consume(approval, recipient));
        check(!coverage.consume(approval, recipient)); // Coverage applies exactly once.
        check(coverage.consume(rejection, other));

        coverage.cover(removal, recipient);
        check(!coverage.consume(removal, other)); // A different connection still needs a mail notification.
        coverage.cover(removal, recipient);
        check(!coverage.consume(removal, null)); // Delivery while offline discards transient coverage.
        check(!coverage.consume(removal, recipient));

        coverage.cover(approval, recipient);
        coverage.cover(rejection, other);
        coverage.forget(recipient);
        check(!coverage.consume(approval, recipient)); // Logging out before the mail retry clears coverage.
        check(coverage.consume(rejection, other));
        check(!new MailNotificationCoverage<>().consume(removal, recipient)); // Server restart cannot suppress replayed mail.
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Organization event notification coverage regression");
    }
}
