package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.FocusManager;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Scroll;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.stream.IntStream;

/** Mouse/keyboard pairing, logical target identity and retained Dropdown Overlay paths. */
public final class ReleaseControlsTest {
    private ReleaseControlsTest() { }

    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        checkbox(font);
        segmented(font);
        scrolledSegmented(font);
        dropdown(font);
    }

    private static void checkbox(Font font) {
        int[] changes = {0};
        Checkbox checkbox = Signals.configure(new Checkbox(Component.literal("Label")), connected -> connected.toggled().connect(connected, value -> changes[0]++));
        checkbox.setPosition(10, 10).setRequestedSize(90, 20);
        var childContainer52 = new Control();
        childContainer52.addChild(checkbox);
        Window window = new Window(true).setContent(childContainer52);
        window.layoutViewport(120, 80, font);
        // The Label child is hit first; its Checkbox parent must own the release.
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,40, 15, null));
        check(!checkbox.getChecked() && changes[0] == 0);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,40, 15) && checkbox.getChecked() && changes[0] == 1);
        check(!io.github.luyingyilong.ore.event.WindowInputs.release(window,40, 15));
        io.github.luyingyilong.ore.event.WindowInputs.press(window,40, 15, null);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,110, 50) && changes[0] == 1);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,40, 15, null);
        checkbox.setEnabled(false).setEnabled(true);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,40, 15);
        check(changes[0] == 1);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,40, 15, null);
        checkbox.setVisible(false);
        checkbox.setVisible(true);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,40, 15);
        check(changes[0] == 1);
        window.getFocusManager().requestFocus(checkbox, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null);
        check(changes[0] == 1);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_SPACE, 0, null) && changes[0] == 2);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        window.getFocusManager().requestFocus(null, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(changes[0] == 2);
    }

    private static void segmented(Font font) {
        int[] changes = {0};
        Segmented segmented = Signals.configure(new Segmented(List.of(Component.literal("A"), Component.literal("B"))), connected -> connected.selectionChanged().connect(connected, index -> changes[0]++));
        Window window = new Window(true).setContent(segmented);
        window.layoutViewport(120, 20, font);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,80, 10, null));
        check(segmented.getSelected() == 0 && changes[0] == 0);
        check(segmented.topInset(0, false) == 2 && segmented.topInset(1, true) == 2);
        check(segmented.topInset(1, false) == 0); // Dragging away restores the normal head.
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,10, 10) && changes[0] == 0); // Another option is not the original target.
        check(segmented.topInset(1, true) == 0);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,80, 10, null);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,80, 35) && changes[0] == 0);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,80, 10, null);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,80, 10) && segmented.getSelected() == 1 && changes[0] == 1);
        window.getFocusManager().requestFocus(segmented, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_LEFT, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(segmented.getSelected() == 1 && changes[0] == 1);
        check(segmented.topInset(0, false) == 2 && segmented.getFocusBounds().y() == 2);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(segmented.getSelected() == 0 && changes[0] == 2);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(segmented.getSelected() == 0 && changes[0] == 2);
        check(!io.github.luyingyilong.ore.event.WindowInputs.press(window,10, 0, null)); // Selected texture's 2px top inset.
        io.github.luyingyilong.ore.event.WindowInputs.release(window,10, 10);
        check(changes[0] == 2);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,10, 10, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,10, 10);
        check(changes[0] == 2 && segmented.getFocusBounds().x() == 0); // Reselecting is not a state change.
    }

    private static void scrolledSegmented(Font font) {
        Segmented segmented = new Segmented(List.of(Component.literal("A"), Component.literal("B")));
        segmented.setPosition(0, 80).setRequestedSize(100, 20);
        var childContainer53 = new Control().setPreferred(100, 200);
        childContainer53.addChild(segmented);
        Scroll scroll = new Scroll().setContent(childContainer53);
        Window window = new Window(true).setContent(scroll);
        window.layoutViewport(120, 60, font);
        scroll.scrollBy(0, 70);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,75, 15, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,75, 15) && segmented.getSelected() == 1);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,10, 15, null);
        scroll.scrollBy(0, 60);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,10, 15);
        check(segmented.getSelected() == 1);
    }

    private static void dropdown(Font font) {
        int[] changes={0},underlying={0};
        Dropdown<String> dropdown=Signals.configure(new Dropdown<String>("a",2), connected -> connected.itemActivated().connect(connected, value->changes[0]++));
        var options=List.of(option("a"),option("b"));dropdown.setOptions(options);
        dropdown.setPosition(10,10).setRequestedSize(80,20);
        Button behind=Signals.configure(new Button(cue->{}), connected -> connected.pressed().connect(connected, ()->underlying[0]++));
        behind.setPosition(100,90).setRequestedSize(20,20);
        Overlay overlay=new Overlay();
        var childContainer54 = new Control();
        childContainer54.addChild(dropdown);
        childContainer54.addChild(behind);
        Window window=new Window(true).setContent(childContainer54).setOverlay(overlay);
        window.layoutViewport(120,120,font);
        EventFixture.press(window,20,15);check(!dropdown.isOpen());
        EventFixture.release(window,20,15,true);check(dropdown.isOpen());
        EventFixture.press(window,20,55);EventFixture.release(window,20,35,true);
        check(dropdown.isOpen()&&changes[0]==0);
        EventFixture.press(window,20,55);EventFixture.release(window,110,100,true);
        check(dropdown.isOpen()&&changes[0]==0);
        EventFixture.press(window,20,55);dropdown.setOptions(List.copyOf(options));
        EventFixture.release(window,20,55,true);
        check(!dropdown.isOpen()&&dropdown.getSelected().equals("b")&&changes[0]==1);
        EventFixture.press(window,20,15);EventFixture.release(window,20,15,true);
        EventFixture.press(window,110,100); // Outside closes immediately, and consumes this gesture.
        check(!dropdown.isOpen());EventFixture.release(window,110,100,true);check(underlying[0]==0);
        dropdown.select("a");int before=changes[0];
        window.getFocusManager().requestFocus(dropdown,null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER,0,null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER,0,null);
        check(dropdown.isOpen());
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_DOWN,0,null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER,0,null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER,0,null);
        check(changes[0]==before);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER,0,null);
        check(dropdown.getSelected().equals("b")&&changes[0]==before+1&&!dropdown.isOpen());
        dropdown.activate();window.layoutViewport(120,120,font);before=changes[0];
        EventFixture.press(window,20,55);
        Control modal=new Control(){@Override public boolean getFocusScope(){return true;}};
        overlay.addChild(modal);window.layoutViewport(120,120,font);
        EventFixture.release(window,20,55,true);check(changes[0]==before&&!dropdown.isOpen());
        modal.setVisible(false);dropdown.activate();EventFixture.press(window,20,55);
        dropdown.setOptions(List.of(option("a"),option("c")));
        EventFixture.release(window,20,55,true);check(changes[0]==before);
        dropdown.setOptions(List.of());check(!dropdown.isOpen()&&!dropdown.onActivationPressed(GLFW.GLFW_KEY_ENTER));
    }

    private static Dropdown.Option<String> option(String value) {
        return new Dropdown.Option<>(value, Component.literal(value));
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Release gesture, target identity or capture regression");
    }
}
