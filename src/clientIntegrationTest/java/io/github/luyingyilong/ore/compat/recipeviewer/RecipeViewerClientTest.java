package io.github.luyingyilong.ore.compat.recipeviewer;

import io.github.luyingyilong.ore.Drawer;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.screen.OreButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.stream.Stream;

/** Opt-in real plugin discovery and exclusion queries, using an isolated run directory. */
@Mod.EventBusSubscriber(modid = "geg", value = Dist.CLIENT)
public final class RecipeViewerClientTest {
    private static int ticks, phase, changedAt;
    private static Window window;
    private static Drawer drawer;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        String viewer = System.getProperty("ore.recipe.viewer.smoke", "");
        if (viewer.isEmpty() || event.phase != TickEvent.Phase.END || phase == 99) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            if (++ticks > 800) throw new AssertionError("Recipe viewer client timeout, phase=" + phase);
            if (phase == 0 && ticks > 15) {
                checkInstalled(viewer);
                if (!viewersReady(viewer)) return;
                if (viewer.equals("none")) check(List.of("jei", "emi", "roughlyenoughitems").stream()
                        .noneMatch(id -> ModList.get().isLoaded(id)), "Optional viewer APIs are absent from the normal client runtime");
                mc.setScreen(new InventoryScreen(mc.player)); changedAt = ticks; phase = 1;
            } else if (phase == 1 && ticks > changedAt + 10) {
                List<Rect> areas = RecipeViewerExclusions.getAreas(mc.screen);
                check(mc.screen.children().stream().filter(OreButtonWidget.class::isInstance).count() == 2, "Both real inventory entries exist");
                check(areas.size() == 2, "Both inventory entries have independent exclusions");
                for (String id : viewerIds(viewer)) checkViewer(id, areas);
                if (viewer.equals("jei+emi")) checkCombinedExclusions(areas);
                window = Window.embedded().setContent(new Box()); window.open(); changedAt = ticks; phase = 2;
            } else if (phase == 2 && ticks > changedAt + 10) {
                List<Rect> areas = RecipeViewerExclusions.getAreas(mc.screen);
                check(areas.equals(List.of(new Rect(0, 0, mc.screen.width, mc.screen.height))), "Full-screen Ore content is excluded");
                for (String id : viewerIds(viewer)) checkViewer(id, areas);
                Overlay overlay = new Overlay();
                window.setContent(new Control()).setOverlay(overlay);
                drawer = new Drawer(Component.literal("Exclusion test")).setAnimationDuration(0).setWidth(mc.screen.width / 2);
                overlay.addChild(drawer); drawer.open(); changedAt = ticks; phase = 3;
            } else if (phase == 3 && ticks > changedAt + 10) {
                List<Rect> areas = RecipeViewerExclusions.getAreas(mc.screen);
                check(areas.equals(List.of(drawer.getPanelBounds())), "Drawer excludes its panel, not its backdrop");
                for (String id : viewerIds(viewer)) checkViewer(id, areas);
                if (viewer.equals("jei+emi")) checkCombinedExclusions(areas);
                drawer.close(); changedAt = ticks; phase = 4;
            } else if (phase == 4 && ticks > changedAt + 5) {
                check(RecipeViewerExclusions.getAreas(mc.screen).isEmpty(), "Closing the drawer removes all Ore exclusions");
                phase = 99;
                System.out.println("ORE_" + viewer.replace('+', '_').toUpperCase(java.util.Locale.ROOT) + "_EXCLUSIONS_CLIENT_PASS");
                mc.stop();
            }
        } catch (Throwable error) {
            phase = 99; error.printStackTrace(); System.out.println("ORE_RECIPE_VIEWER_CLIENT_FAIL"); mc.stop();
        }
    }

    static List<String> viewerIds(String viewer) {
        return viewer.equals("none") ? List.of() : List.of(viewer.split("\\+"));
    }

    static void checkInstalled(String viewer) {
        for (String id : viewerIds(viewer)) check(ModList.get().isLoaded(id), "Requested viewer is loaded: " + id);
    }

    public static boolean viewersReady(String viewer) throws ReflectiveOperationException {
        if (viewer.equals("jei")) return ModList.get().isLoaded("jei") && getJeiRuntime() != null;
        if (!viewer.equals("jei+emi")) return true;
        return getJeiRuntime() != null
                && (boolean) Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("isLoaded").invoke(null);
    }

    static Object getJeiRuntime() throws ReflectiveOperationException {
        // With EMI, use the runtime actually held by JEMI. MUI selects EMI and its
        // JEI-only cache is not a reliable readiness signal for a combined install.
        if (ModList.get().isLoaded("emi")) return Class.forName("dev.emi.emi.jemi.JemiPlugin").getField("runtime").get(null);
        return Class.forName("brachy.modularui.integration.jei.ModularUIJeiPlugin").getMethod("getRuntime").invoke(null);
    }

    private static void checkCombinedExclusions(List<Rect> expected) throws ReflectiveOperationException {
        Class<?> base = Class.forName("dev.emi.emi.screen.EmiScreenBase");
        Object screen = base.getMethod("of", net.minecraft.client.gui.screens.Screen.class).invoke(null, Minecraft.getInstance().screen);
        if ((boolean) base.getMethod("isEmpty").invoke(screen)) {
            // No viewer host is registered for this OreScreen. Its two native callbacks
            // were checked above; do not force-enable a sidebar just for the test.
            check(Minecraft.getInstance().screen instanceof io.github.luyingyilong.ore.screen.OreScreen,
                    "The inventory must have a real EMI host for the combined exclusion check");
            return;
        }
        Class<?> registry = Class.forName("dev.emi.emi.registry.EmiExclusionAreas");
        List<?> handlers = (List<?>) registry.getField("generic").get(null);
        check(handlers.stream().filter(value -> value.getClass().getName().endsWith(".OreEmiPlugin")).count() == 1,
                "Combined installation registers Ore's EMI handler once");
        List<Rect> previous = null;
        for (int repeat = 0; repeat < 20; repeat++) {
            List<?> actual = (List<?>) registry.getMethod("getExclusion", base).invoke(null, screen);
            var rectangles = new java.util.ArrayList<Rect>();
            for (Object value : actual) {
                Class<?> type = value.getClass();
                rectangles.add(new Rect((int) type.getMethod("x").invoke(value), (int) type.getMethod("y").invoke(value),
                        (int) type.getMethod("width").invoke(value), (int) type.getMethod("height").invoke(value)));
            }
            check(rectangles.containsAll(expected), "Combined EMI registry, including JEI bridge, receives all Ore exclusions");
            if (previous != null) check(rectangles.equals(previous), "Repeated queries cannot accumulate exclusion registrations");
            previous = List.copyOf(rectangles);
        }
        System.out.println("ORE_JEI_EMI_COMBINED_EXCLUSIONS_STABLE");
    }

    private static void checkViewer(String viewer, List<Rect> expected) throws ReflectiveOperationException {
        // Keep optional test APIs off the normal integration-test compile/runtime classpath.
        if (viewer.equals("emi")) {
            Class<?> api = Class.forName("dev.emi.emi.api.EmiExclusionArea");
            var handlers = (List<?>) Class.forName("dev.emi.emi.registry.EmiExclusionAreas").getField("generic").get(null);
            Object handler = handlers.stream().filter(value -> value.getClass().getName().endsWith(".OreEmiPlugin")).findFirst().orElseThrow(
                    () -> new AssertionError("EMI discovered and registered Ore's plugin"));
            var actual = new java.util.ArrayList<Rect>();
            java.util.function.Consumer<Object> consumer = value -> {
                try {
                    Class<?> type = value.getClass();
                    actual.add(new Rect((int) type.getMethod("x").invoke(value), (int) type.getMethod("y").invoke(value),
                            (int) type.getMethod("width").invoke(value), (int) type.getMethod("height").invoke(value)));
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            };
            api.getMethod("addExclusionArea", net.minecraft.client.gui.screens.Screen.class, java.util.function.Consumer.class)
                    .invoke(handler, Minecraft.getInstance().screen, consumer);
            check(actual.containsAll(expected), "EMI's registered handler receives current Ore rectangles");
            return;
        }
        if (viewer.equals("roughlyenoughitems")) {
            Class<?> api = Class.forName("me.shedaniel.rei.api.client.registry.screen.ScreenRegistry");
            Object registry = api.getMethod("getInstance").invoke(null);
            Object zones = api.getMethod("exclusionZones").invoke(registry);
            var values = (List<?>) Class.forName("me.shedaniel.rei.api.client.registry.screen.ExclusionZones")
                    .getMethod("getExclusionZones", net.minecraft.client.gui.screens.Screen.class).invoke(zones, Minecraft.getInstance().screen);
            var actual = new java.util.ArrayList<Rect>();
            for (Object value : values) {
                Class<?> type = value.getClass();
                actual.add(new Rect(type.getField("x").getInt(value), type.getField("y").getInt(value),
                        type.getField("width").getInt(value), type.getField("height").getInt(value)));
            }
            check(actual.containsAll(expected), "REI's real runtime receives current Ore rectangles: expected=" + expected + ", actual=" + actual);
            return;
        }
        if (!viewer.equals("jei")) throw new IllegalArgumentException("Unsupported live test viewer: " + viewer);
        Object runtime = getJeiRuntime();
        check(runtime != null, "JEI runtime is available");
        Object helper = Class.forName("mezz.jei.api.runtime.IJeiRuntime").getMethod("getScreenHelper").invoke(runtime);
        try (Stream<?> stream = (Stream<?>) Class.forName("mezz.jei.api.runtime.IScreenHelper")
                .getMethod("getGuiExclusionAreas", net.minecraft.client.gui.screens.Screen.class)
                .invoke(helper, Minecraft.getInstance().screen)) {
            List<Rect> actual = stream.map(value -> {
                var area = (net.minecraft.client.renderer.Rect2i) value;
                return new Rect(area.getX(), area.getY(), area.getWidth(), area.getHeight());
            }).toList();
            check(actual.containsAll(expected), "JEI's real runtime receives every current Ore rectangle: expected=" + expected + ", actual=" + actual);
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
