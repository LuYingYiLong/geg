package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.data.Tree;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.form.FieldKey;
import io.github.luyingyilong.ore.control.form.Form;
import io.github.luyingyilong.ore.control.form.FormFields;
import io.github.luyingyilong.ore.control.form.FormItem;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.input.Checkbox;
import io.github.luyingyilong.ore.control.input.Dropdown;
import io.github.luyingyilong.ore.control.input.Segmented;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Padding;
import io.github.luyingyilong.ore.control.layout.Panel;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.layout.ScrollContent;
import io.github.luyingyilong.ore.control.layout.Splitter;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.control.input.Input;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.input.NumberInput;
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
    private final Box properties = new Box().setGap(5);
    private final InspectorSignalPanel signalPanel = new InspectorSignalPanel();
    private final InspectorThemePanel themePanel = new InspectorThemePanel();
    private final Segmented views = new Segmented(List.of(tr("properties"), tr("signals"), tr("themes")));
    private final Label title = new Label().setHero(true), crumbs = new Label().setWrap(Label.Wrap.CHARACTER);
    private final Label lifecycleStatus = new Label().setWrap(Label.Wrap.CHARACTER).setThemeTypeVariation("LabelSecondary");
    private final Label message = new Label().setWrap(Label.Wrap.CHARACTER);
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
        search.setPlaceholder(tr("search"));
        search.setMaxLength(128);
        targets.itemActivated().connect(targets, id -> backend.execute(new InspectorCommand(InspectorCommand.Operation.TARGET, id, -1, "", "", false)));
        targets.setMinimum(110, 22);
        targets.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        Box tools = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(3);
        tools.addChild(pick);
        tools.addChild(undo);
        tools.addChild(redo);
        tools.addChild(button("restore_all", () -> action(InspectorCommand.Operation.RESET_ALL)));
        tools.addChild(targets);
        tools.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        Box flags = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(6);
        flags.addChild(flag("layout", true));
        flags.addChild(flag("text", true));
        flags.addChild(flag("all", false));
        flags.setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        Scroll treeScroll = new Scroll().setContent(tree);
        treeScroll.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Box left = new Box().setGap(4);
        left.addChild(new InputControl(search));
        left.addChild(treeScroll);
        left.setMinimum(110, 0);
        properties.setMinimum(0, 0);
        Scroll propertyScroll = new Scroll()
                .setContent(new PropertyViewport(properties))
                .setHorizontal(Scroll.BarMode.DISABLED);
        propertyScroll.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Scroll signalScroll = new Scroll().setContent(new PropertyViewport(signalPanel.body())).setHorizontal(Scroll.BarMode.DISABLED);
        signalScroll.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        signalScroll.setVisible(false);
        Scroll themeScroll=new Scroll().setContent(new PropertyViewport(themePanel.body())).setHorizontal(Scroll.BarMode.DISABLED);
        themeScroll.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        themeScroll.setVisible(false);
        views.setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        views.setDebugName("Inspector views");
        views.selectionChanged().connect(views, index -> {
            propertyScroll.setVisible(index == 0);
            signalScroll.setVisible(index == 1);
            themeScroll.setVisible(index == 2);
            if(index == 2) themePanel.update(snapshot.themes());
            if (index == 1) signalPanel.update(snapshot.signals());
        });
        Box right = new Box().setGap(4);
        right.addChild(views);
        right.addChild(lifecycleStatus);
        right.addChild(propertyScroll);
        right.addChild(signalScroll);
        right.addChild(themeScroll);
        right.setMinimum(190, 0);
        Splitter split = new Splitter(left, right).setRatio(0.34);
        split.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Box root = new Box().setGap(4);
        root.addChild(tools);
        root.addChild(flags);
        root.addChild(split);
        root.addChild(message);
        message.setMinimum(0, 16).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        window = Window
                .nativeWindow(WindowOptions.defaults("Ore Inspector"))
                .setInspectable(false)
                .setContent(new Panel(OreTheme.PANEL)
                        .setChild(new Padding(Padding.Insets.all(6))
                                .setChild(root)));
        window.setDebugName("Ore Inspector");
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
        tree.setSearch(search.getText());
        for (Editor editor : List.copyOf(editors.values())) editor.poll();
        InspectorSnapshot next = backend.snapshot();
        if (sequence == next.sequence()) return;
        snapshot = next;
        var info = next.lifecycle();
        lifecycleStatus.setText(info == null ? Component.empty() : tr("lifecycle_status", info.state().name(),
                info.insideTree(), info.ready(), info.suspended(), info.reason(), info.requestSource().lines().findFirst().orElse("—")));
        lifecycleStatus.setVisible(info != null);
        if (views.getSelected() == 1) signalPanel.update(next.signals());
        if (views.getSelected() == 2) themePanel.update(next.themes());
        sequence = next.sequence();
        targets.setOptions(next.targets().stream().map(t -> new Dropdown.Option<>(t.id(), Component.literal(t.name() + (t.active() ? "" : " [inactive]")))).toList());
        tree.setItems(next.nodes().stream().map(n -> new Tree.Item<>(n.id(), n.parent(), Component.literal(n.name()
                + (n.total() == null ? "" : " [" + n.instances() + "/" + n.total() + "]")), n.id())).toList());
        tree.setSelectedNoSignal(next.selected());
        undo.setDisabled(!next.canUndo());
        redo.setDisabled(!next.canRedo());
        pick.setThemeTypeVariation(next.picking() ? "PrimaryButton" : "SecondaryButton");
        message.setText(next.error().isEmpty() ? tr(next.waiting() ? "waiting" : "session_only") : Component.literal(next.error()))
                .setThemeOverride(OreTheme.FONT_COLOR, next.error().isEmpty() ? OreTheme.TEXT_SECONDARY : 0xFFF46D6D);
        var node = next.nodes().stream().filter(n -> n.id().equals(next.selected())).findFirst().orElse(null);
        String schema = node == null ? "" : node.properties().stream().map(p -> p.name() + p.kind() + p.readonly()).toList().toString();
        if (!selected.equals(next.selected()) || !signature.equals(schema)) {
            selected = next.selected();
            signature = schema;
            rebuild(node);
        }
        if (node != null) {
            title.setText(Component.literal(node.name()));
            crumbs.setText(Component.literal(breadcrumbs(node) + "\n" + node.bounds() + (node.hiddenReason().isEmpty() ? "" : "\n" + node.hiddenReason())));
            for (var property : node.properties()) {
                if (property.readonly() && readonlyLabels.containsKey(property.name()))
                    readonlyLabels.get(property.name()).setText(Component.literal(property.name() + ": " + property.effective()));
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
        window.getFocusManager().forgetSubtree(properties);
        // These labels are reused; only discarded editors should be destroyed.
        properties.removeChild(title);
        properties.removeChild(crumbs);
        for (Control child : properties.getChildren()) { if (child.getParent() != null) child.getParent().removeChild(child); child.queueFree(); }
        editors.clear();
        readonlyLabels.clear();
        if (node == null) {
            properties.addChild(new Label(tr("select_node")).setWrap(Label.Wrap.WORD));
            return;
        }
        properties.addChild(title);
        properties.addChild(crumbs);
        properties.addChild(button("restore_node", () -> {
            backend.execute(new InspectorCommand(InspectorCommand.Operation.RESET_NODE, selected, -1, "", "", false));
            synchronizeEditors();
        }).setDisabled(node.internal()));
        String group = "";
        for (var property : node.properties()) {
            if (!group.equals(property.group())) {
                group = property.group();
                properties.addChild(new Label(tr("group." + group)).setHero(true));
            }
            if (property.readonly()) {
                Label readonly = new Label(Component.literal(property.name() + ": " + property.effective())).setWrap(Label.Wrap.CHARACTER).setThemeTypeVariation("LabelSecondary");
                readonlyLabels.put(property.name(), readonly);
                properties.addChild(readonly);
            } else {
                Editor editor = new Editor(property);
                editors.put(property.index(), editor);
                properties.addChild(editor.body);
            }
        }
    }

    private final class Editor {
        private final Box body = new Box().setGap(2);
        private final Label base = new Label().setWrap(Label.Wrap.CHARACTER).setThemeTypeVariation("LabelSecondary");
        private final Label validation = new Label().setWrap(Label.Wrap.CHARACTER).setThemeTypeVariation("LabelError");
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
            validation.setVisible(false);
            if (property.kind().equals("boolean")) {
                Checkbox check = Signals.configure(new Checkbox(Component.literal(property.name())).setCheckedNoSignal(Boolean.TRUE.equals(property.effective())), connected -> connected.toggled().connect(connected, value -> {
                            if (!refreshing) apply(String.valueOf(value));
                        }));
                checkbox = check;
                form.addItem(new FormItem<>(new FieldKey<>(property.name(), Boolean.class), Component.empty(), FormFields.checkbox(check)));
            } else if (property.kind().equals("enum")) {
                Dropdown<String> choices = new Dropdown<>(String.valueOf(property.effective()), 6);
                choices.setOptions(property.options().stream().map(option -> new Dropdown.Option<>(option, Component.literal(option))).toList());
                dropdown = choices;
                choices.itemActivated().connect(choices, value -> {
                    if (!refreshing) apply(value);
                });
                form.addItem(new FormItem<>(new FieldKey<>(property.name(), String.class), Component.literal(property.name()), FormFields.dropdown(String.class, choices)));
            } else {
                boolean integer = property.kind().equals("number") && !property.name().equals("modulate")
                        && property.effective() instanceof Integer;
                input = integer ? new NumberInput(0).setMinimumValue(Integer.MIN_VALUE).setMaximumValue(Integer.MAX_VALUE) : new Input();
                Signals.configure(input, connected -> { connected.setSubmitOnEnter(true); connected.submitted().connect(connected, submittedText48 -> {
                    poll();
                }); });
                input.setMaxLength(property.name().equals("text") ? 2048 : 256);
                input.value(new brachy.modularui.value.StringValue(String.valueOf(property.effective())));
                input.autoUpdateOnChange(true);
                input.setTextNoSignal(String.valueOf(property.effective()));
                observedText = input.getText();
                if (input instanceof NumberInput number)
                    form.addItem(new FormItem<>(new FieldKey<>(property.name(), java.math.BigDecimal.class), Component.literal(property.name()), FormFields.number(number)));
                else form.addItem(new FormItem<>(new FieldKey<>(property.name(), String.class), Component.literal(property.name()), FormFields.text(input)));
            }
            form.setVisible(true);
            body.addChild(form);
            body.addChild(validation);
            body.addChild(base);
            refresh(property);
        }

        void poll() {
            if (input == null || refreshing || input.getComposing() || Objects.equals(observedText, input.getText()))
                return;
            observedText = input.getText();
            apply(observedText);
        }

        void apply(String value) {
            backend.execute(InspectorCommand.edit(selected, property.index(), value));
            String error = backend.lastError();
            validation.setText(Component.literal(error)).setVisible(!error.isEmpty());
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
            base.setText(tr("base", property.base(), property.overridden() ? " *" : ""));
            refreshing = true;
            try {
                if (checkbox != null) checkbox.setCheckedNoSignal(Boolean.TRUE.equals(property.effective()));
                if (dropdown != null && !Objects.equals(dropdown.getSelected(), String.valueOf(property.effective())))
                    dropdown.setSelectedNoSignal(String.valueOf(property.effective()));
                if (input != null && (force || changed)) {
                    if (force && input.isValid()) input.cancelPreedit();
                    observedText = String.valueOf(property.effective());
                    if (input.getStringValue() != null) input.getStringValue().setStringValue(observedText);
                    input.setTextNoSignal(observedText);
                    if (input instanceof NumberInput number) number.clearError();
                }
                if (force || changed) validation.setVisible(false);
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
            addChild(content);
        }

        @Override
        public io.github.luyingyilong.ore.control.base.Size measureSize(net.minecraft.client.gui.Font font, io.github.luyingyilong.ore.control.base.Size available) {
            return getMinimumSize();
        }

        @Override
        public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
            super.layout(bounds, font);
            height = Math.max(bounds.height(), content.measureHeight(font, bounds.width()));
            layoutChild(content, new Rect(0, 0, bounds.width(), height), font);
        }

        @Override
        public io.github.luyingyilong.ore.control.base.Size getContentExtent() {
            return new io.github.luyingyilong.ore.control.base.Size(getBounds().width(), height);
        }

        @Override
        public void setVisibleRegion(Rect region) {
        }
    }
}
