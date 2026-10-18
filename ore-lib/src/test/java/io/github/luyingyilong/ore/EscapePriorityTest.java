package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

/** Dispatch Esc through the real Window/FocusManager and stacked Ore popup types. */
public final class EscapePriorityTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        int[] closed = {0, 0, 0, 0};
        long[] now = {0L};
        Drawer drawer = new Drawer(Component.literal("Organization"), () -> now[0]).animationDuration(0)
                .content(new Control().preferred(80, 40)).onClose(() -> closed[0]++);
        Modal modal = new Modal(Component.literal("Edit"), null, null, null)
                .size(180, 100).content(new Control().preferred(80, 20));
        modal.onClose(() -> { closed[1]++; modal.visible(false); });
        Modal topModal = new Modal(Component.literal("Quantity"), null, null, null)
                .size(120, 80).content(new Control().preferred(20, 20));
        topModal.onClose(() -> { closed[2]++; topModal.visible(false); });
        topModal.visible(false);
        Window window = new Window(true).content(new Control())
                .overlay(new Overlay().add(drawer).add(modal).add(topModal)).onBack(() -> closed[3]++);
        drawer.open();
        window.layoutViewport(320, 200, font);

        topModal.visible(true);
        window.layoutViewport(320, 200, font);
        window.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, null);
        check(!topModal.visible() && modal.visible() && closed[2] == 1 && drawer.isOpen(), "Only the upper Modal closes");
        check(window.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, null), "Lower Modal consumes the next Esc");
        check(!modal.visible() && closed[1] == 1 && drawer.isOpen(), "Lower Modal closes before the Drawer");
        drawer.animationDuration(100);
        window.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, null);
        check(!drawer.isOpen() && drawer.visible() && closed[3] == 0, "Esc starts closing the Drawer without returning");
        window.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, null);
        check(closed[3] == 0, "The closing animation continues to block page navigation");
        now[0] = 100L;
        window.layoutViewport(320, 200, font);
        check(!drawer.visible() && closed[0] == 1 && closed[3] == 0, "Drawer closes before page navigation");
        window.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, null);
        check(closed[3] == 1, "Esc returns only after all popups are closed");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
