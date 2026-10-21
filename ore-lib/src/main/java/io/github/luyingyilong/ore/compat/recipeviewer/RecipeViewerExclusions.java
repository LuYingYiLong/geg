package io.github.luyingyilong.ore.compat.recipeviewer;

import io.github.luyingyilong.ore.Dialog;
import io.github.luyingyilong.ore.Drawer;
import io.github.luyingyilong.ore.Modal;
import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.screen.OreButtonWidget;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.ore.window.WindowPopups;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Viewer-independent, live GUI-pixel geometry. No viewer API or host mod is loaded here.
 * Never use the full-screen scrim as the bounds of a drawer or popup.
 */
public final class RecipeViewerExclusions {
    private RecipeViewerExclusions() { }

    public static List<Rect> getAreas(Screen screen) {
        if (screen == null) return List.of();
        List<Rect> areas = new ArrayList<>();
        var external = io.github.luyingyilong.ore.window.ScreenWindowHost.get(screen);
        if (external != null && external.isOpen()) {
            if(screen instanceof io.github.luyingyilong.ore.menu.OreMenuScreen<?> menu && menu.getContent()!=null){
                var content=menu.getContent();var bounds=EventGeometry.displayed(content);var clip=EventGeometry.clip(content);
                areas.add(clip==null?bounds:EventGeometry.intersect(bounds,clip));
            }
            else areas.addAll(external.getExclusionAreas());
            for (Control child : external.getWindow().getChildren())
                if (child instanceof Overlay) collectControls(child, areas);
        }
        if (screen instanceof OreScreen oreScreen)
            for (Window window : oreScreen.getWindows())
                if (window.getHost().isOpen()) areas.addAll(getAreas(window));
        Set<GuiEventListener> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collectWidgets(screen, visited, areas);
        return clipAreas(areas, new Rect(0, 0, screen.width, screen.height));
    }

    /** Window-local geometry is converted to the hosting Screen exactly once. */
    public static List<Rect> getAreas(Window window) {
        if (window == null || !available(window) || window.getContext().getNativeWindow()) return List.of();
        List<Rect> areas = new ArrayList<>();
        for (Control child : window.getChildren()) collectControls(child, areas);
        int x = window.getContext().getOriginX(), y = window.getContext().getOriginY();
        return clipAreas(areas, window.getAbsoluteBounds()).stream().map(area -> area.translated(x, y)).toList();
    }

    private static void collectWidgets(GuiEventListener node, Set<GuiEventListener> visited, List<Rect> areas) {
        if (!visited.add(node)) return;
        if (node instanceof OreButtonWidget button) areas.add(button.getExclusionBounds());
        if (node instanceof ContainerEventHandler container)
            for (GuiEventListener child : List.copyOf(container.children())) collectWidgets(child, visited, areas);
    }

    private static void collectControls(Control node, List<Rect> areas) {
        if (!available(node)) return;
        // Overlay and the bare, non-painting Control are routing/layout roots, not surfaces.
        if (node instanceof Overlay || node.getClass() == Control.class || node.getClass() == io.github.luyingyilong.ore.layout.Container.class) {
            for (Control child : node.getChildren()) collectControls(child, areas);
            return;
        }
        Rect bounds;
        if (node instanceof Drawer drawer) bounds = drawer.getPanelBounds();
        else if (node instanceof Modal modal) bounds = modal.getPanelBounds();
        else if (node instanceof Dialog dialog) bounds = dialog.getPanelBounds();
        else if (node instanceof WindowPopups.Popup popup) bounds = popup.getPanelBounds();
        else bounds = node.getAbsoluteBounds();
        bounds = EventGeometry.displayed(node, bounds);
        Rect clip = EventGeometry.clip(node);
        areas.add(clip == null ? bounds : EventGeometry.intersect(bounds, clip));
    }

    private static boolean available(Control node) {
        return node.isAlive() && node.getVisible() && !node.isSuspended();
    }

    /** Keep disjoint surfaces separate, discard empty/duplicate/contained rectangles. */
    static List<Rect> clipAreas(List<Rect> areas, Rect viewport) {
        List<Rect> result = new ArrayList<>();
        for (Rect raw : areas) {
            Rect area = EventGeometry.intersect(raw, viewport);
            if (area.width() == 0 || area.height() == 0 || result.stream().anyMatch(old -> contains(old, area))) continue;
            result.removeIf(old -> contains(area, old));
            result.add(area);
        }
        return List.copyOf(result);
    }

    private static boolean contains(Rect outer, Rect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }
}
