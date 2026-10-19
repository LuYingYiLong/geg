package io.github.luyingyilong.ore.inspector;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Drawer;
import io.github.luyingyilong.ore.Table;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Vector4f;

import java.util.*;

/**
 * All methods except the immutable transport handoff run on the Minecraft client thread.
 */
public final class InspectorBackend implements DebugBridge.Backend {
    private final Map<Window, Long> targets = new WeakHashMap<>();
    private final DebugOverrides overrides = new DebugOverrides();
    private final InspectorLifecycles lifecycleHistory = new InspectorLifecycles();
    private LifecycleInfo selectedLifecycle;
    private final InspectorSignals signalInspector = new InspectorSignals();
    private final Map<Control, Long> identities = new WeakHashMap<>();
    private final Map<Control, Long> lifecycles = new WeakHashMap<>();
    private final Map<Control, Binding> bindings = new WeakHashMap<>();
    private final Map<Window, Long> sessions = new WeakHashMap<>();
    private final Map<Window, Set<Control>> rootTargets = new WeakHashMap<>();
    private final Map<Control, Rect> slots = new WeakHashMap<>();
    private final IdentityHashMap<Control, Geometry> painted = new IdentityHashMap<>();
    private final IdentityHashMap<Control, List<TextGeometry>> texts = new IdentityHashMap<>();
    private final IdentityHashMap<Control, List<Primitive>> primitives = new IdentityHashMap<>();
    private final IdentityHashMap<Control, Part> parts = new IdentityHashMap<>();
    private final ArrayDeque<Rect> clips = new ArrayDeque<>();
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Set<Window> swallowed = Collections.newSetFromMap(new IdentityHashMap<>());
    private InspectorSnapshot snapshot = InspectorSnapshot.empty();
    private Window root;
    private long nextIdentity, nextSession, sequence, lastSend, lastFrame;
    private int originX, originY, drawOrder;
    private String selected = "", hovered = "", error = "";
    private boolean picking, layout = true, text = true, all, collecting, bypass;
    public InspectorBackend() {
        io.github.luyingyilong.ore.signal.SignalDiagnostics.tracingEnabled(true);
    }

    private static void horizontal(GuiGraphics g, Rect r, float y, int color) {
        int at = (int) Math.floor(y);
        g.fill(r.x(), at, r.right(), at + 1, color);
    }

