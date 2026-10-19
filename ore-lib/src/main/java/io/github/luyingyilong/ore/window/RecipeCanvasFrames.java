package io.github.luyingyilong.ore.window;

import net.minecraft.client.gui.screens.Screen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Reconcile only completed recipe-render frames; minimization/ticks cannot retire a layout. */
public final class RecipeCanvasFrames {
    public interface Layout {
        Screen getScreen();
        void close();
    }
    private static final Set<Layout> LAYOUTS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final java.util.Map<Screen, Set<Layout>> FRAMES = new IdentityHashMap<>();
    private RecipeCanvasFrames() { }
    public static void begin(Screen screen) { FRAMES.put(screen, Collections.newSetFromMap(new IdentityHashMap<>())); }
    public static void rendered(Layout layout) {
        LAYOUTS.add(layout);
        Set<Layout> seen = FRAMES.get(layout.getScreen());
        if (seen != null) seen.add(layout);
    }
    public static void end(Screen screen) {
        Set<Layout> seen = FRAMES.remove(screen);
        if (seen == null) return;
        for (Layout layout : List.copyOf(LAYOUTS))
            if (layout.getScreen() == screen && !seen.contains(layout)) { layout.close(); LAYOUTS.remove(layout); }
    }
    public static void collectStale() {
        for (Layout layout : List.copyOf(LAYOUTS)) if (layout.getScreen() == null) { layout.close(); LAYOUTS.remove(layout); }
    }
    public static void forget(Layout layout) { LAYOUTS.remove(layout); FRAMES.values().forEach(seen -> seen.remove(layout)); }
    public static void clear() { for (Layout layout : List.copyOf(LAYOUTS)) layout.close(); LAYOUTS.clear(); FRAMES.clear(); }
}
