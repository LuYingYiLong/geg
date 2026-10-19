package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.Signals;

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
        Drawer drawer = Signals.configure(new Drawer(Component.literal("Organization"), () -> now[0]).setAnimationDuration(0)
                .setContent(new Control().setPreferred(80, 40)), connected -> connected.closed().connect(connected, () -> closed[0]++));
        Modal modal = new Modal(Component.literal("Edit"), null, null, null)
                .setSize(180, 100).setContent(new Control().setPreferred(80, 20));
        modal.closeRequested().connect(modal, () -> { closed[1]++; modal.setVisible(false); });
        Modal topModal = new Modal(Component.literal("Quantity"), null, null, null)
                .setSize(120, 80).setContent(new Control().setPreferred(20, 20));
        topModal.closeRequested().connect(topModal, () -> { closed[2]++; topModal.setVisible(false); });
        topModal.setVisible(false);
        Window window = new Window(true).setContent(new Control())
                .setOverlay(new Overlay().add(drawer).add(modal).add(topModal)).onBack(() -> closed[3]++);
        drawer.open();
        window.layoutViewport(320, 200, font);

        topModal.setVisible(true);
        window.layoutViewport(320, 200, font);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        check(modal.getVisible()&&drawer.isOpen(),"Held Esc cannot cascade through layers");
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        check(!topModal.getVisible() && modal.getVisible() && closed[2] == 1 && drawer.isOpen(), "Only the upper Modal closes");
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null), "Lower Modal consumes the next Esc");
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        check(!modal.getVisible() && closed[1] == 1 && drawer.isOpen(), "Lower Modal closes before the Drawer");
        drawer.setAnimationDuration(100);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        check(!drawer.isOpen() && drawer.getVisible() && closed[3] == 0, "Esc starts closing the Drawer without returning");
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        check(closed[3] == 0, "The closing animation continues to block page navigation");
        now[0] = 100L;
        window.layoutViewport(320, 200, font);
        check(!drawer.getVisible() && closed[0] == 1 && closed[3] == 0, "Drawer closes before page navigation");
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ESCAPE, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ESCAPE,0,null);
        check(closed[3] == 1, "Esc returns only after all popups are closed");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
