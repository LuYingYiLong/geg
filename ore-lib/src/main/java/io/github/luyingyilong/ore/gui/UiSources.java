package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.Control;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Read-only provenance, with weak keys and text-only snapshots. No Inspector dependency.
 */
public final class UiSources {
    private static final Map<Control, Info> SOURCES = new WeakHashMap<>();

    private UiSources() {
    }

    static void put(Control control, Info info) {
        SOURCES.put(control, info);
        io.github.luyingyilong.ore.debug.DebugBridge.backend().xml(control, info);
    }

    public static Info get(Control control) {
        for (Control node = control; node != null; node = node.getGeometryParent()) {
            Info info = SOURCES.get(node);
            if (info != null) return info;
        }
        return null;
    }

    public static String getPropertyOrigin(Control control, String key, Object base) {
        Info info = SOURCES.get(control);
        if (info == null) return "Java";
        String configured = info.configuration().get(key);
        String location = info.resource() + ":" + info.line() + ":" + info.column();
        return configured != null && configured.equals(String.valueOf(base)) ? "XML " + location : "Java (XML " + location + ")";
    }

    public record Info(String resource, int line, int column, String scope, long version,
                       Map<String, String> configuration, String reloadError) {
        public Info {
            configuration = Map.copyOf(configuration);
        }
    }
}
