package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.navigation.TabItem;
import io.github.luyingyilong.ore.control.navigation.Tabs;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.network.chat.Component;

/** Snapshot-only harness: no rendering thread or live Inspector window required. */
public final class InspectorTabsTest {
    public static void main(String[] args) throws Exception {
        Control first = new Control().setDebugName("First content"), second = new Control().setDebugName("Hidden content");
        Tabs tabs = new Tabs().addItem(new TabItem("first", Component.literal("First"), first))
                .addItem(new TabItem("second", Component.literal("Second"), second, null, true));
        Window root = Window.embedded().setContent(tabs);
        InspectorBackend backend = new InspectorBackend();
        try {
            root.layoutViewport(128, 100, null);
            // Exercise immutable tree snapshots without invoking the live renderer/client-thread gate.
            var target = InspectorBackend.class.getDeclaredField("root"); target.setAccessible(true); target.set(backend, root);
            snapshot(backend, root);
            var node = backend.snapshot().nodes().stream().filter(n -> n.className().equals("Tabs")).findFirst().orElseThrow();
            for (String name : java.util.List.of("selectedKey", "itemCount", "headerScrollX", "items"))
                check(node.properties().stream().anyMatch(p -> p.name().equals(name) && p.readonly()), "Read-only tab diagnostics: " + name);
            check(node.properties().stream().anyMatch(p -> p.name().equals("items") && p.effective().toString().contains("second [disabled]")), "Disabled metadata is visible");
            var hidden = backend.snapshot().nodes().stream().filter(n -> n.name().equals("Hidden content")).findFirst().orElseThrow();
            check(!hidden.visible() && !hidden.hiddenReason().isEmpty(), "Hidden pages retain their reason without appearing visible");
            check(backend.snapshot().nodes().stream().filter(InspectorSnapshot.Node::internal).count() >= 4, "Both captions expose owned Label and TextureRect nodes");
            check(InspectorProperties.properties(tabs).stream().noneMatch(p -> p.key().equals("selectedKey")), "Selection is not an editable business-state override");
            var maximum = InspectorProperties.properties(tabs).stream().filter(p -> p.key().equals("maxTabWidth")).findFirst().orElseThrow();
            check(maximum.parse("72").equals(72) && tabs.getMaxTabWidth() == 0, "Maximum width validation does not overwrite the source value");
            DebugOverrides widths = new DebugOverrides();
            DebugBridge.install(new DebugBridge.Backend() {
                @Override public Object value(Control control, String key, Object base) { return widths.value(control, key, base); }
            });
            try {
                widths.set(tabs, "maxTabWidth", 72); check(tabs.getMaxTabWidth() == 72, "Maximum width supports live override");
                widths.undo(); check(tabs.getMaxTabWidth() == 0, "Undo restores original maximum width");
            } finally { DebugBridge.install(null); }
            tabs.setThemeOverride(Tabs.FONT_COLOR, 0xFF123456);
            Control caption = ((Scroll) tabs.getChild(0)).getContent().getChild(0);
            check(DebugBridge.themes(caption).items().stream().anyMatch(i -> i.key().equals("Tabs:font_color") && i.value().equals("#FF123456")), "Caption theme diagnostics match parent local overrides");
            tabs.setItem(new TabItem("second", Component.literal("Second"), second)); tabs.setSelectedKey("second");
            snapshot(backend, root);
            check(backend.snapshot().nodes().stream().anyMatch(n -> n.id().equals(hidden.id()) && n.visible()), "Switching preserves node identity");
            var item = tabs.removeItem("first"); item.getContent().queueFree(); LifecycleScheduler.flush();
            snapshot(backend, root);
            check(backend.snapshot().nodes().stream().noneMatch(n -> n.name().equals("First content")), "Removed pages leave the active Inspector tree");
        } finally { backend.clear(); root.queueFree(); LifecycleScheduler.flush(); }
        System.out.println("ORE_INSPECTOR_TABS_PASS");
    }

    private static void snapshot(InspectorBackend backend, Window root) throws Exception {
        var rebuild = InspectorBackend.class.getDeclaredMethod("rebuildNodes", Window.class); rebuild.setAccessible(true); rebuild.invoke(backend, root);
        var publish = InspectorBackend.class.getDeclaredMethod("publish"); publish.setAccessible(true); publish.invoke(backend);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
