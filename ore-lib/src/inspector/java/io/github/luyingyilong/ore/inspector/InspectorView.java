package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.input.NumberInput;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.window.WindowOptions;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Ore-only Inspector frontend. Business controls are never exposed to the view.
 */
public final class InspectorView {
    private final InspectorBackend backend;
    private final Window window;
    private final Tree<String> tree = new Tree<>();
    private final Box properties = new Box().gap(5);
    private final InspectorSignalPanel signalPanel = new InspectorSignalPanel();
    private final Segmented views = new Segmented(List.of(tr("properties"), tr("signals")));
    private final Label title = new Label().hero(true), crumbs = new Label().wrap(Label.Wrap.CHARACTER);
    private final Label lifecycleStatus = new Label().wrap(Label.Wrap.CHARACTER).color(OreTheme.TEXT_SECONDARY);
    private final Label message = new Label().wrap(Label.Wrap.CHARACTER);
    private final Input search = new Input();
    private final Dropdown<String> targets = new Dropdown<>("", 6);
    private final Button undo = button("undo", () -> action(InspectorCommand.Operation.UNDO));
    private final Button redo = button("redo", () -> action(InspectorCommand.Operation.REDO));
    private final Button pick = button("pick", () -> action(InspectorCommand.Operation.PICK));
    private final Map<Integer, Editor> editors = new LinkedHashMap<>();
    private final Map<String, Label> readonlyLabels = new LinkedHashMap<>();
    private String selected = "", signature = "";
    private long sequence = -1;
    private InspectorSnapshot snapshot = InspectorSnapshot.empty();

    public InspectorView(InspectorBackend backend) {
        this.backend = backend;
        tree.selectionChanged().connect(tree, id -> backend.execute(InspectorCommand.select(id)));
        search.placeholder(tr("search"));
        search.setMaxLength(128);
        targets.itemActivated().connect(targets, id -> backend.execute(new InspectorCommand(InspectorCommand.Operation.TARGET, id, -1, "", "", false)));
        targets.minimum(110, 22);
        targets.sizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        Box tools = new Box()
                .orientation(Box.Orientation.HORIZONTAL)
                .gap(3)
                .add(pick)
                .add(undo)
                .add(redo)
                .add(button("restore_all", () -> action(InspectorCommand.Operation.RESET_ALL)))
                .add(targets);
        tools.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        Box flags = new Box()
                .orientation(Box.Orientation.HORIZONTAL)
                .gap(6)
                .add(flag("layout", true))
                .add(flag("text", true))
                .add(flag("all", false));
        flags.sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        Scroll treeScroll = new Scroll().content(tree);
        treeScroll.sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Box left = new Box()
                .gap(4)
                .add(new InputControl(search))
                .add(treeScroll);
        left.minimum(110, 0);
        properties.minimum(0, 0);
        Scroll propertyScroll = new Scroll()
                .content(new PropertyViewport(properties))
                .horizontal(Scroll.BarMode.DISABLED);
        propertyScroll.sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Scroll signalScroll = new Scroll().content(new PropertyViewport(signalPanel.body())).horizontal(Scroll.BarMode.DISABLED);
        signalScroll.sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        signalScroll.visible(false);
        views.sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        views.debugName("Inspector views");
        views.selectionChanged().connect(views, index -> {
            propertyScroll.visible(index == 0);
            signalScroll.visible(index == 1);
            if (index == 1) signalPanel.update(snapshot.signals());
        });
        Box right = new Box().gap(4).add(views).add(lifecycleStatus).add(propertyScroll).add(signalScroll);
        right.minimum(190, 0);
        Splitter split = new Splitter(left, right).ratio(0.34);
        split.sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Box root = new Box()
                .gap(4)
                .add(tools)
                .add(flags)
                .add(split)
                .add(message);
        message.minimum(0, 16).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        window = Window
                .nativeWindow(WindowOptions.defaults("Ore Inspector"))
                .inspectable(false)
                .content(new Panel(OreTheme.PANEL)
                        .child(new Padding(Padding.Insets.all(6))
                                .child(root)));
        window.debugName("Ore Inspector");
        window.closed().connect(window, OreInspector::stop);
    }

    private static Button button(String key, Runnable action) {
        return Signals.configure(new Button(tr(key)), connected -> connected.pressed().connect(connected, action));
    }

    private static Component tr(String key, Object... arguments) {
        return Component.translatable("inspector.ore." + key, arguments);
    }

    public Window window() {
        return window;
    }

    public void open() {
        window.open();
    }

    private Checkbox flag(String key, boolean checked) {
        return Signals.configure(new Checkbox(tr(key)).setCheckedNoSignal(checked), connected -> connected.toggled().connect(connected, value -> backend.execute(InspectorCommand.flag(key, value))));
    }

    private void action(InspectorCommand.Operation operation) {
        backend.execute(InspectorCommand.action(operation));
        if (operation == InspectorCommand.Operation.UNDO || operation == InspectorCommand.Operation.REDO
                || operation == InspectorCommand.Operation.RESET_ALL) synchronizeEditors();
    }

