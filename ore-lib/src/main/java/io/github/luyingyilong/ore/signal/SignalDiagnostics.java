package io.github.luyingyilong.ore.signal;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Optional, pure Java diagnostics. Snapshots/history never retain owners, slots or controls. */
public final class SignalDiagnostics {
    public enum Reason { MANUAL, RECEIVER_CLOSED, SOURCE_CLOSED, OWNER_DISPOSED, BINDING_RESET, SCOPE_CLOSED }
    public enum Kind { CONNECTED, DISCONNECTED, SIGNAL_CLOSED, SCOPE_CLOSED }

    /** The receiver is the explicit lifetime owner, not an inferred lambda target. */
    public record Owner(long id, long lifecycleOwnerId, String description, String purpose, boolean closed, boolean excluded) { }
    public record ConnectionInfo(long id, long signalId, Owner sender, String signalName, Owner receiver,
                                 String label, String source, long connectedAt, boolean possibleDuplicate) { }
    public record SignalInfo(long id, Owner sender, String name, int arity, String parameterType,
                             boolean closed, List<ConnectionInfo> connections) { }
    public record Event(long sequence, long timestamp, Kind kind, Owner owner, ConnectionInfo connection,
                        String signalName, Reason reason, int remainingConnections) {
        public boolean affects(Set<Long> owners) {
            return matches(owner, owners) || connection != null
                    && (matches(connection.sender(), owners) || matches(connection.receiver(), owners));
        }
    }
    public record Snapshot(List<SignalInfo> signals, List<ConnectionInfo> incoming, List<Event> history,
                           long discardedEvents) {
        public static Snapshot empty() { return new Snapshot(List.of(), List.of(), List.of(), 0); }
    }

    private static final AtomicLong IDENTITIES = new AtomicLong();
    private static final int HISTORY_LIMIT = 512;
    private static final ThreadLocal<History> HISTORY = ThreadLocal.withInitial(History::new);
    private static final ThreadLocal<Integer> SUPPRESSED = ThreadLocal.withInitial(() -> 0);
    private static volatile boolean tracingEnabled;

    private SignalDiagnostics() { }

    /** Enable early in a development client to also record subscriptions made before Inspector opens. */
    public static void tracingEnabled(boolean enabled) { tracingEnabled = enabled; }
    public static boolean tracingEnabled() { return tracingEnabled; }
    public static void clearHistory() { HISTORY.remove(); }
    public static long discardedEvents() { return HISTORY.get().discarded; }
    public static TraceGuard suppress(boolean enabled) {
        if (!enabled) return TraceGuard.NONE;
        int depth = SUPPRESSED.get();
        SUPPRESSED.set(depth + 1);
        return new TraceGuard(depth);
    }
    /** Diagnostic UI scopes created here remain excluded, including their eventual disposal. */
    public static <T> T withoutTracing(java.util.function.Supplier<T> factory) {
        try (TraceGuard ignored = suppress(true)) { return factory.get(); }
    }
    public static void runWithoutTracing(Runnable action) { withoutTracing(() -> { action.run(); return null; }); }
    static boolean suppressed() { return SUPPRESSED.get() > 0; }
    static long nextId() { return IDENTITIES.incrementAndGet(); }

    public static long ownerId(SignalOwner owner) {
        owner.signalScope().checkThread();
        return owner.signalScope().diagnosticOwner().id();
    }

    public static Snapshot inspect(SignalOwner... owners) {
        Map<Long, SignalInfo> signals = new LinkedHashMap<>();
        Map<Long, ConnectionInfo> incoming = new LinkedHashMap<>();
        java.util.HashSet<Long> ids = new java.util.HashSet<>();
        java.util.HashSet<Long> visited = new java.util.HashSet<>();
        for (SignalOwner owner : owners) {
            SignalScope scope = owner.signalScope();
            scope.checkThread();
            ids.add(scope.diagnosticOwner().id());
            collect(scope, signals, incoming, visited);
        }
        return new Snapshot(List.copyOf(signals.values()), markDuplicates(incoming.values().stream()
                .sorted(Comparator.comparingLong(ConnectionInfo::id)).toList()), history(ids), HISTORY.get().discarded);
    }

