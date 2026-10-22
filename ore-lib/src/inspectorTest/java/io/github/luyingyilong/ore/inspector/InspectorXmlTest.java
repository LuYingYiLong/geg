package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.gui.*;
import io.github.luyingyilong.ore.signal.SignalScope;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.io.StringReader;

public final class InspectorXmlTest {
    public static void main(String[] args) throws Exception {
        UiLoader loader = new UiLoader(UiTypeRegistry.createDefault(), id -> new StringReader("<Label id='title' text='XML label'/>"), Runnable::run);
        try (SignalScope owner = new SignalScope(); UiDocument document = loader.open(ResourceLocation.parse("test:gui/inspector.xml"), new UiBindings(), owner)) {
            Window window = Window.embedded().setContent(document.getRoot()); InspectorBackend backend = new InspectorBackend();
            try {
                window.layoutViewport(128, 100, null);
                var target = InspectorBackend.class.getDeclaredField("root"); target.setAccessible(true); target.set(backend, window);
                snapshot(backend, window);
                var node = backend.snapshot().nodes().stream().filter(n -> n.properties().stream().anyMatch(p -> p.name().equals("id") && p.effective().equals("title"))).findFirst().orElseThrow();
                for (String property : java.util.List.of("id", "xmlResource", "xmlLocation", "xmlScope", "xmlVersion", "xmlConfiguration", "xmlReloadError"))
                    check(node.properties().stream().anyMatch(p -> p.name().equals(property) && p.readonly()), "Read-only XML metadata " + property);
                check(node.properties().stream().anyMatch(p -> p.name().equals("text") && p.origin().startsWith("XML ")), "Property source is XML");
                document.getRoot().findControlFromId("title", Label.class).setText(Component.literal("Java value")); snapshot(backend, window);
                check(backend.snapshot().nodes().stream().flatMap(n -> n.properties().stream()).anyMatch(p -> p.name().equals("text") && p.origin().startsWith("Java (XML")), "Java base changes are distinct from XML configuration");
            } finally { backend.clear(); window.queueFree(); LifecycleScheduler.flush(); }
        }
        System.out.println("ORE_INSPECTOR_XML_PASS");
    }
    private static void snapshot(InspectorBackend backend, Window window) throws Exception {
        var rebuild = InspectorBackend.class.getDeclaredMethod("rebuildNodes", Window.class); rebuild.setAccessible(true); rebuild.invoke(backend, window);
        var publish = InspectorBackend.class.getDeclaredMethod("publish"); publish.setAccessible(true); publish.invoke(backend);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
