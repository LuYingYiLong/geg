package io.github.luyingyilong.ore.property;

import java.util.Objects;
import java.util.function.Supplier;

/** Neutral property access support shared by resource configuration and optional debug tooling. */
public final class PropertyAccess {
    private static final ThreadLocal<Integer> BASE_READ = ThreadLocal.withInitial(() -> 0);
    private static volatile Preview preview;
    private PropertyAccess() { }
    public static boolean isReadingBase() { return BASE_READ.get() != 0; }
    public static <T> T readBase(Supplier<T> reader) {
        BASE_READ.set(BASE_READ.get() + 1);
        try { return reader.get(); } finally { BASE_READ.set(BASE_READ.get() - 1); }
    }
    public static boolean isPreviewing() { return preview != null; }
    /** Called after validation, before setter mutation; captures a dry run without changing the target. */
    public static boolean previewWrite(Object target, String key, Object value) {
        Preview current = preview;
        if (current == null || current.thread != Thread.currentThread()) return false;
        if (current.target != target || !current.key.equals(key)) throw new IllegalStateException("Unexpected setter during property validation: " + key);
        if (!Objects.equals(current.expected, value)) throw new IllegalArgumentException("Setter would normalize proposed value: " + key);
        current.captured = true;
        return true;
    }
    public static void validateSetter(Object target, String key, Object value, Runnable setter) {
        if (preview != null) throw new IllegalStateException("Nested property setter validation");
        Preview current = new Preview(target, key, value); preview = current;
        try {
            setter.run();
            if (!current.captured) throw new IllegalStateException("Setter lacks previewWrite: " + key);
        } finally { preview = null; }
    }
    private static final class Preview {
        final Thread thread = Thread.currentThread();
        final Object target, expected;
        final String key;
        boolean captured;
        Preview(Object target, String key, Object expected) { this.target = target; this.key = key; this.expected = expected; }
    }
}
