package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.signal.SignalDiagnostics;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** Removed/disposed selections are diagnosed without a running native window. */
public final class InspectorSignalsTest {
    public static void main(String[] args) {
        SignalDiagnostics.tracingEnabled(true);
        SignalDiagnostics.clearHistory();
        Box root = new Box();
        Button button = new Button(Component.literal("Purchase"));
        button.debugName("Purchase button");
        Control receiver = new Control();
        root.add(button).add(receiver);
        int[] actions = {0};
        for (int i = 0; i < 2; i++) subscribe(button, receiver, actions);
        InspectorSignals signals = new InspectorSignals();
        long senderId = SignalDiagnostics.ownerId(button), receiverId = SignalDiagnostics.ownerId(receiver);
        Map<Long, String> names = Map.of(senderId, "Purchase button", receiverId, "Order editor");
        signals.watch("test-node", "Purchase button", button);
        var panel = signals.snapshot(names, true);
        check(panel.state().equals("attached"), "Attached state");
        var pressed = panel.data().signals().stream().filter(s -> s.name().equals("pressed")).findFirst().orElseThrow();
        check(pressed.connections().size() == 2 && pressed.connections().stream().allMatch(SignalDiagnostics.ConnectionInfo::possibleDuplicate), "Duplicate connections reach Inspector");
        check(pressed.connections().stream().allMatch(c -> panel.owners().get(c.receiver().id()).equals("Order editor")), "Receiver labels use tree names");

        InspectorSignalPanel view = new InspectorSignalPanel();
        view.update(panel);
        int labels = view.body().children().size();
        Control original = view.body().children().get(0);
        view.update(panel);
        check(view.body().children().size() == labels && view.body().children().get(0) == original, "Unchanged snapshots do not rebuild diagnostic controls");
        check(actions[0] == 0, "Reading and displaying signals cannot activate business callbacks");

        root.removeChild(button);
        var detached = signals.snapshot(Map.of(), false);
        check(detached.state().equals("detached") && !detached.data().signals().stream().allMatch(SignalDiagnostics.SignalInfo::closed), "Detach is distinct from disposal");
        button.queueFree();
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        var disposed = signals.snapshot(Map.of(), false);
        check(disposed.state().equals("disposed"), "Disposed selection is retained as diagnostic metadata");
        check(disposed.data().signals().stream().allMatch(s -> s.closed() && s.connections().isEmpty()), "Closed signals have zero live connections");
        check(disposed.data().history().stream().anyMatch(e -> e.kind() == SignalDiagnostics.Kind.SCOPE_CLOSED && e.remainingConnections() == 0), "Disposed selection retains cleanup proof");
        view.update(disposed);
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        check(original.isDisposed(), "Rebuilt read-only view disposes discarded labels");
        check(actions[0] == 0, "Cleanup preview never fires slots");

        // Inspector Tree rows can be instantiated during layout rather than update().
        Control[] lazy = {null};
        Window diagnosticWindow = Window.embedded().inspectable(false).content(new Control() {
            @Override public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
                super.layout(bounds, font);
                if (lazy[0] == null) {
                    Button row = new Button(Component.literal("Lazy diagnostic row"));
                    row.pressed().connect(receiver, () -> actions[0]++);
                    lazy[0] = row;
                }
            }
        });
        diagnosticWindow.layoutViewport(300, 100, null);
        check(SignalDiagnostics.inspect(lazy[0]).history().isEmpty(), "Rows constructed during diagnostic layout do not enter history");
        lazy[0].queueFree();
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        check(SignalDiagnostics.inspect(lazy[0]).history().isEmpty(), "Later disposal of lazy diagnostic rows remains excluded");
        diagnosticWindow.queueFree();
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();

        signals.watch("receiver", "Order editor", receiver);
        check(signals.snapshot(names, true).data().history().stream().anyMatch(e -> e.kind() == SignalDiagnostics.Kind.DISCONNECTED), "Receiver can inspect source disposal too");
        signals.clear();
        check(signals.snapshot(Map.of(), false).state().equals("empty"), "Stop clears selection");
        root.queueFree();
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        view.body().queueFree(); io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        System.out.println("ORE_INSPECTOR_SIGNALS_TEST_PASS");
    }

    private static void subscribe(Button button, Control receiver, int[] actions) {
        button.pressed().connect(receiver, "purchase", () -> actions[0]++);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
