package io.github.luyingyilong.ore.compat.recipeviewer;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.display.Badge;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.input.Dropdown;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.overlay.Drawer;
import io.github.luyingyilong.ore.control.overlay.Modal;
import io.github.luyingyilong.ore.control.overlay.Overlay;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.window.screen.OreButtonWidget;
import io.github.luyingyilong.ore.window.WindowOptions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.List;

/** All three adapters use these live rectangles rather than independent position caches. */
public final class RecipeViewerExclusionsTest {
    private static final Font FONT = new Font(id -> null, false) {
        @Override public int width(FormattedText text) { return text.getString().length() * 6; }
    };

    public static void main(String[] args) {
        inventoryButtons();
        surfacesAndLifecycle();
        drawerAnimation();
        dropdownOverlay();
        System.out.println("ORE_RECIPE_VIEWER_EXCLUSIONS_PASS");
    }

    private static void inventoryButtons() {
        TestScreen screen = new TestScreen();
        Badge badge = new Badge(1);
        Button button = new Button().setLabel(Component.literal("Mail")).setBadge(badge);
        OreButtonWidget widget = new OreButtonWidget(button, Component.literal("Mail"));
        widget.place(FONT, 4, 74, 32);
        screen.add(widget);
        List<Rect> before = RecipeViewerExclusions.getAreas(screen);
        check(before.equals(List.of(new Rect(4, 74, widget.getWidth(), widget.getHeight()))), "Inventory entry reserves its actual rectangle");
        widget.active = false;
        check(RecipeViewerExclusions.getAreas(screen).equals(before), "Disabled visible controls still reserve space");
        badge.setCount(100);
        widget.place(FONT, 4, 74, 32);
        check(RecipeViewerExclusions.getAreas(screen).get(0).width() > before.get(0).width(), "Unread badge resize updates exclusion immediately");
        button.setOffset(-10, 0);
        widget.place(FONT, 4, 74, 32);
        Rect shifted = RecipeViewerExclusions.getAreas(screen).get(0);
        check(shifted.x() == 0 && shifted.width() == widget.getWidth() - 6, "Use final offset and clip to GUI viewport");
        widget.visible = false;
        check(RecipeViewerExclusions.getAreas(screen).isEmpty(), "Hidden vanilla widget leaves no stale region");
        widget.visible = true; button.setVisible(false);
        check(RecipeViewerExclusions.getAreas(screen).isEmpty(), "Hidden Ore button leaves no stale region");
        button.setVisible(true); widget.queueFree();
        check(RecipeViewerExclusions.getAreas(screen).isEmpty(), "Queued widgets stop reserving before deferred cleanup");
        check(RecipeViewerExclusions.getAreas((Screen) null).isEmpty(), "No screen, no regions");
        LifecycleScheduler.flush();
    }

