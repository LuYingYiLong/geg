package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.ControlIdScope;
import io.github.luyingyilong.ore.control.base.Offset;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.signal.SignalOwner;
import io.github.luyingyilong.ore.signal.SignalScope;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-thread document lifetime. The stable mount never owns or closes its host.
 */
public final class UiDocument implements AutoCloseable {
    private static final AtomicLong IDS = new AtomicLong();
    private final String scopeId = "xml-" + IDS.incrementAndGet();
    private final UiLoader loader;
    private final ResourceLocation resource;
    private final UiBindings bindings;
    private final SignalScope lifetime;
    private final UiMount mount = new UiMount();
    private final List<Saved> geometryInitializers = new ArrayList<>();
    private SignalScope connections;
    private Live root;
    private long version = 1, requested;
    private CompletableFuture<UiBlueprint> pending;
    private boolean resetPending, enabled = true, closed;
    private Status status = Status.READY;
    private String error = "";
    UiDocument(UiLoader loader, UiBlueprint blueprint, UiBindings bindings, SignalOwner owner) {
        this.loader = loader;
        this.resource = blueprint.resource();
        this.bindings = bindings;
        lifetime = owner.getSignalScope().child("XML " + resource);
        List<Control> created = new ArrayList<>();
        try {
            root = build(blueprint.root(), created, false);
            mount.setContent(root.control);
            connections = bind(root, version);
            mount.afterLayout = this::applyGeometryInitializers;
            queueGeometryInitializers(root);
            publish();
            lifetime.own(this::close);
            mount.getSignalScope().own(this::close);
        } catch (RuntimeException | Error failure) {
            created.forEach(control -> control.queueFree("XML load failed"));
            lifetime.close();
            mount.queueFree("XML load failed");
            throw failure;
        }
    }

    private static void apply(UiConfiguration.Write write, Control control, UiBlueprint.Node node) {
        try {
            write.apply(control);
        } catch (RuntimeException failure) {
            throw new UiException(node.source(), "Property " + write.key() + ": " + failure.getMessage(), failure);
        }
    }

    private static boolean interactiveMode(UiConfiguration.Write write, Control control) {
        return write.key().equals("mode") && write.value() == io.github.luyingyilong.ore.control.inventory.Inventory.Mode.CONTAINER
                && (control instanceof io.github.luyingyilong.ore.control.inventory.Inventory || control instanceof io.github.luyingyilong.ore.control.inventory.Slot);
    }

    private static boolean containerMode(Control control) {
        return control instanceof io.github.luyingyilong.ore.control.inventory.Inventory inventory && inventory.getMode() == io.github.luyingyilong.ore.control.inventory.Inventory.Mode.CONTAINER
                || control instanceof io.github.luyingyilong.ore.control.inventory.Slot slot && slot.getMode() == io.github.luyingyilong.ore.control.inventory.Inventory.Mode.CONTAINER;
    }

    private static void setMenuMode(Control control, io.github.luyingyilong.ore.control.inventory.Inventory.Mode mode) {
        if (control instanceof io.github.luyingyilong.ore.control.inventory.Inventory inventory)
            inventory.setMode(mode);
        else ((io.github.luyingyilong.ore.control.inventory.Slot) control).setMode(mode);
    }

    private static void match(Live next, Map<String, Live> identities, Map<Live, Live> reuse, Set<Control> retained, boolean blocked) {
        Live old = identities.get(next.control.getId());
        boolean compatible = !blocked && !next.control.getId().isEmpty() && old != null && old.node.type().equals(next.node.type())
                && old.type == next.type && old.constructionKey.equals(next.constructionKey);
        if (compatible) {
            reuse.put(next, old);
            retained.add(old.control);
        }
        boolean blockChildren = blocked || next.type.structure() == UiTypeRegistry.Structure.FORM_ITEM && !compatible;
        for (Live child : next.children) match(child, identities, reuse, retained, blockChildren);
    }

