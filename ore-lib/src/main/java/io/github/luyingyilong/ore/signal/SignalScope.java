package io.github.luyingyilong.ore.signal;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic, thread-confined lifetime; has no Minecraft dependency. */
public final class SignalScope implements SignalOwner, AutoCloseable {
    private final Thread thread = Thread.currentThread();
    private final Set<Connection> incoming = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayList<AutoCloseable> owned = new ArrayList<>();
    private final ArrayList<WeakReference<SignalSource<?>>> signals = new ArrayList<>();
    private final long diagnosticId = SignalDiagnostics.nextId();
    private long lifecycleOwnerId = diagnosticId;
    private String diagnosticDescription = "SignalScope#" + diagnosticId;
    private String diagnosticPurpose = "lifetime";
    private boolean diagnosticExcluded = SignalDiagnostics.suppressed();
    private boolean closed;
    private SignalScope parent;

    @Override public SignalScope getSignalScope() { return this; }
    public boolean isClosed() { checkThread(); return closed; }

    public void checkThread() {
        if (Thread.currentThread() != thread)
            throw new IllegalStateException("Signal access must run on its owning thread: " + thread.getName());
    }

    void requireOpen() {
        checkThread();
        if (closed) throw new IllegalStateException("Signal lifetime has ended");
    }

    /** Also useful for nested editor and virtual-data binding scopes. */
    public <T extends AutoCloseable> T own(T resource) {
        requireOpen();
        owned.add(Objects.requireNonNull(resource));
        return resource;
    }

    public SignalScope child() {
        return child("child");
    }

    public SignalScope child(String purpose) {
        Objects.requireNonNull(purpose);
        SignalScope child = own(new SignalScope());
        child.parent = this;
        child.lifecycleOwnerId = lifecycleOwnerId;
        child.diagnosticDescription = diagnosticDescription;
        child.diagnosticPurpose = purpose;
        child.diagnosticExcluded = diagnosticExcluded;
        return child;
    }

    void describe(SignalOwner owner) {
        if (owner != this) diagnosticDescription = owner.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(owner));
    }
    void register(SignalSource<?> source) { signals.add(new WeakReference<>(source)); }
    SignalDiagnostics.Owner diagnosticOwner() {
        return new SignalDiagnostics.Owner(diagnosticId, lifecycleOwnerId, diagnosticDescription, diagnosticPurpose, closed, diagnosticExcluded);
    }
    List<SignalSource<?>> diagnosticSources() {
        signals.removeIf(reference -> reference.get() == null);
        ArrayList<SignalSource<?>> result = new ArrayList<>();
        for (var reference : signals) { SignalSource<?> source = reference.get(); if (source != null) result.add(source); }
        return result;
    }
    List<Connection> diagnosticIncoming() { return List.copyOf(incoming); }
    List<SignalScope> diagnosticChildren() {
        return owned.stream().filter(resource -> resource instanceof SignalScope).map(resource -> (SignalScope) resource).toList();
    }
    void release(AutoCloseable resource) { checkThread(); owned.remove(resource); }
    void attach(Connection connection) { requireOpen(); incoming.add(connection); }
    void detach(Connection connection) { checkThread(); incoming.remove(connection); }

    @Override public void close() {
        close(SignalDiagnostics.Reason.SCOPE_CLOSED);
    }

    public void close(SignalDiagnostics.Reason reason) {
        checkThread();
        Objects.requireNonNull(reason);
        if (closed) return;
        closed = true;
        if (parent != null) { parent.release(this); parent = null; }
        for (Connection connection : new ArrayList<>(incoming)) {
            if (connection instanceof SignalSource.Subscription<?> subscription)
                subscription.disconnect(reason == SignalDiagnostics.Reason.SCOPE_CLOSED
                        ? SignalDiagnostics.Reason.RECEIVER_CLOSED : reason);
            else connection.disconnect();
        }
        incoming.clear();
        ArrayList<AutoCloseable> resources = new ArrayList<>(owned);
        owned.clear();
        for (int i = resources.size() - 1; i >= 0; i--) {
            try {
                AutoCloseable resource = resources.get(i);
                if (resource instanceof SignalSource<?> source)
                    source.close(reason == SignalDiagnostics.Reason.SCOPE_CLOSED ? SignalDiagnostics.Reason.SOURCE_CLOSED : reason);
                else if (resource instanceof SignalScope scope) scope.close(reason);
                else resource.close();
            }
            catch (Exception error) {
                System.getLogger(SignalScope.class.getName()).log(System.Logger.Level.ERROR, "Signal resource cleanup failed", error);
            }
        }
        int remaining = incoming.size();
        for (SignalSource<?> source : diagnosticSources()) remaining += source.signal().connectionCount();
        SignalDiagnostics.closed(SignalDiagnostics.Kind.SCOPE_CLOSED, diagnosticOwner(), "", reason, remaining);
    }
}
