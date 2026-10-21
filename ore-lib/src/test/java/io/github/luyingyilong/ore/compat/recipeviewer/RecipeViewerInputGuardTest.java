package io.github.luyingyilong.ore.compat.recipeviewer;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.input.Dropdown;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.overlay.Drawer;
import io.github.luyingyilong.ore.control.overlay.Modal;
import io.github.luyingyilong.ore.control.overlay.Overlay;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.window.WindowOptions;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.List;

/** Protection must be queried without revealing/repositioning a scrolled native input. */
public final class RecipeViewerInputGuardTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        Window root = new Window(true).setContent(new Control()).setOverlay(new Overlay());
        root.layoutViewport(200, 120, font);
        check(!RecipeViewerInputGuard.getKeyboardProtected(root), "Empty page leaves viewer shortcuts available");
        // Real native Input focus is exercised by RecipeViewerInputClientTest. Keep this
        // layout check independent of MUI's Minecraft-registry bootstrap.
        Control input = new Control().setFocusable(true).setPreferred(300, 22);
        Scroll scroll = new Scroll().setHorizontal(Scroll.BarMode.NEVER).setVertical(Scroll.BarMode.DISABLED).setContent(input);
        root.setContent(scroll); root.layoutViewport(120, 60, font);
        root.getFocusManager().requestFocus(input, null);
        scroll.setScroll(120, 0);
        check(!RecipeViewerInputGuard.getKeyboardProtected(root), "A plain focus target does not impersonate an editable field");
        check(scroll.getScrollX() == 120, "Ownership query must not validate/reveal the input and move the pointer target");
        input.setVisible(false);
        check(!RecipeViewerInputGuard.getKeyboardProtected(root), "Hidden input cannot retain protection");
        input.setVisible(true); root.getFocusManager().requestFocus(null, null);
        check(!RecipeViewerInputGuard.getKeyboardProtected(root), "Unfocused page restores viewer shortcuts");

        Overlay overlay = root.getPopupOverlay();
        Modal modal = io.github.luyingyilong.ore.control.overlay.OverlayTestAccess.modal(Component.literal("Modal"), null, null, null).setSize(80, 80);
        overlay.addChild(modal); root.layoutViewport(200, 120, font);
        check(RecipeViewerInputGuard.getKeyboardProtected(root), "Modal owns keyboard even with no editable field");
        modal.setVisible(false);
        check(!RecipeViewerInputGuard.getKeyboardProtected(root), "Hidden Modal releases protection");
        Control dialog = new Control() { @Override public boolean getFocusScope() { return true; } };
        overlay.addChild(dialog); root.layoutViewport(200, 120, font);
        check(RecipeViewerInputGuard.getKeyboardProtected(root), "Dialog uses the same focus-scope barrier");
        dialog.setVisible(false);
        long[] clock = {0};
        Drawer drawer = io.github.luyingyilong.ore.control.overlay.OverlayTestAccess.drawer(Component.literal("Drawer"), () -> clock[0]);
        overlay.addChild(drawer); drawer.open(); root.layoutViewport(200, 120, font);
        check(RecipeViewerInputGuard.getKeyboardProtected(root), "Opening animation protects the full backdrop");
        clock[0] = 200; root.layoutViewport(200, 120, font); drawer.close();
        check(RecipeViewerInputGuard.getKeyboardProtected(root), "Closing animation remains a barrier");
        clock[0] = 400; root.layoutViewport(200, 120, font);
        check(!RecipeViewerInputGuard.getKeyboardProtected(root), "Finished close releases the barrier");

        root.setContent(new Control());
        Dropdown<String> dropdown = new Dropdown<>("a", 4);
        dropdown.setOptions(List.of(new Dropdown.Option<>("a", Component.literal("A")), new Dropdown.Option<>("b", Component.literal("B"))));
        dropdown.setPosition(10, 10).setRequestedSize(60, 22);
        root.getChildren().get(0).addChild(dropdown); root.layoutViewport(200, 120, font);
        io.github.luyingyilong.ore.event.WindowInputs.press(root, 15, 15, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(root, 15, 15);
        check(dropdown.isOpen() && RecipeViewerInputGuard.getKeyboardProtected(root), "Dropdown Overlay owns keyboard and backdrop input");
        root.getContext().getPopups().cancel();
        check(!RecipeViewerInputGuard.getKeyboardProtected(root), "Closing Dropdown restores normal input ownership");
        root.queueFree();
        check(!RecipeViewerInputGuard.getKeyboardProtected(root), "Pending deletion cannot keep viewer input blocked");
        scroll.queueFree();
        Window nativeWindow = Window.nativeWindow(WindowOptions.defaults("Inspector"));
        check(!RecipeViewerInputGuard.getKeyboardProtected(nativeWindow), "Native Inspector never captures main-window viewer input");
        nativeWindow.queueFree(); LifecycleScheduler.flush();
        System.out.println("ORE_RECIPE_VIEWER_INPUT_GUARD_PASS");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