    private static void outline(GuiGraphics g, Rect r, int color) {
        if (r == null || r.width() <= 0 || r.height() <= 0) return;
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, color);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), color);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom(), color);
        g.fill(r.right() - 1, r.y(), r.right(), r.bottom(), color);
    }

    private static Rect intersect(Rect clip, Rect area) {
        if (clip == null) return area;
        int x = Math.max(clip.x(), area.x()), y = Math.max(clip.y(), area.y());
        return new Rect(x, y, Math.max(0, Math.min(clip.right(), area.right()) - x), Math.max(0, Math.min(clip.bottom(), area.bottom()) - y));
    }

    public InspectorSnapshot snapshot() {
        return snapshot;
    }

    private void clientThread() {
        if (!Minecraft.getInstance().isSameThread())
            throw new IllegalStateException("Inspector accessed outside client thread");
    }

    @Override
    public Object value(Control c, String key, Object base) {
        return bypass ? base : overrides.value(c, key, base);
    }

    @Override
    public void slot(Control c, Rect slot) {
        if (c.windowContext().owner() == null || c.windowContext().owner().inspectable()) slots.put(c, slot);
    }

    @Override
    public void binding(Control c, Object key) {
        Binding previous = bindings.get(c);
        if (previous == null || !Objects.equals(previous.key, key)) {
            bindings.put(c, new Binding(key, previous == null ? 1 : previous.generation + 1));
            overrides.removeTree(c);
        }
    }

    @Override
    public void removed(Control c) {
        lifecycles.merge(c, 1L, Long::sum);
        overrides.removeTree(c);
    }

    @Override public void lifecycle(Control control, LifecycleInfo info) {
        if (control.windowContext().owner() != null && !control.windowContext().owner().inspectable()) return;
        Node node = nodes.values().stream().filter(n -> n.control == control).findFirst().orElse(null);
        if (node != null && node.id.equals(selected) || selectedLifecycle != null && selectedLifecycle.id() == info.id()) selectedLifecycle = info;
        lifecycleHistory.record(control, info);
        if (info.state() == LifecycleState.FREED) {
            overrides.removeTree(control);
            nodes.values().removeIf(n -> n.control == control);
            painted.remove(control); texts.remove(control); primitives.remove(control); parts.remove(control);
            rootTargets.values().forEach(set -> set.remove(control));
        }
    }

    private String id(Control c) {
        long epoch = 1;
        for (Control n = c; n != null; n = n.parent()) {
            Binding b = bindings.get(n);
            if (b != null) epoch = 31 * epoch + b.generation;
            epoch = 31 * epoch + lifecycles.getOrDefault(n, 0L);
        }
        return sessions.computeIfAbsent(root, k -> ++nextSession) + ":" + identities.computeIfAbsent(c, k -> ++nextIdentity) + ":" + epoch;
    }

    @Override
    public void internal(Control owner, String name, Control part) {
        if (collecting) parts.put(part, new Part(owner, name));
    }

    @Override
    public void begin(Window root, ModularGuiContext context, int x, int y) {
        clientThread();
        if (!root.inspectable()) {
            collecting = false;
            return;
        }
        targets.put(root, System.nanoTime());
        if (this.root == null) this.root = root;
        if (this.root != root) {
            collecting = false;
            return;
        }
        originX = x;
        originY = y;
        lastFrame = System.nanoTime();
        collecting = true;
        painted.clear();
        texts.clear();
        primitives.clear();
        parts.clear();
        clips.clear();
        drawOrder = 0;
    }

    @Override
    public void paint(Control c, ModularGuiContext context, int x, int y) {
        if (!collecting) return;
        float depth = context.getGraphics().pose().last().pose().m32(), treeDepth = 0;
        for (Control node = c; node != null; node = node.parent()) treeDepth += node.contentDepth();
        Rect area = c.screenBounds(x, y);
        var matrix = context.getGraphics().pose().last().pose();
        Vector4f start = matrix.transform(new Vector4f(area.x(), area.y(), 0, 1));
        Vector4f end = matrix.transform(new Vector4f(area.right(), area.bottom(), 0, 1));
        area = new Rect((int) Math.floor(start.x), (int) Math.floor(start.y),
                Math.max(0, (int) Math.ceil(end.x - start.x)), Math.max(0, (int) Math.ceil(end.y - start.y)));
        painted.put(c, new Geometry(area, clips.peek(), Math.max(depth, treeDepth), ++drawOrder));
    }

    @Override
    public void clip(Rect rect) {
        if (collecting && rect != null) clips.push(intersect(clips.peek(), rect));
    }

    @Override
    public void unclip() {
        if (collecting && !clips.isEmpty()) clips.pop();
    }

    @Override
    public void text(Control c, GuiGraphics graphics, Rect area, float x, float y, float width, float height,
                     float rowTop, float baseline, float inkTop, float inkBottom, float fontSize, String fonts, float scroll) {
        if (!collecting) return;
        var matrix = graphics.pose().last().pose();
        Vector4f point = matrix.transform(new Vector4f(x, y, 0, 1));
        Vector4f base = matrix.transform(new Vector4f(x, baseline, 0, 1));
        Vector4f top = matrix.transform(new Vector4f(x, inkTop, 0, 1)), bottom = matrix.transform(new Vector4f(x, inkBottom, 0, 1));
        // GUI pose transforms include the native editor's local translation; the camera projection is separate.
        Vector4f row = matrix.transform(new Vector4f(x, rowTop, 0, 1));
        Rect line = new Rect((int) Math.floor(point.x), (int) Math.floor(row.y), Math.max(0, (int) Math.ceil(width)), Math.max(1, (int) Math.ceil(height)));
        texts.computeIfAbsent(c, k -> new ArrayList<>()).add(new TextGeometry(line, base.y, point.y, top.y, bottom.y, fontSize, fonts, scroll, clips.peek()));
        if (!painted.containsKey(c)) painted.put(c, new Geometry(line, clips.peek(), matrix.m32(), ++drawOrder));
    }

    @Override
    public void primitive(Control c, String kind, GuiGraphics graphics, Rect area) {
        if (!collecting) return;
        var p = graphics.pose().last().pose().transform(new Vector4f(area.x(), area.y(), 0, 1));
        primitives.computeIfAbsent(c, k -> new ArrayList<>()).add(new Primitive(kind,
                new Rect((int) p.x, (int) p.y, area.width(), area.height()), clips.peek()));
    }

    @Override
    public boolean picking() {
        return picking;
    }

    @Override
    public boolean picking(Window root) {
        return picking && root == this.root;
    }

    @Override
    public boolean pointer(Window window, int x, int y, int button, boolean pressed) {
        if (!pressed && swallowed.remove(window)) return true;
        if (!picking || window != root) return false;
        if (pressed) swallowed.add(window);
        if (button == 0 && pressed) pickClick(x, y);
        return true;
    }

    @Override
    public boolean key(Window window, int key, int action) {
        if (!picking || window != root) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) cancelPick();
        return true;
    }

    public void cancelPick() {
        picking = false;
        hovered = "";
    }

    public void pickClick(int x, int y) {
        if (picking) {
            selected = pick(x, y);
            picking = false;
        }
    }

    @Override
    public void end(Window root, ModularGuiContext context, int x, int y) {
        if (!collecting || this.root != root) return;
        rebuildNodes(root);
        if (picking) hovered = pick(context.getAbsMouseX(), context.getAbsMouseY());
        drawOverlay(context.getGraphics());
        if (System.nanoTime() - lastSend >= 100_000_000L) publish();
        collecting = false;
    }

    private void rebuildNodes(Window window) {
        nodes.clear();
        collectOwnedParts(window, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        walk(window, "", true, "");
        // Internal presentation nodes have no parent in the business tree and cannot be edited directly.
        for (var e : parts.entrySet().stream().sorted(java.util.Comparator.comparing(e -> internalId(e.getKey(), new HashSet<>()))).toList()) {
            Part p = e.getValue();
            Node owner = nodes.get(internalId(p.owner, new HashSet<>()));
            if (owner == null || !e.getKey().isAlive()) continue;
            Control c = e.getKey();
            String key = owner.id + "/" + p.name;
            Geometry g = painted.get(c);
            nodes.put(key, new Node(key, owner.id, c, "[internal] " + p.name, owner.visible, g == null ? "Not painted this frame" : "", g, true));
        }
        Set<Control> live = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node n : nodes.values()) {
            live.add(n.control);
            for (DebugProperty p : properties(n.control)) live.add(p.target());
        }
        Set<Control> old = rootTargets.put(window, live);
        if (old != null) {
            old.removeAll(live);
            overrides.forget(old);
        }
    }

    private void collectOwnedParts(Control owner, Set<Control> seen) {
        if (!owner.isAlive() || !seen.add(owner)) return;
        for (Control child : owner.children()) if (child.parent() == owner) collectOwnedParts(child, seen);
        for (var entry : owner.ownedParts().entrySet()) {
            Control part = entry.getValue();
            if (part.parent() == null) parts.putIfAbsent(part, new Part(owner, entry.getKey()));
            collectOwnedParts(part, seen);
        }
    }

    private String internalId(Control c, Set<Control> seen) {
        if (!seen.add(c)) return "";
        Part part = parts.get(c);
        return part == null ? id(c) : internalId(part.owner, seen) + "/" + part.name;
    }

    private void walk(Control c, String parent, boolean ancestorVisible, String reason) {
        if (!c.isAlive()) return;
        boolean visible = ancestorVisible && c.visible();
        String hidden = visible ? "" : !ancestorVisible ? reason : c.getClass().getSimpleName() + ".visible=false";
        String key = id(c);
        nodes.put(key, new Node(key, parent, c, c.debugName().isEmpty() ? c.getClass().getSimpleName() : c.debugName(), visible, hidden, painted.get(c), false));
        for (Control child : c.children()) if (child.parent() == c) walk(child, key, visible, hidden);
    }

    String pick(int x, int y) {
        Node best = null;
        for (Node n : nodes.values()) {
            Geometry g = n.geometry;
            if (!n.control.isAlive() || n.control instanceof Overlay || n.control instanceof Window || !n.visible || g == null || !g.area.contains(x, y) || (g.clip != null && !g.clip.contains(x, y)))
                continue;
            if (best == null || g.depth > best.geometry.depth || (g.depth == best.geometry.depth && g.order > best.geometry.order))
                best = n;
        }
        return best == null ? "" : best.id;
    }

    Window inspectedRoot() {
        return root;
    }

    String selection() {
        return selected;
    }

    String lastError() {
        return error;
    }

    public void tick() {
        clientThread();
        if ((root == null || System.nanoTime() - lastFrame > 300_000_000L) && System.nanoTime() - lastSend > 100_000_000L)
            publish();
    }

    private List<DebugProperty> properties(Control c) {
        return InspectorProperties.properties(c);
    }

    @Override
    public void properties(Control c, List<DebugProperty> out) {
        out.addAll(properties(c));
    }

    public void execute(InspectorCommand command) {
        clientThread();
        error = "";
        try {
            switch (command.operation()) {
                case SELECT -> {
                    valid(command.node());
                    selected = command.node();
                    selectedLifecycle = nodes.get(selected).control.lifecycleInfo();
                }
                case TARGET -> {
                    Window next = targets.keySet().stream().filter(w -> w.context().id().toString().equals(command.node())).findFirst().orElseThrow(() -> new IllegalArgumentException("Window no longer exists"));
                    if (root != null) root.cancelInteractions();
                    root = next;
                    nodes.clear();
                    selected = "";
                    signalInspector.clear();
                    hovered = "";
                    picking = false;
                    lastFrame = 0;
                }
                case PICK -> {
                    picking = !picking;
                    if (root != null) root.cancelInteractions();
                }
                case FLAG -> {
                    switch (command.flag()) {
                        case "layout" -> layout = command.enabled();
                        case "text" -> text = command.enabled();
                        case "all" -> all = command.enabled();
                        default -> throw new IllegalArgumentException("Unknown flag");
                    }
                }
                case UNDO -> overrides.undo();
                case REDO -> overrides.redo();
                case RESET_ALL -> overrides.resetAll();
                case EDIT, RESET, RESET_NODE -> {
                    Node n = valid(command.node());
                    if (n.internal)
                        throw new IllegalArgumentException("Internal diagnostic nodes are read-only; edit the owning control");
                    if (command.operation() == InspectorCommand.Operation.RESET_NODE) {
                        Set<Control> controls = Collections.newSetFromMap(new IdentityHashMap<>());
                        controls.add(n.control);
                        properties(n.control).forEach(p -> controls.add(p.target()));
                        overrides.reset(controls);
                    } else {
                        List<DebugProperty> list = properties(n.control);
                        int index = command.property();
                        if (index < 0 || index >= list.size())
                            throw new IllegalArgumentException("Property no longer exists");
                        DebugProperty property = list.get(index);
                        if (command.operation() == InspectorCommand.Operation.RESET)
                            overrides.set(property.target(), property.key(), null);
                        else {
                            bypass = true;
                            Object value;
                            try {
                                value = property.parse(command.value());
                            } finally {
                                bypass = false;
                            }
                            overrides.set(property.target(), property.key(), value);
                        }
                    }
                }
                case STOP -> {
                    OreInspector.stop();
                    return;
                }
            }
            if (root != null) root.invalidateTextLayout();
        } catch (Exception ex) {
            bypass = false;
            error = ex.getMessage() == null ? "Invalid request" : ex.getMessage();
        }
        publish();
    }

    private Node valid(String key) {
        Node node = nodes.get(key);
        if (root == null || !root.isAlive() || node == null || (!node.internal && !key.equals(id(node.control))) || !node.control.isAlive() || !attached(node.control))
            throw new IllegalArgumentException("Stale node; refresh the tree");
        return node;
    }

    private boolean attached(Control c) {
        for (Control n = c; n != null; n = n.parent()) if (n == root) return true;
        Part part = parts.get(c);
        return part != null && attached(part.owner);
    }

    private void publish() {
        List<InspectorSnapshot.Node> out = new ArrayList<>();
        for (Node n : nodes.values()) {
            if (!n.control.isAlive()) continue;
            List<InspectorSnapshot.Property> props = new ArrayList<>();
            int index = 0;
            for (DebugProperty p : properties(n.control)) {
                bypass = true;
                Object base;
                try {
                    base = p.read().get();
                } finally {
                    bypass = false;
                }
                Object effective = p.read().get();
                props.add(new InspectorSnapshot.Property(index++, p.key(), p.group(), p.kind(), p.options(), display(base), display(effective),
                        overrides.contains(p.target(), p.key()), n.internal, base instanceof Component component ? Component.Serializer.toJson(component) : ""));
            }
            readonly(props, "visible", n.visible);
            readonly(props, "focusable", n.control.focusable());
            readonly(props, "layoutDirty", n.control.layoutDirty());
            readonly(props, "requestedPlacement", n.control.debugRequestedBounds());
            readonly(props, "actualBounds", n.control.layoutBounds());
            if (n.control instanceof Drawer drawer) readonly(props, "panelBounds", drawer.panelBounds());
            if (n.control instanceof Scroll scroll) readonly(props, "viewport", scroll.viewport());
            if (n.control instanceof Button button) {
                readonly(props, "disabled", button.disabled());
                readonly(props, "buttonPressed", button.buttonPressed());
            }
            if (n.control instanceof io.github.luyingyilong.ore.input.InputControl input)
                readonly(props, "inputValue", input.widget().getText());
            Binding binding = bindings.get(n.control);
            if (binding != null) {
                readonly(props, "stableKey", String.valueOf(binding.key));
                readonly(props, "bindingGeneration", binding.generation);
            }
            if (texts.containsKey(n.control)) readonly(props, "textGeometry", texts.get(n.control).toString());
            if (primitives.containsKey(n.control))
                readonly(props, "inputGeometry", primitives.get(n.control).toString());
            Integer total = null, instances = null;
            if (n.control instanceof Grid<?> grid) {
                total = grid.entries().size();
                instances = grid.activeViews();
            }
            if (n.control instanceof Table<?> table) {
                total = table.layoutRows().size();
                instances = table.activeViews();
            }
            String reason = !n.visible ? n.reason : n.geometry == null ? "Not rendered (outside viewport or buffer)" : "";
            out.add(new InspectorSnapshot.Node(n.id, n.parent, n.name, n.control.getClass().getSimpleName(), n.visible, n.internal, reason,
                    n.geometry == null ? n.control.absoluteBounds() : n.geometry.area, total, instances, List.copyOf(props)));
        }
        List<InspectorSnapshot.Target> windows = targets.entrySet().stream().map(e -> new InspectorSnapshot.Target(e.getKey().context().id().toString(),
                        e.getKey().debugName().isBlank() ? "Window" : e.getKey().debugName(), e.getKey().host().nativeWindow(), System.nanoTime() - e.getValue() < 500_000_000L))
                .sorted(Comparator.comparing(InspectorSnapshot.Target::id)).toList();
        snapshot = new InspectorSnapshot(++sequence, root == null || System.nanoTime() - lastFrame > 300_000_000L, selected, picking, error,
                overrides.canUndo(), overrides.canRedo(), windows, List.copyOf(out), signalSnapshot(), lifecycleSnapshot(), lifecycleHistory.snapshot());
        lastSend = System.nanoTime();
    }

    private LifecycleInfo lifecycleSnapshot() {
        Node current = nodes.get(selected);
        if (current != null) selectedLifecycle = current.control.lifecycleInfo();
        return selectedLifecycle;
    }

    private InspectorSnapshot.SignalPanel signalSnapshot() {
        Node current = nodes.get(selected);
        if (current != null) signalInspector.watch(current.id, current.name, current.control);
        Map<Long, String> names = new HashMap<>();
        for (Node node : nodes.values()) {
            names.put(io.github.luyingyilong.ore.signal.SignalDiagnostics.ownerId(node.control), node.name);
            if (node.control instanceof io.github.luyingyilong.ore.input.InputControl input)
                names.put(io.github.luyingyilong.ore.signal.SignalDiagnostics.ownerId(input.widget()), node.name + " / Input");
        }
        return signalInspector.snapshot(names, current != null);
    }

    private void readonly(List<InspectorSnapshot.Property> props, String name, Object value) {
        props.add(new InspectorSnapshot.Property(-1, name, "State", "text", List.of(), display(value), display(value), false, true, ""));
    }

    private Object display(Object value) {
        if (value instanceof Component component) return component.getString();
        if (value instanceof Enum<?> e) return e.name();
        return value;
    }

    private void drawOverlay(GuiGraphics g) {
        String key = picking ? hovered : selected;
        Node selectedNode = nodes.get(key);
        g.flush();
        g.pose().pushPose();
        // Minecraft's GUI near plane clips very large positive Z (10,000 is already outside it).
        float maximum = painted.values().stream().map(geo -> geo.depth).max(Float::compare).orElse(0.0F);
        float depth = Math.min(8000.0F, Math.max(1000.0F, maximum + 500.0F));
        g.pose().translate(0, 0, depth - g.pose().last().pose().m32());
        try {
            for (Node n : nodes.values()) {
                if ((!all && n != selectedNode) || !n.control.isAlive() || !n.visible || n.geometry == null) continue;
                Geometry geo = n.geometry;
                Rect area = intersect(geo.clip, geo.area);
                if (area.width() == 0 || area.height() == 0) continue;
                if (layout) {
                    Rect slot = slots.get(n.control);
                    if (slot != null && n.control.parent() != null) {
                        Rect parent = displayed(n.control.parent());
                        outline(g, slot.translated(parent.x(), parent.y()), 0xFFFFCC66);
                    }
                    if (geo.clip != null) outline(g, geo.clip, 0xFFCE8AFF);
                    if (n.control instanceof Scroll scroll)
                        outline(g, scroll.viewport().translated(geo.area.x(), geo.area.y()), 0xFFCE8AFF);
                    if (n.control instanceof Box box) {
                        Rect before = null;
                        for (Control child : box.children())
                            if (child.visible()) {
                                Rect current = displayed(child);
                                if (before != null) {
                                    if (current.y() >= before.bottom())
                                        g.fill(current.x(), before.bottom(), current.right(), current.y(), 0x55F383FF);
                                    else if (current.x() >= before.right())
                                        g.fill(before.right(), current.y(), current.x(), current.bottom(), 0x55F383FF);
                                }
                                before = current;
                            }
                    }
                    if (n.control instanceof Padding padding) {
                        var inset = padding.insets();
                        Rect a = geo.area;
                        g.fill(a.x(), a.y(), a.right(), a.y() + inset.top(), 0x40FFA34D);
                        g.fill(a.x(), a.bottom() - inset.bottom(), a.right(), a.bottom(), 0x40FFA34D);
                        g.fill(a.x(), a.y(), a.x() + inset.left(), a.bottom(), 0x40FFA34D);
                        g.fill(a.right() - inset.right(), a.y(), a.right(), a.bottom(), 0x40FFA34D);
                    }
                    Rect min = new Rect(geo.area.x(), geo.area.y(), n.control.minimumWidth(), n.control.minimumHeight());
                    outline(g, min, 0xFF63E688);
                    outline(g, area, 0xFF68D5FF);
                }
                if (text) for (Primitive primitive : primitives.getOrDefault(n.control, List.of()))
                    outline(g, intersect(primitive.clip, primitive.area), primitive.kind.equals("caret") ? 0xFF67F784 : 0xFFFFAB64);
                if (text) for (TextGeometry line : texts.getOrDefault(n.control, List.of())) {
                    outline(g, intersect(line.clip, line.line), 0xFF83FFFF);
                    horizontal(g, line.line, line.origin, 0xFF64AAFF);
                    horizontal(g, line.line, line.inkTop, 0xFFFFFF80);
                    horizontal(g, line.line, line.inkBottom, 0xFFFFFF80);
                    horizontal(g, line.line, line.baseline, 0xFFFF627B);
                }
            }
            if (picking || selectedNode != null) g.drawString(Minecraft.getInstance().font,
                    "Ore Inspector · " + (picking ? "PICK / Esc cancels" : "cyan bounds · gold slot · green min · purple clip · pink baseline"), 4, 4, 0xFFFFFFFF, true);
            g.flush();
        } finally {
            g.pose().popPose();
        }
    }

    private Rect displayed(Control c) {
        Geometry geometry = painted.get(c);
        if (geometry != null) return geometry.area;
        Rect area = c.absoluteBounds().translated(originX, originY);
        for (Control child = c, n = c.parent(); n != null; child = n, n = n.parent())
            if (n instanceof Scroll scroll && scroll.isContentChild(child))
                area = area.translated(-scroll.scrollX(), -scroll.scrollY());
        return area;
    }

    @Override
    public void close(Window closed) {
        overrides.removeTree(closed);
        Set<Control> ownedTargets = rootTargets.remove(closed);
        if (ownedTargets != null) overrides.forget(ownedTargets);
        sessions.remove(closed);
        targets.remove(closed);
        swallowed.remove(closed);
        if (root == closed) {
            root = null;
            nodes.clear();
            painted.clear();
            parts.clear();
            texts.clear();
            primitives.clear();
            clips.clear();
            selected = "";
            hovered = "";
            picking = false;
            collecting = false;
        }
    }

    public void clear() {
        signalInspector.clear();
        lifecycleHistory.clear(); selectedLifecycle = null;
        swallowed.clear();
        targets.clear();
        overrides.clear();
        nodes.clear();
        painted.clear();
        parts.clear();
        texts.clear();
        slots.clear();
        rootTargets.clear();
        bindings.clear();
        lifecycles.clear();
        identities.clear();
        sessions.clear();
    }

    private record Primitive(String kind, Rect area, Rect clip) {
    }

    private record Binding(Object key, long generation) {
    }

    private record Geometry(Rect area, Rect clip, float depth, int order) {
    }

    private record TextGeometry(Rect line, float baseline, float origin, float inkTop, float inkBottom, float fontSize,
                                String fonts, float scroll, Rect clip) {
    }

    private record Part(Control owner, String name) {
    }

    private record Node(String id, String parent, Control control, String name, boolean visible, String reason,
                        Geometry geometry, boolean internal) {
    }
}
