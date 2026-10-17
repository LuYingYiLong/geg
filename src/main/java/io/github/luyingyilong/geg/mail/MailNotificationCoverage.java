package io.github.luyingyilong.geg.mail;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** An event's operation toast covers only the connection that received it, never a later login. */
final class MailNotificationCoverage<S> {
    private final Map<UUID, S> connections = new HashMap<>();

    void cover(UUID event, S connection) {
        connections.put(Objects.requireNonNull(event), Objects.requireNonNull(connection));
    }

    boolean consume(UUID event, S connection) {
        S covered = connections.remove(event);
        return connection != null && covered == connection;
    }

    void forget(S connection) { connections.values().removeIf(covered -> covered == connection); }
}
