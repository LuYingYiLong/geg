package io.github.luyingyilong.ore.control.input;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.overlay.Overlay;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Fixed pixel geometry, reversible bounce and interaction through the real Window event route. */
public final class SwitchTest {
    public static void main(String[] args) throws Exception {
        geometryAndResources();
        animation();
        interaction();
        scrolledCapture();
        System.out.println("ORE_SWITCH_TEST_PASS");
    }

    private static void geometryAndResources() throws Exception {
        AtomicLong now = new AtomicLong(1_000L);
        Switch toggle = new Switch(false, now::get, cue -> { });
        check(!toggle.getChecked() && !toggle.getDisabled() && !toggle.getLoading(), "Defaults");
        check(toggle.measureSize(null, new Size(200, 100)).equals(new Size(28, 15)), "Natural footprint");
        check(toggle.getSizeFlagsHorizontal() == SizeFlags.SIZE_SHRINK_BEGIN
                && toggle.getSizeFlagsVertical() == SizeFlags.SIZE_SHRINK_CENTER, "Default alignment");
        toggle.layout(new Rect(10, 20, 80, 40), null);
        check(toggle.artworkBounds().equals(new Rect(36, 32, 28, 15)), "Fixed centered artwork");
        check(toggle.baseBounds().equals(new Rect(36, 34, 27, 13)), "Bottom-aligned base");
        check(toggle.thumbBounds().equals(new Rect(36, 32, 14, 15)), "Off thumb");
        check(!toggle.hit(10, 20) && toggle.hit(36, 32), "Extra allocation is not clickable");
        toggle.setChecked(true);
        now.addAndGet(240L);
        check(toggle.thumbBounds().equals(new Rect(49, 32, 14, 15)), "On thumb travels exactly thirteen pixels");
        check(toggle.thumbBounds().bottom() == toggle.baseBounds().bottom(), "Bottoms match");
        check(toggle.getFocusBounds().equals(toggle.thumbBounds()) && toggle.getThemeItem(io.github.luyingyilong.ore.theme.OreTheme.FOCUS).getExpandMargin().left() == 1, "Thumb focus");
        check(new Switch(true).getChecked(), "Initial value constructor");
        for (var key : List.of(Switch.BUTTON_NORMAL, Switch.BUTTON_HOVERED, Switch.BUTTON_PRESSED, Switch.BUTTON_DISABLED))
            check(SwitchTest.class.getResource("/assets/ore/" + key.defaultValue().getTexture().getPath()) != null,
                    "Shared thumb fallback points to an existing texture");

        for (String name : List.of("switch_base", "switch_base_disabled", "grabber_normal",
                "grabber_hover", "grabber_pressed", "grabber_disabled")) {
            try (var input = new DataInputStream(java.util.Objects.requireNonNull(SwitchTest.class.getResourceAsStream(
                    "/assets/ore/textures/icons/" + name + ".png")))) {
                input.skipNBytes(16);
                check(input.readInt() == (name.startsWith("switch_base") ? 27 : 14), "PNG width: " + name);
                check(input.readInt() == (name.startsWith("switch_base") ? 13 : 15), "PNG height: " + name);
            }
        }
    }

    private static void animation() {
        AtomicLong now = new AtomicLong(2_000L);
        Switch toggle = new Switch(false, now::get, cue -> { });
        toggle.setChecked(true);
        check(toggle.offsetAt(now.get()) == 0.0, "No initial jump");
        for (int i = 0; i <= 240; i++)
            check(toggle.offsetAt(2_000L + i) >= 0.0 && toggle.offsetAt(2_000L + i) <= 13.0, "Bounce remains in bounds");
        check(toggle.offsetAt(2_090L) > toggle.offsetAt(2_130L), "Visible rebound");
        check(toggle.offsetAt(2_240L) == 13.0 && toggle.offsetAt(3_000L) == 13.0, "Settles exactly");
        now.set(2_070L);
        double before = toggle.offsetAt(now.get());
        toggle.setChecked(true);
        check(toggle.offsetAt(now.get()) == before, "Repeated binding does not restart animation");
        double later = toggle.offsetAt(now.get() + 60L);
        toggle.setAnimationDuration(240L);
        check(toggle.offsetAt(now.get() + 60L) == later, "Repeated duration does not restart animation");
        toggle.setChecked(false);
        check(toggle.offsetAt(now.get()) == before, "Mid-animation reversal is continuous");
        now.addAndGet(240L);
        check(toggle.offsetAt(now.get()) == 0.0, "Reverse settles exactly");
        toggle.setAnimationDuration(0L).setChecked(true);
        check(toggle.offsetAt(now.get()) == 13.0, "Animation may be disabled");
        try { toggle.setAnimationDuration(-1L); throw new AssertionError("Negative duration accepted"); }
        catch (IllegalArgumentException expected) { }
        check(toggle.getAnimationDuration() == 0L, "Invalid duration preserves previous setting");
    }