    private static void collect(SignalScope scope, Map<Long, SignalInfo> signals, Map<Long, ConnectionInfo> incoming, Set<Long> visited) {
        if (!visited.add(scope.diagnosticOwner().id())) return;
        for (SignalSource<?> source : scope.diagnosticSources()) {
            SignalInfo info = source.diagnosticSnapshot();
            signals.put(info.id(), info);
        }
        for (Connection connection : scope.diagnosticIncoming())
            if (connection instanceof SignalSource.Subscription<?> subscription) {
                ConnectionInfo info = subscription.diagnosticSnapshot(false);
                incoming.put(info.id(), info);
            }
        for (SignalScope child : scope.diagnosticChildren()) collect(child, signals, incoming, visited);
    }

    /** Can be queried after a control has left the tree, using only its frozen IDs. */
    public static List<Event> history(Set<Long> owners) {
        return HISTORY.get().events.stream().filter(event -> event.affects(owners)).toList();
    }

    private static boolean matches(Owner owner, Set<Long> ids) {
        return owner != null && (ids.contains(owner.id()) || ids.contains(owner.lifecycleOwnerId()));
    }

    static String subscriptionSource() {
        if (!tracingEnabled || suppressed()) return "";
        return StackWalker.getInstance().walk(frames -> frames.filter(frame -> {
            String name = frame.getClassName();
            return !name.equals(SignalDiagnostics.class.getName())
                    && !name.startsWith(SignalSource.class.getName())
                    && !name.startsWith(SignalSource0.class.getName());
        }).findFirst().map(frame -> frame.getClassName() + "." + frame.getMethodName()
                + " (" + frame.getFileName() + ":" + frame.getLineNumber() + ")").orElse(""));
    }

    static SignalInfo signalInfo(long id, Owner sender, String name, int arity, String type, boolean closed,
                                 List<ConnectionInfo> connections) {
        return new SignalInfo(id, sender, name, arity, type, closed, markDuplicates(connections));
    }

    private static List<ConnectionInfo> markDuplicates(List<ConnectionInfo> connections) {
        Map<String, Integer> groups = new HashMap<>();
        for (ConnectionInfo connection : connections)
            if (!connection.source().isEmpty()) groups.merge(duplicateKey(connection), 1, Integer::sum);
        return connections.stream().map(c -> new ConnectionInfo(
                c.id(), c.signalId(), c.sender(), c.signalName(), c.receiver(), c.label(), c.source(), c.connectedAt(),
                groups.getOrDefault(duplicateKey(c), 0) > 1)).toList();
    }

    private static String duplicateKey(ConnectionInfo c) {
        return c.signalId() + ":" + c.receiver().id() + ":" + c.label() + ":" + c.source();
    }

    static void connected(ConnectionInfo connection) {
        record(Kind.CONNECTED, connection.receiver(), connection, connection.signalName(), null, -1);
    }

    static void disconnected(ConnectionInfo connection, Reason reason, int remaining) {
        record(Kind.DISCONNECTED, connection.receiver(), connection, connection.signalName(), reason, remaining);
    }

    static void closed(Kind kind, Owner owner, String name, Reason reason, int remaining) {
        record(kind, owner, null, name, reason, remaining);
    }

    private static void record(Kind kind, Owner owner, ConnectionInfo connection, String name, Reason reason, int remaining) {
        if (!tracingEnabled || suppressed() || owner.excluded()
                || connection != null && (connection.sender().excluded() || connection.receiver().excluded())) return;
        History history = HISTORY.get();
        if (history.events.size() == HISTORY_LIMIT) { history.events.removeFirst(); history.discarded++; }
        history.events.addLast(new Event(++history.sequence, System.currentTimeMillis(), kind, owner, connection, name, reason, remaining));
    }

    private static final class History {
        final ArrayDeque<Event> events = new ArrayDeque<>();
        long sequence, discarded;
    }

    public static final class TraceGuard implements AutoCloseable {
        private static final TraceGuard NONE = new TraceGuard(-1);
        private final int previous;
        private final Thread thread;
        private boolean closed;
        private TraceGuard(int previous) { this.previous = previous; this.thread = previous < 0 ? null : Thread.currentThread(); }
        @Override public void close() {
            if (previous < 0) return;
            if (Thread.currentThread() != thread) throw new IllegalStateException("Signal trace scope closed on another thread");
            if (closed) return;
            closed = true;
            if (previous == 0) SUPPRESSED.remove(); else SUPPRESSED.set(previous);
        }
    }
}
