package io.github.luyingyilong.ore.signal;

import java.lang.reflect.RecordComponent;
import java.util.Collection;

/** Pure Java diagnostics contracts; does not require a Minecraft client or Inspector. */
public final class SignalDiagnosticsTest {
    public static void main(String[] args) throws Exception {
        SignalDiagnostics.tracingEnabled(false);
        Owner early = new Owner(), receiver = new Owner();
        SignalSource0 pressed = new SignalSource0(early, "pressed");
        SignalSource<String> text = new SignalSource<>(early, "textChanged", "String");
        Connection beforeTracing = pressed.signal().connect(receiver, () -> { });
        var initial = SignalDiagnostics.inspect(early);
        check(initial.signals().size() == 2, "Unconnected and pre-tracing signals are discoverable");
        check(initial.signals().get(0).arity() == 0 && initial.signals().get(0).parameterType().isEmpty(), "Signal0 has zero parameters, not Void");
        check(initial.signals().get(1).arity() == 1 && initial.signals().get(1).parameterType().equals("String"), "Explicit generic diagnostic type");
        check(initial.signals().get(0).connections().get(0).source().isEmpty(), "Do not invent missing call sites");
        beforeTracing.disconnect();

        SignalDiagnostics.tracingEnabled(true);
        SignalDiagnostics.clearHistory();
        int[] received = {0};
        for (int i = 0; i < 2; i++) duplicate(pressed, receiver, received);
        var connected = SignalDiagnostics.inspect(early);
        var connections = connected.signals().get(0).connections();
        check(connections.size() == 2 && connections.stream().allMatch(SignalDiagnostics.ConnectionInfo::possibleDuplicate), "Group same signal, owner, label and source as possible duplicates");
        check(connections.stream().allMatch(c -> c.source().contains("SignalDiagnosticsTest.duplicate") && c.source().contains("SignalDiagnosticsTest.java:")), "Record application call site rather than Signal0 adapter");
        check(connections.stream().allMatch(c -> c.receiver().id() == SignalDiagnostics.ownerId(receiver)), "Explicit lifetime owner identity");
        check(SignalDiagnostics.inspect(receiver).incoming().size() == 2
                && SignalDiagnostics.inspect(receiver).incoming().stream().allMatch(SignalDiagnostics.ConnectionInfo::possibleDuplicate), "Reverse subscriptions also flag possible duplicates");
        pressed.emit();
        check(received[0] == 2, "Diagnostics do not suppress legitimate multiple slots");
        receiver.signalScope().close(SignalDiagnostics.Reason.OWNER_DISPOSED);
        check(pressed.signal().connectionCount() == 0, "Receiver destruction still clears live slots");
        var disposed = SignalDiagnostics.inspect(receiver);
        check(disposed.history().stream().anyMatch(e -> e.kind() == SignalDiagnostics.Kind.SCOPE_CLOSED
                && e.reason() == SignalDiagnostics.Reason.OWNER_DISPOSED && e.remainingConnections() == 0), "Retain successful cleanup evidence");
        check(disposed.history().stream().filter(e -> e.kind() == SignalDiagnostics.Kind.DISCONNECTED).count() == 2, "One disconnect record per connection");

        SignalScope binding = early.signalScope().child("binding");
        text.signal().connect(binding, "bind row", ignored -> { });
        check(SignalDiagnostics.inspect(early).incoming().size() == 1, "Binding subscriptions are attributed to their control");
        binding.close(SignalDiagnostics.Reason.BINDING_RESET);
        check(SignalDiagnostics.inspect(early).history().stream().anyMatch(e -> e.reason() == SignalDiagnostics.Reason.BINDING_RESET), "Binding cleanup has a distinct reason");
        check(!early.signalScope().isClosed(), "Binding reset does not dispose the control");
        early.signalScope().close(SignalDiagnostics.Reason.OWNER_DISPOSED);
        var closedSource = SignalDiagnostics.inspect(early);
        check(closedSource.signals().size() == 2 && closedSource.signals().stream().allMatch(s -> s.closed() && s.connections().isEmpty()), "Closed declarations remain visible while their owner exists");
        objectFree(closedSource);

        Owner observing = new Owner();
        Owner inspector = SignalDiagnostics.withoutTracing(Owner::new);
        SignalSource0 diagnosticButton = new SignalSource0(inspector, "inspect");
        diagnosticButton.signal().connect(observing, () -> { });
        inspector.signalScope().close();
        check(SignalDiagnostics.inspect(observing).history().isEmpty(), "Inspector subscriptions and later disposal do not pollute target history");
        observing.signalScope().close();

        SignalDiagnostics.clearHistory();
        Owner bounded = new Owner();
        SignalSource0 source = new SignalSource0(bounded, "bounded");
        for (int i = 0; i < 300; i++) source.signal().connect(bounded, () -> { }).disconnect();
        var history = SignalDiagnostics.inspect(bounded);
        check(history.history().size() == 512 && history.discardedEvents() == 88, "Bounded metadata history");
        check(history.history().get(0).sequence() < history.history().get(511).sequence(), "Stable history ordering");
        bounded.signalScope().close();
        SignalDiagnostics.tracingEnabled(false);
        SignalDiagnostics.clearHistory();
        System.out.println("ORE_SIGNAL_DIAGNOSTICS_TEST_PASS");
    }

    private static void duplicate(SignalSource0 source, Owner receiver, int[] count) {
        source.signal().connect(receiver, "save", () -> count[0]++);
    }

    private static void objectFree(Object value) throws Exception {
        if (value == null) return;
        check(!(value instanceof SignalOwner || value instanceof Connection || value instanceof SignalSource<?>
                || value instanceof Runnable || value instanceof java.util.function.Consumer<?>), "Snapshots must not retain live UI/slot references");
        if (value instanceof Collection<?> values) for (Object element : values) objectFree(element);
        else if (value.getClass().isRecord())
            for (RecordComponent component : value.getClass().getRecordComponents()) objectFree(component.getAccessor().invoke(value));
    }

    private static final class Owner implements SignalOwner {
        private final SignalScope scope = new SignalScope();
        @Override public SignalScope signalScope() { return scope; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
