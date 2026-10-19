package io.github.luyingyilong.ore.debug;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Optional client instrumentation. The published mod has no inspector implementation.
 */
public final class DebugBridge {
    private static final Backend NONE = new Backend() {
    };
    private static Backend backend = NONE;
    private static volatile SetterPreview setterPreview;
    private static HostShortcut hostShortcut = (key, scan) -> false;

    @FunctionalInterface public interface HostShortcut { boolean handle(int key, int scan); }

    /** Registered only by the optional development frontend, even before an Inspector is open. */
    public static void setHostShortcut(HostShortcut shortcut) {
        hostShortcut = shortcut == null ? (key, scan) -> false : shortcut;
    }

    public static boolean handleHostShortcut(int key, int scan) { return hostShortcut.handle(key, scan); }

    private DebugBridge() {
    }

    /**
     * Annotated setters call this after validation/normalization, before any mutation.
     */
    public static boolean previewWrite(Control target, String key, Object value) {
        SetterPreview preview = setterPreview;
        if (preview == null || preview.thread != Thread.currentThread()) return false;
        if (preview.target != target || !preview.key.equals(key))
            throw new IllegalStateException("Unexpected setter in Inspector validation: " + key);
        if (!java.util.Objects.equals(preview.expected, value))
            throw new IllegalArgumentException("Setter would normalize the proposed value: " + key);
        preview.captured = true;
        return true;
    }

    // Normal application setters avoid boxing when no Inspector validation is running.
    public static boolean previewWrite(Control target, String key, int value) {
        return setterPreview != null && previewWrite(target, key, (Object) value);
    }

    public static boolean previewWrite(Control target, String key, long value) {
        return setterPreview != null && previewWrite(target, key, (Object) value);
    }

    public static boolean previewWrite(Control target, String key, float value) {
        return setterPreview != null && previewWrite(target, key, (Object) value);
    }

    public static boolean previewWrite(Control target, String key, double value) {
        return setterPreview != null && previewWrite(target, key, (Object) value);
    }

    public static boolean previewWrite(Control target, String key, boolean value) {
        return setterPreview != null && previewWrite(target, key, (Object) value);
    }

    /**
     * Validate via the real setter without changing base values, caches or layout state.
     */
    public static void validateSetter(Control target, String key, Object value, Runnable setter) {
        if (setterPreview != null) throw new IllegalStateException("Nested Inspector setter validation");
        SetterPreview preview = new SetterPreview(target, key, value);
        setterPreview = preview;
        try {
            setter.run();
            if (!preview.captured) throw new IllegalStateException("Setter lacks previewWrite: " + key);
        } finally {
            setterPreview = null;
        }
    }

    public static Backend backend() {
        return backend;
    }

    public static boolean active() {
        return backend != NONE;
    }

    public static void install(Backend next) {
        backend = next == null ? NONE : next;
    }

    @SuppressWarnings("unchecked")
    public static <T> T value(Control node, String key, T base) {
        return backend == NONE ? base : (T) backend.value(node, key, base);
    }

    public static int value(Control node, String key, int base) {
        return backend == NONE ? base : (Integer) backend.value(node, key, base);
    }

    public static float value(Control node, String key, float base) {
        return backend == NONE ? base : (Float) backend.value(node, key, base);
    }

    public static long value(Control node, String key, long base) {
        return backend == NONE ? base : (Long) backend.value(node, key, base);
    }

    public static boolean value(Control node, String key, boolean base) {
        return backend == NONE ? base : (Boolean) backend.value(node, key, base);
    }

    public static void properties(Control node, List<DebugProperty> result) {
        backend.properties(node, result);
    }

    public interface Backend {
        default void lifecycle(Control node, io.github.luyingyilong.ore.layout.LifecycleInfo info) {
        }

        default Object value(Control node, String key, Object base) {
            return base;
        }

        default void properties(Control node, List<DebugProperty> result) {
            node.debugProperties(result);
        }

        default void paint(Control node, DrawContext context, int x, int y) {
        }

        default void clip(Rect area) {
        }

        default void unclip() {
        }

        default void slot(Control node, Rect slot) {
        }

        default void binding(Control node, Object key) {
        }

        default void removed(Control node) {
        }

        default void begin(Window root, DrawContext context, int x, int y) {
        }

        default void end(Window root, DrawContext context, int x, int y) {
        }

        default void text(Control node, GuiGraphics graphics, Rect area, float x, float y,
                          float width, float height, float rowTop, float baseline, float inkTop, float inkBottom,
                          float fontSize, String fonts, float scroll) {
        }

        default void primitive(Control node, String kind, GuiGraphics graphics, Rect area) {
        }

        default void internal(Control owner, String name, Control part) {
        }

        default boolean picking() {
            return false;
        }

        default boolean picking(Window root) {
            return picking();
        }

        default boolean pointer(Window root, int x, int y, int button, boolean pressed) {
            return false;
        }

        default boolean key(Window root, int key, int action) {
            return false;
        }

        default void close(Window root) {
        }
    }

    private static final class SetterPreview {
        final Thread thread = Thread.currentThread();
        final Control target;
        final String key;
        final Object expected;
        boolean captured;

        SetterPreview(Control target, String key, Object expected) {
            this.target = target;
            this.key = key;
            this.expected = expected;
        }
    }
}