    private static Set<Control> ordinaryTree(Control root) {
        Set<Control> result = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Control> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Control node = pending.removeFirst();
            if (result.add(node)) pending.addAll(node.getChildren());
        }
        return result;
    }

    private static void restoreFocus(Window window, Control focus, Set<Control> retained) {
        if (window != null && focus != null && retained.contains(focus) && focus.isAlive() && focus.isInputAccessible())
            window.getFocusManager().restoreFocus(focus, window.getContext().getMui());
    }

    private static List<Saved> runtimeState(Control control) {
        List<Saved> result = new ArrayList<>();
        Object target = UiConfiguration.editor(control);
        // Never set editor text: the actual editor instance contains drafts, undo, caret and selection.
        for (var property : UiPropertySchema.getProperties(target.getClass()).values())
            if (property.initialState() && !property.name().equals("text")
                    && !(control instanceof io.github.luyingyilong.ore.control.data.Tree<?> && property.name().equals("selected"))) {
                UiConfiguration.Accessor a = new UiConfiguration.Accessor(c -> property.get(UiConfiguration.editor(c)), (c, value) -> property.set(UiConfiguration.editor(c), value));
                try {
                    result.add(new Saved(a, control, a.read().apply(control)));
                } catch (IllegalStateException inactiveMode) {
                    if (!(control instanceof io.github.luyingyilong.ore.control.input.Slider)) throw inactiveMode;
                }
            }
        if (control instanceof Scroll scroll) result.add(new Saved(new UiConfiguration.Accessor(c -> null,
                (c, value) -> {
                    Offset offset = (Offset) value;
                    ((Scroll) c).setScroll(offset.x(), offset.y());
                }), control, new Offset(scroll.getScrollX(), scroll.getScrollY())));
        if (control instanceof io.github.luyingyilong.ore.control.data.Tree<?> tree) {
            result.add(new Saved(new UiConfiguration.Accessor(c -> null, (c, value) -> {
                var targetTree = (io.github.luyingyilong.ore.control.data.Tree<?>) c;
                String key = (String) value;
                targetTree.setSelectedNoSignal(targetTree.getItems().stream().anyMatch(item -> item.key().equals(key)) ? key : "");
            }), control, tree.getSelected()));
            result.add(new Saved(new UiConfiguration.Accessor(c -> null, (c, value) -> {
                var targetTree = (io.github.luyingyilong.ore.control.data.Tree<?>) c;
                Set<String> keys = new HashSet<>();
                targetTree.getItems().forEach(item -> keys.add(item.key()));
                targetTree.setExpandedKeysNoSignal(((Collection<?>) value).stream().map(String.class::cast).filter(keys::contains).toList());
            }), control, tree.getExpandedKeys()));
        }
        if (control instanceof io.github.luyingyilong.ore.control.data.Table<?> table)
            result.add(new Saved(new UiConfiguration.Accessor(c -> null, (c, value) -> ((io.github.luyingyilong.ore.control.data.Table<?>) c).setScroll((int) value)), control, table.getScroll()));
        return result;
    }

    private static List<Live> flatten(Live root) {
        List<Live> nodes = new ArrayList<>();
        collect(root, nodes);
        return nodes;
    }

    private static void collect(Live node, List<Live> nodes) {
        nodes.add(node);
        node.children.forEach(child -> collect(child, nodes));
    }

    private static <T> List<T> reversed(List<T> values) {
        List<T> reversed = new ArrayList<>(values);
        Collections.reverse(reversed);
        return reversed;
    }

    public Control getRoot() {
        return mount;
    }

    public ResourceLocation getResource() {
        return resource;
    }

    public Status getStatus() {
        checkThread();
        return status;
    }

    public String getError() {
        checkThread();
        return error;
    }

    public long getVersion() {
        checkThread();
        return version;
    }

    public String getScopeId() {
        return scopeId;
    }

    public boolean isClosed() {
        return closed || !mount.isAlive();
    }

    private void checkThread() {
        lifetime.checkThread();
    }

    public void reload() {
        request(false);
    }

    /**
     * Reapply XML initial values deliberately, rather than overwriting edits on ordinary reload.
     */
    public void reset() {
        request(true);
    }

    private void request(boolean reset) {
        checkThread();
        if (isClosed()) return;
        requested++;
        if (pending != null) pending.cancel(false);
        pending = loader.readAsync(resource);
        resetPending = reset;
        status = Status.LOADING;
    }

    private Live build(UiBlueprint.Node node, List<Control> created, boolean staging) {
        UiTypeRegistry.Type type = loader.registry().require(node);
        List<Live> children = new ArrayList<>();
        for (var child : UiTypeRegistry.controls(type, node)) children.add(build(child, created, staging));
        Control control;
        try {
            control = Objects.requireNonNull(type.factory().create(node, children.stream().map(child -> child.control).toList(), bindings), "Factory returned null");
        } catch (RuntimeException failure) {
            throw new UiException(node.source(), "Factory " + node.type() + ": " + failure.getMessage(), failure);
        }
        if (control.getGeometryParent() != null || !control.isAlive())
            throw new UiException(node.source(), "Factory must return a fresh unattached control");
        created.add(control);
        if (type.structure() == UiTypeRegistry.Structure.OPAQUE) ControlIdScope.isolateChildren(control);
        try {
            control.setId(node.get("id", ""));
            List<UiConfiguration.Write> writes = UiConfiguration.parse(loader, type, control, node, bindings);
            for (var write : writes)
                if (!write.initial() && !(staging && interactiveMode(write, control))) apply(write, control, node);
            UiStructure.apply(loader.registry(), type, control, node, children.stream().map(child -> child.control).toList());
            for (var write : writes) if (write.initial()) apply(write, control, node);
            Live live = new Live(node, type, control, List.copyOf(children), writes);
            validateBindings(live);
            return live;
        } catch (UiException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new UiException(node.source(), failure.getMessage(), failure);
        }
    }

    private List<Binding> validateBindings(Live live) {
        Set<String> unique = new HashSet<>();
        List<Binding> result = new ArrayList<>();
        for (var child : live.node.children())
            if (child.type().equals("connect")) {
                UiTypeRegistry.attributes(child, Set.of("signal", "handler"));
                if (!child.children().isEmpty())
                    throw new UiException(child.source(), "Connection cannot contain children");
                String signal = child.require("signal"), handler = child.require("handler");
                if (!unique.add(signal + "\u0000" + handler))
                    throw new UiException(child.source(), "Duplicate signal connection " + signal + " -> " + handler);
                try {
                    Object target = UiConfiguration.editor(live.control);
                    var accessor = UiBindings.SignalAccessor.find(target, signal, live.type.payloads());
                    result.add(new Binding(target, accessor, bindings.require(handler, accessor.payload()), child.source() + " " + signal + " -> " + handler));
                } catch (RuntimeException failure) {
                    throw new UiException(child.source(), "Signal " + signal + ": " + failure.getMessage(), failure);
                }
            }
        return result;
    }

    private SignalScope bind(Live live, long bindingVersion) {
        List<Binding> validated = new ArrayList<>();
        for (Live node : flatten(live)) validated.addAll(validateBindings(node));
        SignalScope scope = lifetime.child("XML bindings v" + bindingVersion);
        try {
            for (Binding binding : validated)
                binding.accessor.connect(binding.target, scope, binding.label, binding.handler, () -> enabled && !closed);
            return scope;
        } catch (RuntimeException failure) {
            scope.close();
            throw failure;
        }
    }

    void applyPending() {
        checkThread();
        if (isClosed()) {
            close();
            return;
        }
        if (pending == null || !pending.isDone()) return;
        if (!pending.isCompletedExceptionally() && flatten(root).stream().anyMatch(node -> node.control.isAlive() && belongs(node.control) && node.control instanceof InputControl input && input.getWidget().getComposing())) {
            status = Status.WAITING_FOR_IME;
            return;
        }
        CompletableFuture<UiBlueprint> candidate = pending;
        pending = null;
        long request = requested;
        boolean reset = resetPending;
        try {
            UiBlueprint blueprint = candidate.join();
            if (request == requested && !closed) commit(blueprint, reset);
            status = pending == null ? Status.READY : Status.LOADING;
            error = "";
            org.slf4j.LoggerFactory.getLogger(UiDocument.class).info("XML reloaded: {} v{} ({})", resource, version, scopeId);
        } catch (RuntimeException failure) {
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            error = cause.getMessage() == null ? cause.toString() : cause.getMessage();
            status = Status.ERROR;
            org.slf4j.LoggerFactory.getLogger(UiDocument.class).warn("XML reload retained the previous document: {}", error);
        }
        publish();
    }

    private void commit(UiBlueprint blueprint, boolean reset) {
        List<Control> created = new ArrayList<>();
        UiMount staging = new UiMount();
        Live candidate = null;
        SignalScope[] nextConnections = {null};
        List<Saved> rollback = new ArrayList<>();
        List<Saved> rollbackRuntime = new ArrayList<>();
        Map<Control, io.github.luyingyilong.ore.control.inventory.Inventory.Mode> menuModes = new IdentityHashMap<>();
        Set<Control> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Live, Live> reuse = new IdentityHashMap<>();
        List<SignalScope.Silence> silences = new ArrayList<>();
        Control focus = null;
        Window window = mount.getWindowContext().getOwner();
        List<Live> previousNodes = flatten(root).stream().filter(node -> belongs(node.control)).toList();
        Set<Control> previousTree = ordinaryTree(mount);
        try {
            candidate = build(blueprint.root(), created, true);
            staging.setContent(candidate.control); // validates all document IDs before touching the old tree
            Map<String, Live> identities = new HashMap<>();
            for (Live old : previousNodes)
                if (old.control.isAlive() && !old.control.getSignalScope().isClosed()
                        && !old.control.getId().isEmpty() && mount.findControlFromId(old.control.getId()) == old.control)
                    identities.put(old.control.getId(), old);
            match(candidate, identities, reuse, retained, false);
            if (window != null) {
                focus = window.getFocusManager().getFocused();
                boolean ours = false;
                for (Control node = focus; node != null; node = node.getParent()) if (node == mount) ours = true;
                if (!ours) focus = null;
            }
            for (Live node : previousNodes)
                if (node.control.isAlive() && node.control instanceof InputControl input) input.suspendHostFocus();
            if (window != null) {
                window.cancelCaptures(mount, io.github.luyingyilong.ore.event.CancelEvent.Reason.REBOUND);
                window.getContext().getPopups().cancelFrom(mount);
            }
            mount.cancelInteractions();
            for (Live node : previousNodes) {
                if (!node.control.isAlive() || node.control.getSignalScope().isClosed()) continue;
                silences.add(node.control.getSignalScope().silence());
                if (node.control instanceof InputControl input)
                    silences.add(input.getWidget().getSignalScope().silence());
            }
            for (Live old : previousNodes) rollbackRuntime.addAll(runtimeState(old.control));
            enabled = false;
            Live finalCandidate = candidate;
            List<Saved> runtime = new ArrayList<>();
            for (Live next : flatten(candidate)) {
                Live old = reuse.get(next);
                runtime.addAll(runtimeState(old == null ? next.control : old.control));
            }
            mount.transact(() -> {
                for (Live old : previousNodes)
                    if (!retained.contains(old.control) && containerMode(old.control)) {
                        menuModes.put(old.control, io.github.luyingyilong.ore.control.inventory.Inventory.Mode.CONTAINER);
                        setMenuMode(old.control, io.github.luyingyilong.ore.control.inventory.Inventory.Mode.READ_ONLY);
                    }
                for (Live old : reversed(previousNodes))
                    if (old.control.isAlive()) UiStructure.clear(old.type, old.control);
                for (Live next : reversed(flatten(finalCandidate))) UiStructure.clear(next.type, next.control);
                staging.setContent(null);
                mount.setContent(null);
                for (Live next : flatten(finalCandidate)) {
                    Live old = reuse.get(next);
                    if (old == null) continue;
                    Control clone = next.control;
                    next.control = old.control;
                    next.defaults.putAll(old.defaults);
                    Set<String> keys = new HashSet<>();
                    next.writes.forEach(write -> keys.add(write.key()));
                    for (var previous : old.writes)
                        if (!previous.initial() && !keys.contains(previous.key())) {
                            rollback.add(new Saved(previous.accessor(), old.control, previous.read(old.control)));
                            previous.accessor().write().accept(old.control, old.defaults.get(previous.key()));
                        }
                    for (var write : next.writes)
                        if (!write.initial()) {
                            rollback.add(new Saved(write.accessor(), old.control, write.read(old.control)));
                            apply(write, old.control, next.node);
                        }
                    clone.queueFree("XML candidate reused an existing node");
                }
                for (Live next : reversed(flatten(finalCandidate))) {
                    UiStructure.apply(loader.registry(), next.type, next.control, next.node, next.children.stream().map(child -> child.control).toList());
                }
                for (Live next : flatten(finalCandidate))
                    if (!reuse.containsKey(next))
                        for (var write : next.writes)
                            if (interactiveMode(write, next.control)) apply(write, next.control, next.node);
                for (Saved saved : runtime) {
                    try {
                        saved.restore();
                    } catch (IllegalArgumentException |
                             IllegalStateException ignored) { /* Existing widget fallback handles removed choices/modes. */ }
                }
                if (reset) for (Live next : flatten(finalCandidate))
                    for (var write : next.writes) if (write.initial()) apply(write, next.control, next.node);
                mount.setContent(finalCandidate.control);
                nextConnections[0] = bind(finalCandidate, version + 1);
            });
            connections.close();
            connections = nextConnections[0];
            nextConnections[0] = null;
            for (Live old : previousNodes)
                if (!retained.contains(old.control)) old.control.queueFree("XML node replaced or removed");
            Set<Control> currentTree = ordinaryTree(mount);
            for (Control abandoned : previousTree)
                if (abandoned.isAlive() && !currentTree.contains(abandoned)
                        && abandoned.getParent() == null && abandoned.getGeometryParent() == null)
                    abandoned.queueFree("XML structure discarded a Java-added branch");
            root = candidate;
            if (reset) geometryInitializers.clear();
            else geometryInitializers.removeIf(initializer -> !retained.contains(initializer.control()));
            for (Live next : flatten(candidate))
                if (reset || !reuse.containsKey(next)) queueGeometryInitializers(next, false);
            version++;
            restoreFocus(window, focus, retained);
        } catch (RuntimeException | Error failure) {
            for (Control fresh : created)
                if (!retained.contains(fresh) && containerMode(fresh))
                    try {
                        setMenuMode(fresh, io.github.luyingyilong.ore.control.inventory.Inventory.Mode.READ_ONLY);
                    } catch (RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
            for (Saved saved : reversed(rollback))
                try {
                    saved.restore();
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            menuModes.forEach((control, mode) -> {
                try {
                    setMenuMode(control, mode);
                } catch (RuntimeException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            });
            for (Saved saved : rollbackRuntime)
                try {
                    saved.restore();
                } catch (RuntimeException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            if (nextConnections[0] != null) nextConnections[0].close();
            for (Control createdNode : created)
                if (!retained.contains(createdNode)) createdNode.queueFree("XML candidate rejected");
            restoreFocus(window, focus, retained);
            throw failure;
        } finally {
            for (var silence : reversed(silences)) silence.close();
            enabled = true;
            staging.queueFree("XML staging complete");
        }
    }

    private boolean belongs(Control control) {
        for (Control node = control; node != null; node = node.getParent()) if (node == mount) return true;
        return false;
    }

    private void queueGeometryInitializers(Live node) {
        queueGeometryInitializers(node, true);
    }

    private void queueGeometryInitializers(Live node, boolean recursive) {
        for (var write : node.writes)
            if (write.key().equals("scroll"))
                geometryInitializers.add(new Saved(write.accessor(), node.control, write.value()));
        if (recursive) for (Live child : node.children) queueGeometryInitializers(child);
    }

    private void applyGeometryInitializers() {
        for (Saved initializer : List.copyOf(geometryInitializers)) {
            Control control = initializer.control();
            if (!control.isAlive()) {
                geometryInitializers.remove(initializer);
                continue;
            }
            // An inactive Tabs page has not measured its Scroll extent yet.
            if (control.getLayoutDirty() || control.getBounds().width() == 0 || control.getBounds().height() == 0)
                continue;
            initializer.restore();
            geometryInitializers.remove(initializer);
        }
    }

    private void publish() {
        if (root == null) return;
        for (Live node : flatten(root)) {
            Map<String, String> configuration = new LinkedHashMap<>();
            for (var write : node.writes) configuration.put(write.key(), String.valueOf(write.value()));
            var source = node.node.source();
            UiSources.put(node.control, new UiSources.Info(resource.toString(), source.line(), source.column(), scopeId, version, configuration, error));
        }
        UiSources.put(mount, new UiSources.Info(resource.toString(), 1, 1, scopeId, version, Map.of(), error));
    }

    @Override
    public void close() {
        checkThread();
        if (closed) return;
        closed = true;
        enabled = false;
        status = Status.CLOSED;
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
        if (connections != null) connections.close();
        lifetime.close();
        mount.queueFree("XML document closed");
        root = null;
        geometryInitializers.clear();
    }

    public enum Status {READY, LOADING, WAITING_FOR_IME, ERROR, CLOSED}

    private static final class Live {
        final UiBlueprint.Node node;
        final UiTypeRegistry.Type type;
        final String constructionKey;
        final List<Live> children;
        final List<UiConfiguration.Write> writes;
        final Map<String, Object> defaults = new HashMap<>();
        Control control;

        Live(UiBlueprint.Node node, UiTypeRegistry.Type type, Control control, List<Live> children, List<UiConfiguration.Write> writes) {
            this.node = node;
            this.type = type;
            this.control = control;
            this.children = children;
            this.writes = writes;
            constructionKey = type.constructionKey().apply(node);
            for (var write : writes) defaults.put(write.key(), write.defaultValue());
        }
    }

    private record Binding(Object target, UiBindings.SignalAccessor accessor, UiBindings.Handler<?> handler,
                           String label) {
    }

    private record Saved(UiConfiguration.Accessor accessor, Control control, Object value) {
        void restore() {
            accessor.write().accept(control, value);
        }
    }
}