    private void synchronizeEditors() {
        backend.snapshot().nodes().stream().filter(node -> node.id().equals(selected)).findFirst().ifPresent(node -> {
            for (var property : node.properties()) {
                Editor editor = editors.get(property.index());
                if (editor != null) editor.refresh(property, true);
            }
        });
    }

    public void update() {
        io.github.luyingyilong.ore.signal.SignalDiagnostics.runWithoutTracing(this::updateInternal);
    }

    private void updateInternal() {
        tree.search(search.getText());
        for (Editor editor : List.copyOf(editors.values())) editor.poll();
        InspectorSnapshot next = backend.snapshot();
        if (sequence == next.sequence()) return;
        snapshot = next;
        var info = next.lifecycle();
        lifecycleStatus.text(info == null ? Component.empty() : tr("lifecycle_status", info.state().name(),
                info.insideTree(), info.ready(), info.suspended(), info.reason(), info.requestSource().lines().findFirst().orElse("—")));
        lifecycleStatus.visible(info != null);
        if (views.selected() == 1) signalPanel.update(next.signals());
        sequence = next.sequence();
        targets.setOptions(next.targets().stream().map(t -> new Dropdown.Option<>(t.id(), Component.literal(t.name() + (t.active() ? "" : " [inactive]")))).toList());
        tree.items(next.nodes().stream().map(n -> new Tree.Item<>(n.id(), n.parent(), Component.literal(n.name()
                + (n.total() == null ? "" : " [" + n.instances() + "/" + n.total() + "]")), n.id())).toList());
        tree.setSelectedNoSignal(next.selected());
        undo.disabled(!next.canUndo());
        redo.disabled(!next.canRedo());
        pick.type(next.picking() ? Button.Type.PRIMARY : Button.Type.SECONDARY);
        message.text(next.error().isEmpty() ? tr(next.waiting() ? "waiting" : "session_only") : Component.literal(next.error()))
                .color(next.error().isEmpty() ? OreTheme.TEXT_SECONDARY : 0xFFF46D6D);
        var node = next.nodes().stream().filter(n -> n.id().equals(next.selected())).findFirst().orElse(null);
        String schema = node == null ? "" : node.properties().stream().map(p -> p.name() + p.kind() + p.readonly()).toList().toString();
        if (!selected.equals(next.selected()) || !signature.equals(schema)) {
            selected = next.selected();
            signature = schema;
            rebuild(node);
        }
        if (node != null) {
            title.text(Component.literal(node.name()));
            crumbs.text(Component.literal(breadcrumbs(node) + "\n" + node.bounds() + (node.hiddenReason().isEmpty() ? "" : "\n" + node.hiddenReason())));
            for (var property : node.properties()) {
                if (property.readonly() && readonlyLabels.containsKey(property.name()))
                    readonlyLabels.get(property.name()).text(Component.literal(property.name() + ": " + property.effective()));
                else if (editors.containsKey(property.index())) editors.get(property.index()).refresh(property);
            }
        }
    }

    private String breadcrumbs(InspectorSnapshot.Node node) {
        Map<String, InspectorSnapshot.Node> byId = new HashMap<>();
        snapshot.nodes().forEach(n -> byId.put(n.id(), n));
        List<String> names = new ArrayList<>();
        for (var n = node; n != null; n = byId.get(n.parent())) names.add(n.name());
        Collections.reverse(names);
        return String.join(" / ", names);
    }

    private void rebuild(InspectorSnapshot.Node node) {
        window.focusManager().forgetSubtree(properties);
        // These labels are reused; only discarded editors should be destroyed.
        properties.removeChild(title);
        properties.removeChild(crumbs);
        for (Control child : properties.children()) { if (child.parent() != null) child.parent().removeChild(child); child.queueFree(); }
        editors.clear();
        readonlyLabels.clear();
        if (node == null) {
            properties.add(new Label(tr("select_node")).wrap(Label.Wrap.WORD));
            return;
        }
        properties.add(title).add(crumbs);
        properties.add(button("restore_node", () -> {
            backend.execute(new InspectorCommand(InspectorCommand.Operation.RESET_NODE, selected, -1, "", "", false));
            synchronizeEditors();
        }).disabled(node.internal()));
        String group = "";
        for (var property : node.properties()) {
            if (!group.equals(property.group())) {
                group = property.group();
                properties.add(new Label(tr("group." + group)).hero(true));
            }
            if (property.readonly()) {
                Label readonly = new Label(Component.literal(property.name() + ": " + property.effective())).wrap(Label.Wrap.CHARACTER).color(OreTheme.TEXT_SECONDARY);
                readonlyLabels.put(property.name(), readonly);
                properties.add(readonly);
            } else {
                Editor editor = new Editor(property);
                editors.put(property.index(), editor);
                properties.add(editor.body);
            }
        }
    }

    private final class Editor {
        private final Box body = new Box().gap(2);
        private final Label base = new Label().wrap(Label.Wrap.CHARACTER).color(OreTheme.TEXT_SECONDARY);
        private final Label validation = new Label().wrap(Label.Wrap.CHARACTER).color(OreTheme.TEXT_ERROR);
        private InspectorSnapshot.Property property;
        private Input input;
        private Checkbox checkbox;
        private Dropdown<String> dropdown;
        private boolean refreshing;
        private String observedText;
        private Object effectiveValue;

