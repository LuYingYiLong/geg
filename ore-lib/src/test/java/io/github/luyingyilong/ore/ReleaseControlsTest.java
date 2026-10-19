package io.github.luyingyilong.ore;

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
        Checkbox checkbox = new Checkbox(Component.literal("Label")).onChange(value -> changes[0]++);
        checkbox.position(10, 10).requestedSize(90, 20);
        Window window = new Window(true).content(new Control().addChild(checkbox));
        window.layoutViewport(120, 80, font);
        // The Label child is hit first; its Checkbox parent must own the release.
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,40, 15, null));
        check(!checkbox.checked() && changes[0] == 0);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,40, 15) && checkbox.checked() && changes[0] == 1);
        check(!io.github.luyingyilong.ore.event.WindowInputs.release(window,40, 15));
        io.github.luyingyilong.ore.event.WindowInputs.press(window,40, 15, null);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,110, 50) && changes[0] == 1);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,40, 15, null);
        checkbox.enabled(false).enabled(true);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,40, 15);
        check(changes[0] == 1);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,40, 15, null);
        checkbox.visible(false);
        checkbox.visible(true);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,40, 15);
        check(changes[0] == 1);
        window.focusManager().requestFocus(checkbox, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null);
        check(changes[0] == 1);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_SPACE, 0, null) && changes[0] == 2);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        window.focusManager().requestFocus(null, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(changes[0] == 2);
    }

    private static void segmented(Font font) {
        int[] changes = {0};
        Segmented segmented = new Segmented(List.of(Component.literal("A"), Component.literal("B")))
                .onSelect(index -> changes[0]++);
        Window window = new Window(true).content(segmented);
        window.layoutViewport(120, 20, font);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,80, 10, null));
        check(segmented.selected() == 0 && changes[0] == 0);
        check(segmented.topInset(0, false) == 2 && segmented.topInset(1, true) == 2);
        check(segmented.topInset(1, false) == 0); // Dragging away restores the normal head.
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,10, 10) && changes[0] == 0); // Another option is not the original target.
        check(segmented.topInset(1, true) == 0);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,80, 10, null);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,80, 35) && changes[0] == 0);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,80, 10, null);
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,80, 10) && segmented.selected() == 1 && changes[0] == 1);
        window.focusManager().requestFocus(segmented, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_LEFT, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(segmented.selected() == 1 && changes[0] == 1);
        check(segmented.topInset(0, false) == 2 && segmented.focusBounds().y() == 2);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(segmented.selected() == 0 && changes[0] == 2);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(segmented.selected() == 0 && changes[0] == 2);
        check(!io.github.luyingyilong.ore.event.WindowInputs.press(window,10, 0, null)); // Selected texture's 2px top inset.
        io.github.luyingyilong.ore.event.WindowInputs.release(window,10, 10);
        check(changes[0] == 2);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,10, 10, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,10, 10);
        check(changes[0] == 3 && segmented.focusBounds().x() == 0);
    }

    private static void scrolledSegmented(Font font) {
        Segmented segmented = new Segmented(List.of(Component.literal("A"), Component.literal("B")));
        segmented.position(0, 80).requestedSize(100, 20);
        Scroll scroll = new Scroll().content(new Control().preferred(100, 200).addChild(segmented));
        Window window = new Window(true).content(scroll);
        window.layoutViewport(120, 60, font);
        scroll.scrollBy(0, 70);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,75, 15, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,75, 15) && segmented.selected() == 1);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,10, 15, null);
        scroll.scrollBy(0, 60);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,10, 15);
        check(segmented.selected() == 1);
    }

    private static void dropdown(Font font) {
        int[] changes={0},underlying={0};
        Dropdown<String> dropdown=new Dropdown<String>("a",2).onSelect(value->changes[0]++);
        var options=List.of(option("a"),option("b"));dropdown.setOptions(options);
        dropdown.position(10,10).requestedSize(80,20);
        Button behind=new Button(cue->{}).onPress(()->underlying[0]++);
        behind.position(100,90).requestedSize(20,20);
        Overlay overlay=new Overlay();
        Window window=new Window(true).content(new Control().addChild(dropdown).addChild(behind)).overlay(overlay);
        window.layoutViewport(120,120,font);
        EventFixture.press(window,20,15);check(!dropdown.isOpen());
        EventFixture.release(window,20,15,true);check(dropdown.isOpen());
        EventFixture.press(window,20,55);EventFixture.release(window,20,35,true);
        check(dropdown.isOpen()&&changes[0]==0);
        EventFixture.press(window,20,55);EventFixture.release(window,110,100,true);
        check(dropdown.isOpen()&&changes[0]==0);
        EventFixture.press(window,20,55);dropdown.setOptions(List.copyOf(options));
        EventFixture.release(window,20,55,true);
        check(!dropdown.isOpen()&&dropdown.selected().equals("b")&&changes[0]==1);
        EventFixture.press(window,20,15);EventFixture.release(window,20,15,true);
        EventFixture.press(window,110,100); // Outside closes immediately, and consumes this gesture.
        check(!dropdown.isOpen());EventFixture.release(window,110,100,true);check(underlying[0]==0);
        dropdown.select("a");int before=changes[0];
        window.focusManager().requestFocus(dropdown,null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER,0,null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER,0,null);
        check(dropdown.isOpen());
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_DOWN,0,null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER,0,null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER,0,null);
        check(changes[0]==before);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER,0,null);
        check(dropdown.selected().equals("b")&&changes[0]==before+1&&!dropdown.isOpen());
        dropdown.activate();window.layoutViewport(120,120,font);before=changes[0];
        EventFixture.press(window,20,55);
        Control modal=new Control(){@Override public boolean focusScope(){return true;}};
        overlay.add(modal);window.layoutViewport(120,120,font);
        EventFixture.release(window,20,55,true);check(changes[0]==before&&!dropdown.isOpen());
        modal.visible(false);dropdown.activate();EventFixture.press(window,20,55);
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
