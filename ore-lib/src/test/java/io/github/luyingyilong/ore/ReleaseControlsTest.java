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

/** Mouse/keyboard pairing, logical target identity and both tree/detached Dropdown paths. */
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
        detachedDropdown();
    }

    private static void checkbox(Font font) {
        int[] changes = {0};
        Checkbox checkbox = new Checkbox(Component.literal("Label")).onChange(value -> changes[0]++);
        checkbox.position(10, 10).requestedSize(90, 20);
        Window window = new Window(true).content(new Control().addChild(checkbox));
        window.layoutViewport(120, 80, font);
        // The Label child is hit first; its Checkbox parent must own the release.
        check(window.pointerPressed(40, 15, null));
        check(!checkbox.checked() && changes[0] == 0);
        check(window.pointerReleased(40, 15) && checkbox.checked() && changes[0] == 1);
        check(!window.pointerReleased(40, 15));
        window.pointerPressed(40, 15, null);
        check(window.pointerReleased(110, 50) && changes[0] == 1);
        window.pointerPressed(40, 15, null);
        checkbox.enabled(false).enabled(true);
        window.pointerReleased(40, 15);
        check(changes[0] == 1);
        window.pointerPressed(40, 15, null);
        checkbox.visible(false);
        checkbox.visible(true);
        window.pointerReleased(40, 15);
        check(changes[0] == 1);
        window.focusManager().requestFocus(checkbox, null);
        window.keyPressed(GLFW.GLFW_KEY_SPACE, 0, null);
        window.keyPressed(GLFW.GLFW_KEY_SPACE, 0, null);
        check(changes[0] == 1);
        check(window.keyReleased(GLFW.GLFW_KEY_SPACE, 0, null) && changes[0] == 2);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        window.focusManager().requestFocus(null, null);
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(changes[0] == 2);
    }

    private static void segmented(Font font) {
        int[] changes = {0};
        Segmented segmented = new Segmented(List.of(Component.literal("A"), Component.literal("B")))
                .onSelect(index -> changes[0]++);
        Window window = new Window(true).content(segmented);
        window.layoutViewport(120, 20, font);
        check(window.pointerPressed(80, 10, null));
        check(segmented.selected() == 0 && changes[0] == 0);
        check(segmented.topInset(0, false) == 2 && segmented.topInset(1, true) == 2);
        check(segmented.topInset(1, false) == 0); // Dragging away restores the normal head.
        check(window.pointerReleased(10, 10) && changes[0] == 0); // Another option is not the original target.
        check(segmented.topInset(1, true) == 0);
        window.pointerPressed(80, 10, null);
        check(window.pointerReleased(80, 35) && changes[0] == 0);
        window.pointerPressed(80, 10, null);
        check(window.pointerReleased(80, 10) && segmented.selected() == 1 && changes[0] == 1);
        window.focusManager().requestFocus(segmented, null);
        window.keyPressed(GLFW.GLFW_KEY_LEFT, 0, null);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        check(segmented.selected() == 1 && changes[0] == 1);
        check(segmented.topInset(0, false) == 2 && segmented.focusBounds().y() == 2);
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(segmented.selected() == 0 && changes[0] == 2);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        window.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, null);
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(segmented.selected() == 0 && changes[0] == 2);
        check(!window.pointerPressed(10, 0, null)); // Selected texture's 2px top inset.
        window.pointerReleased(10, 10);
        check(changes[0] == 2);
        window.pointerPressed(10, 10, null);
        window.pointerReleased(10, 10);
        check(changes[0] == 3 && segmented.focusBounds().x() == 0);
    }

    private static void scrolledSegmented(Font font) {
        Segmented segmented = new Segmented(List.of(Component.literal("A"), Component.literal("B")));
        segmented.position(0, 80).requestedSize(100, 20);
        Scroll scroll = new Scroll().content(new Control().preferred(100, 200).addChild(segmented));
        Window window = new Window(true).content(scroll);
        window.layoutViewport(120, 60, font);
        scroll.scrollBy(0, 70);
        check(window.pointerPressed(75, 15, null));
        check(window.pointerReleased(75, 15) && segmented.selected() == 1);
        window.pointerPressed(10, 15, null);
        scroll.scrollBy(0, 60);
        window.pointerReleased(10, 15);
        check(segmented.selected() == 1);
    }

    private static void dropdown(Font font) {
        int[] changes = {0};
        Dropdown<String> dropdown = new Dropdown<>("a", 2);
        List<Dropdown.Option<String>> options = List.of(option("a"), option("b"));
        dropdown.setOptions(options);
        dropdown.onSelect(value -> changes[0]++);
        dropdown.position(10, 10).requestedSize(80, 20);
        int[] underlyingClicks = {0};
        Button underneath = new Button(cue -> { }).onPress(() -> underlyingClicks[0]++);
        underneath.position(100, 90).requestedSize(20, 20);
        Control page = new Control().addChild(dropdown).addChild(underneath);
        Control menuLayer = new Control() {
            @Override public Control hitTop(int x, int y) { return dropdown.isOpen() && hit(x, y) ? this : null; }
            @Override protected boolean onPointerPressed(int x, int y) {
                return dropdown.isOpen() && dropdown.clickAt(0, 0, x, y);
            }
            @Override public boolean releasePointer(int x, int y, boolean over) {
                if (!over) { dropdown.cancelInteraction(); return true; }
                return dropdown.releaseAt(x, y);
            }
            @Override public void cancelInteraction() { dropdown.cancelInteraction(); }
        };
        Overlay overlay = new Overlay().add(menuLayer);
        Window window = new Window(true).content(page).overlay(overlay);
        window.layoutViewport(120, 120, font);
        window.pointerPressed(20, 15, null);
        check(!dropdown.isOpen());
        check(window.pointerReleased(20, 15) && dropdown.isOpen());
        window.pointerPressed(20, 55, null);
        check(dropdown.selected().equals("a") && changes[0] == 0);
        window.pointerReleased(20, 35); // Press B, release A.
        check(dropdown.isOpen() && dropdown.selected().equals("a") && changes[0] == 0);
        window.pointerPressed(20, 55, null);
        window.pointerReleased(110, 100);
        check(dropdown.isOpen() && changes[0] == 0);
        window.pointerPressed(20, 55, null);
        dropdown.setOptions(List.copyOf(options)); // Unchanged snapshots must preserve the gesture.
        window.pointerReleased(20, 55);
        check(!dropdown.isOpen() && dropdown.selected().equals("b") && changes[0] == 1);
        window.pointerPressed(20, 15, null);
        window.pointerReleased(110, 100);
        check(!dropdown.isOpen() && underlyingClicks[0] == 0);
        window.pointerPressed(20, 15, null);
        window.pointerReleased(20, 15);
        window.pointerPressed(110, 100, null);
        check(dropdown.isOpen());
        window.pointerReleased(110, 100);
        check(!dropdown.isOpen());

        dropdown.select("a");
        int before = changes[0];
        window.focusManager().requestFocus(dropdown, null);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        check(!dropdown.isOpen());
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(dropdown.isOpen());
        window.keyPressed(GLFW.GLFW_KEY_DOWN, 0, null);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        check(dropdown.selected().equals("a") && changes[0] == before);
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(dropdown.selected().equals("b") && changes[0] == before + 1 && !dropdown.isOpen());

        dropdown.select("a");
        dropdown.activate();
        before = changes[0];
        window.pointerPressed(20, 55, null);
        Control popup = new Control() { @Override public boolean focusScope() { return true; } };
        overlay.add(popup);
        window.layoutViewport(120, 120, font);
        window.pointerReleased(20, 55);
        check(changes[0] == before);
        popup.visible(false);
        window.pointerPressed(20, 55, null);
        dropdown.setOptions(List.of(option("a"), option("c")));
        window.pointerReleased(20, 55);
        check(changes[0] == before && dropdown.selected().equals("a"));

        dropdown.setOptions(IntStream.range(0, 10).mapToObj(index -> option("v" + index)).toList());
        dropdown.clickAt(0, 0, 20, 55);
        dropdown.scroll(-1);
        dropdown.releaseAt(20, 55);
        check(changes[0] == before);
        dropdown.setOptions(List.of());
        check(!dropdown.isOpen() && !dropdown.onActivationPressed(GLFW.GLFW_KEY_ENTER));
    }

    private static void detachedDropdown() {
        int[] actions = {0};
        Dropdown<String> dropdown = new Dropdown<String>("a", 4).checkable(false)
                .onSelect(value -> actions[0]++);
        dropdown.setOptions(List.of(option("a"), option("b")));
        check(dropdown.click(200, 100, 80, 20, 210, 110) && !dropdown.isOpen());
        check(dropdown.releaseAt(210, 110) && dropdown.isOpen());
        dropdown.click(200, 100, 80, 20, 210, 125);
        check(actions[0] == 0);
        dropdown.releaseAt(210, 145);
        check(dropdown.isOpen() && actions[0] == 0);
        dropdown.click(200, 100, 80, 20, 210, 125);
        dropdown.releaseAt(210, 125);
        check(!dropdown.isOpen() && actions[0] == 1); // Re-selecting an action must still execute it.
        dropdown.onActivationPressed(GLFW.GLFW_KEY_SPACE);
        check(!dropdown.isOpen());
        dropdown.onActivationReleased(GLFW.GLFW_KEY_SPACE);
        check(dropdown.isOpen());
        dropdown.onFocusMove(FocusManager.Direction.DOWN);
        dropdown.onActivationPressed(GLFW.GLFW_KEY_ENTER);
        dropdown.close();
        dropdown.onActivationReleased(GLFW.GLFW_KEY_ENTER);
        check(actions[0] == 1);
    }

    private static Dropdown.Option<String> option(String value) {
        return new Dropdown.Option<>(value, Component.literal(value));
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Release gesture, target identity or capture regression");
    }
}
