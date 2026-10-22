package io.github.luyingyilong.ore.signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The emitter capability stays private in the declaring component.
 */
public final class SignalSource<T> implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(SignalSource.class.getName());
    private final SignalScope lifetime;
    private final String description;
    private final long diagnosticId = SignalDiagnostics.nextId();
    private final String name;
    private final String parameterType;
    private final int arity;
    private final ArrayList<Subscription<T>> subscriptions = new ArrayList<>();
    private final Signal<T> view = new Signal<>() {
        @Override
        public Connection connect(SignalOwner owner, Consumer<? super T> slot) {
            return connect(owner, "", slot);
        }

        @Override
        public Connection connect(SignalOwner owner, String label, Consumer<? super T> slot) {
            lifetime.requireOpen();
            if (closed) throw new IllegalStateException("Signal is closed: " + description);
            SignalScope receiver = Objects.requireNonNull(owner).getSignalScope();
            receiver.requireOpen();
            receiver.describe(owner);
            Subscription<T> subscription = new Subscription<>(SignalSource.this, receiver, Objects.requireNonNull(label), Objects.requireNonNull(slot));
            subscriptions.add(subscription);
            receiver.attach(subscription);
            if (SignalDiagnostics.tracingEnabled()) SignalDiagnostics.connected(subscription.diagnosticSnapshot(false));
            return subscription;
        }

        @Override
        public int connectionCount() {
            lifetime.checkThread();
            return subscriptions.size();
        }
    };
    private boolean closed;

    public SignalSource(SignalOwner owner, String name) {
        this(owner, name, "T");
    }

    /**
     * Diagnostic type name only; the generic Signal<T> still enforces slot types.
     */
    public SignalSource(SignalOwner owner, String name, String parameterType) {
        this(owner, name, 1, parameterType);
    }

    SignalSource(SignalOwner owner, String name, int arity, String parameterType) {
        lifetime = Objects.requireNonNull(owner).getSignalScope();
        this.name = Objects.requireNonNull(name);
        this.arity = arity;
        this.parameterType = Objects.requireNonNull(parameterType);
        lifetime.describe(owner);
        description = owner.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(owner))
                + "." + Objects.requireNonNull(name);
        lifetime.own(this);
        lifetime.register(this);
    }

    SignalDiagnostics.SignalInfo diagnosticSnapshot() {
        lifetime.checkThread();
        List<SignalDiagnostics.ConnectionInfo> connections = subscriptions.stream().map(subscription -> subscription.diagnosticSnapshot(false)).toList();
        return SignalDiagnostics.signalInfo(diagnosticId, lifetime.diagnosticOwner(), name, arity, parameterType, closed, connections);
    }

    public Signal<T> signal() {
        return view;
    }

    public void emit(T value) {
        lifetime.checkThread();
        if (closed || lifetime.isClosed() || lifetime.isSilent()) return;
        try (var guard = DispatchGuard.enter()) {
            for (Subscription<T> subscription : new ArrayList<>(subscriptions)) {
                if (!subscription.isConnected()) continue;
                try {
                    subscription.slot.accept(value);
                } catch (RuntimeException error) {
                    LOG.log(System.Logger.Level.ERROR, "Ore signal slot failed: " + description, error);
                }
            }
        }
    }

    @Override
    public void close() {
        close(SignalDiagnostics.Reason.SOURCE_CLOSED);
    }

    void close(SignalDiagnostics.Reason reason) {
        lifetime.checkThread();
        if (closed) return;
        closed = true;
        for (Subscription<T> subscription : new ArrayList<>(subscriptions)) subscription.disconnect(reason);
        lifetime.release(this);
        SignalDiagnostics.closed(SignalDiagnostics.Kind.SIGNAL_CLOSED, lifetime.diagnosticOwner(), name, reason, subscriptions.size());
    }

    static final class Subscription<T> implements Connection {
        private final Thread thread = Thread.currentThread();
        private final long id = SignalDiagnostics.nextId();
        private final String label;
        private final String subscriptionSource = SignalDiagnostics.subscriptionSource();
        private final long connectedAt = System.currentTimeMillis();
        private SignalSource<T> source;
        private SignalScope receiver;
        private Consumer<? super T> slot;

        Subscription(SignalSource<T> source, SignalScope receiver, String label, Consumer<? super T> slot) {
            this.source = source;
            this.receiver = receiver;
            this.slot = slot;
            this.label = label;
        }

        SignalDiagnostics.ConnectionInfo diagnosticSnapshot(boolean duplicate) {
            return new SignalDiagnostics.ConnectionInfo(id, source.diagnosticId, source.lifetime.diagnosticOwner(), source.name,
                    receiver.diagnosticOwner(), label, subscriptionSource, connectedAt, duplicate);
        }

        private void checkThread() {
            if (Thread.currentThread() != thread)
                throw new IllegalStateException("Connection access from another thread");
        }

        @Override
        public boolean isConnected() {
            checkThread();
            return source != null;
        }

        @Override
        public void disconnect() {
            disconnect(SignalDiagnostics.Reason.MANUAL);
        }

        void disconnect(SignalDiagnostics.Reason reason) {
            checkThread();
            if (source == null) return;
            SignalDiagnostics.ConnectionInfo info = SignalDiagnostics.tracingEnabled() ? diagnosticSnapshot(false) : null;
            source.subscriptions.remove(this);
            receiver.detach(this);
            int remaining = source.subscriptions.size();
            source = null;
            receiver = null;
            slot = null;
            if (info != null) SignalDiagnostics.disconnected(info, reason, remaining);
        }
    }
}
