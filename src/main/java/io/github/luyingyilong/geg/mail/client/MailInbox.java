package io.github.luyingyilong.geg.mail.client;

import io.github.luyingyilong.geg.mail.*;

import java.util.*;

/** Sequential batches form one scrolling inbox; query IDs reject obsolete filter responses. */
final class MailInbox {
    enum Update { IGNORED, UPDATED, CONTINUE, RESTART }
    private final Map<UUID, MailSyncPacket.Row> rows = new LinkedHashMap<>();
    private final Map<UUID, MailSyncPacket.Row> staged = new LinkedHashMap<>();
    private MailState.Filter filter = MailState.Filter.ALL;
    private MailActionPacket.Scope scope = MailActionPacket.Scope.PLAYER;
    private UUID queryId;
    private int requestedOffset;
    private int nextOffset;
    private boolean loading;
    private boolean more;
    private long revision = -1;
    private int refreshTarget;

    MailState.Filter filter() { return filter; }
    MailActionPacket.Scope scope() { return scope; }
    List<MailSyncPacket.Row> rows() { return List.copyOf(rows.values()); }
    boolean loading() { return loading; }
    boolean hasMore() { return more; }
    long revision() { return revision; }
    boolean catchingUp() { return more && staged.size() < refreshTarget; }

    MailActionPacket select(MailState.Filter filter, MailActionPacket.Scope scope) {
        this.filter = Objects.requireNonNull(filter);
        this.scope = Objects.requireNonNull(scope);
        rows.clear();
        return refresh();
    }

    MailActionPacket refresh() {
        queryId = UUID.randomUUID(); requestedOffset = 0; loading = true; more = false;
        refreshTarget = rows.size(); staged.clear();
        return request();
    }

    MailActionPacket nextBatch() {
        if (loading || !more) return null;
        requestedOffset = nextOffset; loading = true;
        return request();
    }

    private MailActionPacket request() {
        return MailActionPacket.query(MailActionPacket.Action.LIST, queryId, filter, requestedOffset, scope);
    }

    Update accept(MailSyncPacket packet) {
        if (packet.kind() != MailSyncPacket.Kind.LIST || !loading || !Objects.equals(queryId, packet.token())
                || packet.filter() != filter || packet.offset() != requestedOffset) return Update.IGNORED;
        loading = false;
        if (requestedOffset > 0 && packet.revision() != revision) return Update.RESTART;
        if (packet.rows().stream().anyMatch(row -> row.scope() != scope)) return Update.RESTART;
        packet.rows().forEach(row -> staged.put(row.id(), row));
        revision = packet.revision(); nextOffset = packet.offset() + packet.rows().size();
        more = packet.more() && packet.rows().size() == 50;
        if (catchingUp()) return Update.CONTINUE;
        rows.clear(); rows.putAll(staged);
        return Update.UPDATED;
    }

    void failed(UUID token) { if (Objects.equals(queryId, token)) { loading = false; more = false; } }
}