    private static void surfacesAndLifecycle() {
        Overlay overlay = new Overlay();
        Window window = new Window(true).setContent(new Box()).setOverlay(overlay);
        window.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(window).equals(List.of(new Rect(0, 0, 200, 120))), "Full-page content reserves the GUI viewport");
        Modal modal = io.github.luyingyilong.ore.control.overlay.OverlayTestAccess.modal(Component.literal("Modal"), null, null, null).setSize(80, 80);
        overlay.addChild(modal); window.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(window).size() == 1, "Contained overlays are deduplicated");
        Control empty = new Control();
        window.setContent(empty); window.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(window).equals(List.of(modal.getPanelBounds())), "Backdrop and bare routing roots do not reserve the whole screen");
        window.getContext().setOrigin(7, 11);
        check(RecipeViewerExclusions.getAreas(window).equals(List.of(modal.getPanelBounds().translated(7, 11))), "Canvas origin is applied exactly once");
        modal.setVisible(false);
        check(RecipeViewerExclusions.getAreas(window).isEmpty(), "Closing overlay releases its area");
        Scroll scroll = new Scroll().setContent(new Control().setPreferred(1000, 1000));
        scroll.setPosition(10, 20).setRequestedSize(90, 70);
        empty.addChild(scroll); window.layoutViewport(200, 120, FONT);
        scroll.setScroll(200, 300);
        check(RecipeViewerExclusions.getAreas(window).equals(List.of(new Rect(17, 31, 90, 70))), "Large scrolled content only reserves the visible scroll viewport");
        window.prepareTree(); window.hostDetached();
        check(RecipeViewerExclusions.getAreas(window).isEmpty(), "Retained parent screens do not reserve space");
        window.prepareTree(); window.queueFree();
        check(RecipeViewerExclusions.getAreas(window).isEmpty(), "Pending root cleanup releases all exclusions");
        Window nativeWindow = Window.nativeWindow(WindowOptions.defaults("Inspector")).setContent(new Box());
        nativeWindow.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(nativeWindow).isEmpty(), "Native Inspector never excludes areas in Minecraft");
        nativeWindow.queueFree(); LifecycleScheduler.flush();
    }

    private static void drawerAnimation() {
        long[] now = {0};
        Overlay overlay = new Overlay();
        Window root = new Window(true).setContent(new Control()).setOverlay(overlay);
        Drawer drawer = io.github.luyingyilong.ore.control.overlay.OverlayTestAccess.drawer(Component.literal("Drawer"), () -> now[0]);
        overlay.addChild(drawer);
        root.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(root).isEmpty(), "Closed drawer is absent");
        drawer.open(); root.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(root).isEmpty(), "Fully offscreen opening frame is absent");
        now[0] = 100; root.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(root).equals(List.of(new Rect(150, 0, 50, 120))), "Opening follows the visible animated frame, excluding its scrim");
        now[0] = 200; root.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(root).equals(List.of(new Rect(100, 0, 100, 120))), "Open right drawer reserves half the viewport");
        drawer.close(); now[0] = 300; root.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(root).equals(List.of(new Rect(150, 0, 50, 120))), "Closing animation still reserves its visible part");
        now[0] = 400; root.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(root).isEmpty(), "Closed animation releases its reservation");
        drawer.setPlacement(Drawer.Placement.LEFT).setAnimationDuration(0).open();
        root.layoutViewport(200, 120, FONT);
        check(RecipeViewerExclusions.getAreas(root).equals(List.of(new Rect(0, 0, 100, 120))), "Left-side chat drawer uses the same geometry");
        root.queueFree(); LifecycleScheduler.flush();
    }

    private static void dropdownOverlay() {
        Dropdown<String> dropdown = new Dropdown<>("a", 4);
        dropdown.setOptions(List.of(new Dropdown.Option<>("a", Component.literal("A")),
                new Dropdown.Option<>("b", Component.literal("B"))));
        dropdown.setPosition(30, 20).setRequestedSize(70, 22);
        Control page = new Control(); page.addChild(dropdown);
        Window root = new Window(true).setContent(page).setOverlay(new Overlay());
        root.layoutViewport(200, 120, FONT);
        io.github.luyingyilong.ore.event.WindowInputs.press(root, 35, 25, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(root, 35, 25);
        check(dropdown.isOpen(), "Dropdown opens through the normal Window input pipeline");
        List<Rect> open = RecipeViewerExclusions.getAreas(root);
        check(open.size() == 2 && open.contains(dropdown.getAbsoluteBounds()), "Trigger and popup reserve separate surfaces");
        check(open.stream().noneMatch(area -> area.equals(new Rect(0, 0, 200, 120))), "Popup's input mask does not reserve the entire screen");
        root.getContext().getPopups().cancel();
        check(RecipeViewerExclusions.getAreas(root).equals(List.of(dropdown.getAbsoluteBounds())), "Pending popup removal immediately releases its area");
        root.queueFree(); LifecycleScheduler.flush();
    }

    private static final class TestScreen extends Screen {
        TestScreen() { super(Component.empty()); width = 200; height = 100; }
        void add(OreButtonWidget widget) { addRenderableWidget(widget); }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