        Editor(InspectorSnapshot.Property property) {
            this.property = property;
            effectiveValue = property.effective();
            Form form = new Form();
            validation.visible(false);
            if (property.kind().equals("boolean")) {
                Checkbox check = Signals.configure(new Checkbox(Component.literal(property.name())).setCheckedNoSignal(Boolean.TRUE.equals(property.effective())), connected -> connected.toggled().connect(connected, value -> {
                            if (!refreshing) apply(String.valueOf(value));
                        }));
                checkbox = check;
                form.add(new FormItem(property.name(), Component.empty(), check, () -> String.valueOf(check.checked())));
            } else if (property.kind().equals("enum")) {
                Dropdown<String> choices = new Dropdown<>(String.valueOf(property.effective()), 6);
                choices.setOptions(property.options().stream().map(option -> new Dropdown.Option<>(option, Component.literal(option))).toList());
                dropdown = choices;
                choices.itemActivated().connect(choices, value -> {
                    if (!refreshing) apply(value);
                });
                form.add(new FormItem(property.name(), Component.literal(property.name()), choices, () -> choices.selected()));
            } else {
                boolean integer = property.kind().equals("number") && !property.name().equals("color")
                        && property.effective() instanceof Integer;
                input = integer ? new NumberInput(0).minimumValue(Integer.MIN_VALUE).maximumValue(Integer.MAX_VALUE) : new Input();
                Signals.configure(input, connected -> { connected.submitOnEnter(true); connected.submitted().connect(connected, submittedText48 -> {
                    poll();
                }); });
                input.setMaxLength(property.name().equals("text") ? 2048 : 256);
                input.value(new brachy.modularui.value.StringValue(String.valueOf(property.effective())));
                input.autoUpdateOnChange(true);
                input.setTextNoSignal(String.valueOf(property.effective()));
                observedText = input.getText();
                form.add(new FormItem(property.name(), Component.literal(property.name()), input));
            }
            form.setVisible(true);
            body.add(form);
            body.add(validation).add(base);
            refresh(property);
        }

        void poll() {
            if (input == null || refreshing || input.composing() || Objects.equals(observedText, input.getText()))
                return;
            observedText = input.getText();
            apply(observedText);
        }

        void apply(String value) {
            backend.execute(InspectorCommand.edit(selected, property.index(), value));
            String error = backend.lastError();
            validation.text(Component.literal(error)).visible(!error.isEmpty());
            if (error.isEmpty()) {
                // A successful local edit must not normalize the user's draft or move their cursor.
                backend.snapshot().nodes().stream().filter(node -> node.id().equals(selected)).findFirst()
                        .flatMap(node -> node.properties().stream().filter(p -> p.index() == property.index()).findFirst())
                        .ifPresent(p -> effectiveValue = p.effective());
            }
        }

        void refresh(InspectorSnapshot.Property property) {
            refresh(property, false);
        }

        void refresh(InspectorSnapshot.Property property, boolean force) {
            boolean changed = !Objects.equals(effectiveValue, property.effective());
            this.property = property;
            effectiveValue = property.effective();
            base.text(tr("base", property.base(), property.overridden() ? " *" : ""));
            refreshing = true;
            try {
                if (checkbox != null) checkbox.setCheckedNoSignal(Boolean.TRUE.equals(property.effective()));
                if (dropdown != null && !Objects.equals(dropdown.selected(), String.valueOf(property.effective())))
                    dropdown.setSelectedNoSignal(String.valueOf(property.effective()));
                if (input != null && (force || changed)) {
                    if (force && input.isValid()) input.cancelPreedit();
                    observedText = String.valueOf(property.effective());
                    if (input.getStringValue() != null) input.getStringValue().setStringValue(observedText);
                    input.setTextNoSignal(observedText);
                    if (input instanceof NumberInput number) number.clearError();
                }
                if (force || changed) validation.visible(false);
            } finally {
                refreshing = false;
            }
        }
    }

    /**
     * Diagnostic strings wrap to the viewport instead of widening every form/button.
     */
    private final class PropertyViewport extends Container implements ScrollContent {
        private int height;
        private final Box content;

        PropertyViewport(Box content) {
            this.content = content;
            super.addChild(content);
        }

        @Override
        public io.github.luyingyilong.ore.layout.Size measureSize(net.minecraft.client.gui.Font font, io.github.luyingyilong.ore.layout.Size available) {
            return minimumSize();
        }

        @Override
        public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
            super.layout(bounds, font);
            height = Math.max(bounds.height(), content.measureHeight(font, bounds.width()));
            layoutChild(content, new Rect(0, 0, bounds.width(), height), font);
        }

        @Override
        public io.github.luyingyilong.ore.layout.Size contentExtent() {
            return new io.github.luyingyilong.ore.layout.Size(bounds().width(), height);
        }

        @Override
        public void visibleRegion(Rect region) {
        }
    }
}
