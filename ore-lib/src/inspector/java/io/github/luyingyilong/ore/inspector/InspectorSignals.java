package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.signal.SignalDiagnostics;
import io.github.luyingyilong.ore.signal.SignalOwner;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Retains a removed selection for cleanup inspection without keeping the control alive. */
final class InspectorSignals {
    private WeakReference<Control> selected = new WeakReference<>(null);
    private String id = "", name = "";
    private Set<Long> ownerIds = Set.of();
    private Map<Long, String> names = Map.of();

    void watch(String id, String name, Control control) {
        this.id = id;
        this.name = name;
        selected = new WeakReference<>(control);
        Set<Long> ids = new HashSet<>();
        ids.add(SignalDiagnostics.ownerId(control));
        if (control instanceof InputControl input) ids.add(SignalDiagnostics.ownerId(input.getWidget()));
        ownerIds = Set.copyOf(ids);
    }

    InspectorSnapshot.SignalPanel snapshot(Map<Long, String> liveNames, boolean attached) {
        if (id.isEmpty()) return InspectorSnapshot.SignalPanel.empty();
        if (attached) names = Map.copyOf(liveNames);
        Control control = selected.get();
        if (control == null) {
            var data = new SignalDiagnostics.Snapshot(java.util.List.of(), java.util.List.of(), SignalDiagnostics.history(ownerIds), SignalDiagnostics.discardedEvents());
            return new InspectorSnapshot.SignalPanel(id, name, "unavailable", data, names);
        }
        SignalOwner[] owners = control instanceof InputControl input
                ? new SignalOwner[]{control, input.getWidget()} : new SignalOwner[]{control};
        return new InspectorSnapshot.SignalPanel(id, name, control.isDisposed() ? "disposed" : attached ? "attached" : "detached",
                SignalDiagnostics.inspect(owners), names);
    }

    void clear() {
        selected.clear();
        id = "";
        name = "";
        ownerIds = Set.of();
        names = Map.of();
    }
}
