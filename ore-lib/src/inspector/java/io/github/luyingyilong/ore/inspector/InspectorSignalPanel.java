package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.signal.SignalDiagnostics;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Read-only signal view. It has no emit/disconnect actions and receives immutable data only.
 */
final class InspectorSignalPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static final int VISIBLE_HISTORY_LIMIT = 120;
    private final Box body = new Box().setGap(4);
    private InspectorSnapshot.SignalPanel previous;

    private static Component tr(String key, Object... arguments) {
        return Component.translatable("inspector.ore." + key, arguments);
    }

    Box body() {
        return body;
    }

    void update(InspectorSnapshot.SignalPanel panel) {
        SignalDiagnostics.runWithoutTracing(() -> updateInternal(panel));
    }

    private void updateInternal(InspectorSnapshot.SignalPanel panel) {
        if (Objects.equals(previous, panel)) return;
        previous = panel;
        for (Control child : body.getChildren()) { if (child.getParent() != null) child.getParent().removeChild(child); child.queueFree(); }
        if (panel.state().equals("empty")) {
            add(tr("select_node"), false);
            return;
        }
        add(Component.literal(panel.name()), true);
        add(tr("signals.state." + panel.state()), false);
        var data = panel.data();
        add(tr("signals.outgoing", data.signals().size()), true);
        if (data.signals().isEmpty()) add(tr("signals.none"), false);
        boolean multipleOwners = data.signals().stream().map(signal -> signal.sender().id()).distinct().count() > 1;
        for (var signal : data.signals()) {
            // Preserve Java identifier casing; zero-connection signals need only a single row.
            add(Component.literal((multipleOwners ? owner(panel, signal.sender()) + "." : "")
                            + signal.name() + "(" + signal.parameterType() + ")  #" + signal.id() + " - ")
                    .append(tr(signal.closed() ? "signals.closed_count" : "signals.active_count", signal.connections().size())), false)
                    ;
            for (var connection : signal.connections()) connection(panel, connection, false);
        }
        add(tr("signals.incoming", data.incoming().size()), true);
        if (data.incoming().isEmpty()) add(tr("signals.no_connections"), false);
        for (var connection : data.incoming()) connection(panel, connection, true);
        add(tr("signals.history", data.history().size()), true);
        if (data.history().isEmpty()) add(tr("signals.no_history"), false);
        int start = Math.max(0, data.history().size() - VISIBLE_HISTORY_LIMIT);
        if (start > 0) add(tr("signals.history_limit", VISIBLE_HISTORY_LIMIT), false);
        for (int index = data.history().size() - 1; index >= start; index--) {
            var event = data.history().get(index);
            var line = Component.literal(TIME.format(Instant.ofEpochMilli(event.timestamp())) + "  ")
                    .append(tr("signals.event." + event.kind().name()));
            if (event.connection() != null) {
                var c = event.connection();
                line.append(Component.literal(" #" + c.id() + " " + owner(panel, c.sender()) + "." + c.signalName()
                        + " → " + owner(panel, c.receiver())));
            } else line.append(Component.literal(" " + owner(panel, event.owner())
                    + (event.signalName().isEmpty() ? "" : "." + event.signalName())));
            if (event.reason() != null)
                line.append(Component.literal(" - ")).append(tr("signals.reason." + event.reason().name()));
            if (event.remainingConnections() >= 0)
                line.append(Component.literal(" - ")).append(tr(event.kind() == SignalDiagnostics.Kind.SCOPE_CLOSED
                        ? "signals.remaining_scope" : "signals.remaining_signal", event.remainingConnections()));
            Label historyLine = add(line, false);
            if (event.kind() == SignalDiagnostics.Kind.SCOPE_CLOSED && event.remainingConnections() > 0)
                historyLine.setThemeTypeVariation("LabelError");
        }
        if (data.discardedEvents() > 0) add(tr("signals.discarded", data.discardedEvents()), false);
    }

    private void connection(InspectorSnapshot.SignalPanel panel, SignalDiagnostics.ConnectionInfo connection, boolean incoming) {
        add(Component.literal("#" + connection.id() + " " + (incoming
                ? owner(panel, connection.sender()) + "." + connection.signalName() + " → " : "→ ")
                + owner(panel, connection.receiver())), false);
        if (!connection.label().isEmpty()) add(tr("signals.slot_label", connection.label()), false);
        add(connection.source().isEmpty() ? tr("signals.source_unavailable") : tr("signals.source", connection.source()), false);
        if (connection.possibleDuplicate())
            add(tr("signals.duplicate"), false).setThemeTypeVariation("LabelError");
    }

    private String owner(InspectorSnapshot.SignalPanel panel, SignalDiagnostics.Owner owner) {
        String name = panel.owners().getOrDefault(owner.id(), panel.owners().getOrDefault(owner.lifecycleOwnerId(), owner.description()));
        return name + " #" + owner.id() + (owner.purpose().equals("lifetime") ? "" : " [" + owner.purpose() + "]");
    }

    private Label add(Component text, boolean heading) {
        Label label = new Label(text).setWrap(Label.Wrap.CHARACTER).setHero(heading);
        label.setThemeOverride(OreTheme.FONT_COLOR, heading ? OreTheme.TEXT_WHITE : OreTheme.TEXT_SECONDARY);
        body.addChild(label);
        return label;
    }
}