    private static void interaction() {
        AtomicLong now = new AtomicLong(1_000L);
        List<String> events = new ArrayList<>();
        Switch toggle = Signals.configure(Signals.configure(new Switch(false, now::get, cue -> events.add(cue.name())), connected -> connected.toggled().connect(connected, value -> events.add("change:" + value))), connected -> connected.activated().connect(connected, value -> events.add("click:" + value)));
        toggle.setPosition(10, 10).setRequestedSize(28, 15);
        Control body = new Control();
        body.addChild(toggle);
        Overlay overlay = new Overlay();
        Window window = new Window(true).setContent(body).setOverlay(overlay);
        window.layoutViewport(100, 100, null);
        check(toggle.getButtonTextureKey(false).equals(io.github.luyingyilong.ore.control.input.Switch.BUTTON_NORMAL), "Normal texture");
        check(toggle.getButtonTextureKey(true).equals(io.github.luyingyilong.ore.control.input.Switch.BUTTON_HOVERED), "Hovered texture");
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,11, 11, null) && events.isEmpty() && !toggle.getChecked(), "Press arms only");
        check(toggle.getButtonTextureKey(true).equals(io.github.luyingyilong.ore.control.input.Switch.BUTTON_PRESSED), "Pressed texture");
        io.github.luyingyilong.ore.event.WindowInputs.move(window,80, 80);
        check(toggle.getButtonTextureKey(false).equals(io.github.luyingyilong.ore.control.input.Switch.BUTTON_NORMAL), "Drag out clears pressed visual");
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,80, 80) && events.isEmpty(), "Drag-out cancels");
        io.github.luyingyilong.ore.event.WindowInputs.press(window,11, 11, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,11, 11);
        check(events.equals(List.of("CLICK", "change:true", "click:true")) && toggle.getChecked(), "One release, one change");
        check(!io.github.luyingyilong.ore.event.WindowInputs.release(window,11, 11), "Duplicate release ignored");
        events.clear();
        toggle.setCheckedNoSignal(false);
        check(events.isEmpty(), "Programmatic binding is silent");

        window.getFocusManager().requestFocus(toggle, null);
        window.getFocusManager().keyboardNavigation();
        now.addAndGet(240L);
        check(window.getFocusManager().getOutlineBounds().equals(new Rect(9, 9, 16, 17)), "Off focus outline");
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null);
        check(events.isEmpty(), "Key repeat does not activate");
        check(!io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null), "Different key cannot release");
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_SPACE, 0, null);
        now.addAndGet(240L);
        check(toggle.getChecked() && events.size() == 3, "Keyboard activation once");
        check(window.getFocusManager().getOutlineBounds().equals(new Rect(22, 9, 16, 17)), "Focus follows moving thumb");

        events.clear();
        io.github.luyingyilong.ore.event.WindowInputs.press(window,11, 11, null);
        toggle.setDisabled(true).setDisabled(false);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,11, 11);
        check(events.isEmpty(), "Disable cancels armed press");
        toggle.setDisabled(true);
        check(!toggle.acceptsFocus() && !toggle.activate(), "Disabled blocks keyboard and activation");
        check(toggle.getButtonTextureKey(true).equals(io.github.luyingyilong.ore.control.input.Switch.BUTTON_DISABLED), "Disabled texture");
        toggle.setDisabled(false);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,11, 11, null);
        toggle.setLoading(true).setLoading(false);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,11, 11);
        check(events.isEmpty(), "Loading cancels armed press");
        toggle.setLoading(true);
        check(!toggle.acceptsFocus() && !toggle.activate() && toggle.getChildren().get(0).getVisible(), "Loading spinner and lock");
        toggle.setLoading(false);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,11, 11, null);
        toggle.setVisible(false);
        toggle.setVisible(true);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,11, 11);
        check(events.isEmpty(), "Hide cancels armed press");
        io.github.luyingyilong.ore.event.WindowInputs.press(window,11, 11, null);
        Control modal = new Control() { @Override public boolean getFocusScope() { return true; } };
        overlay.addChild(modal);
        window.layoutViewport(100, 100, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,11, 11);
        check(events.isEmpty(), "Top layer blocks stale releases");
    }

    private static void scrolledCapture() {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText value) { return value.getString().length() * 6; }
        };
        int[] changes = {0};
        Switch toggle = Signals.configure(new Switch(false, () -> 0L, cue -> { }), connected -> connected.toggled().connect(connected, value -> changes[0]++));
        toggle.setPosition(10, 80).setRequestedSize(28, 15);
        var childContainer59 = new Control().setPreferred(80, 200);
        childContainer59.addChild(toggle);
        Scroll scroll = new Scroll().setContent(childContainer59);
        Window window = new Window(true).setContent(scroll);
        window.layoutViewport(80, 60, font);
        scroll.scrollBy(0, 70);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null), "Scrolled press");
        io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15);
        check(changes[0] == 1, "Scrolled release uses content coordinates");
        io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null);
        scroll.scrollBy(0, 50);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15);
        check(changes[0] == 1, "Scrolling target away cancels release");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
