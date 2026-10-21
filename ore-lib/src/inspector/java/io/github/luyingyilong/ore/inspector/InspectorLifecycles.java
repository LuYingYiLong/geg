package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleInfo;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded tombstones never keep a destroyed UI graph alive. */
final class InspectorLifecycles {
    private record Entry(LifecycleInfo info, WeakReference<Control> control) { }
    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
    void record(Control control, LifecycleInfo info) {
        entries.put(info.id(), new Entry(info, new WeakReference<>(control)));
        while (entries.size() > 512) entries.remove(entries.keySet().iterator().next());
    }
    List<LifecycleInfo> snapshot() { return entries.values().stream().map(Entry::info).toList(); }
    void clear() { entries.clear(); }
}
