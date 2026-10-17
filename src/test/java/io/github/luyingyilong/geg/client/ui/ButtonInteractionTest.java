package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Overlay;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Scroll;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import io.github.luyingyilong.geg.client.ui.theme.OreIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Paired input events, cancellation, exact feedback and scrolled pointer capture. */
public final class ButtonInteractionTest {
    private ButtonInteractionTest() { }

    public static void main(String[] args) {
        List<String> events = new ArrayList<>();
        Button button = new Button(cue -> events.add(cue.name())).onPress(() -> events.add("action"));
        button.position(10, 10).requestedSize(30, 20);
        Control content = new Control().addChild(button);
        Overlay overlay = new Overlay();
        Window window = new Window(true).content(content).overlay(overlay);
        window.layoutViewport(100, 100, null);
        check(button.actionMode() == Button.ACTION_MODE.BUTTON_RELEASE && !button.emphasizedSound());

        check(window.pointerPressed(15, 15, null) && events.isEmpty());
        check(window.pointerReleased(15, 15));
        check(events.equals(List.of("CLICK", "action")));
        check(!window.pointerReleased(15, 15));
        events.clear();
        check(window.pointerPressed(15, 15, null));
        check(window.pointerReleased(50, 50) && events.isEmpty());
        window.pointerPressed(50, 50, null);
        window.pointerReleased(15, 15);
        check(events.isEmpty()); // Pressing outside cannot arm a button.

        button.actionMode(Button.ACTION_MODE.BUTTON_PRESS).emphasizedSound(true);
        check(window.pointerPressed(15, 15, null));
        button.pressAt(15, 15); // A duplicate dispatch must not fire a second callback.
        check(events.equals(List.of("BUTTON", "action")));
        check(window.pointerReleased(15, 15));
        check(events.size() == 2);

        events.clear();
        button.actionMode(Button.ACTION_MODE.BUTTON_RELEASE).emphasizedSound(false);
        check(window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null));
        check(window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null)); // OS repeat.
        check(events.isEmpty());
        check(!window.keyReleased(GLFW.GLFW_KEY_SPACE, 0, null));
        check(window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null));
        check(events.equals(List.of("CLICK", "action")));
        check(!window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null));

        events.clear();
        button.actionMode(Button.ACTION_MODE.BUTTON_PRESS);
        check(window.keyPressed(GLFW.GLFW_KEY_SPACE, 0, null));
        check(window.keyPressed(GLFW.GLFW_KEY_SPACE, 0, null));
        check(window.keyReleased(GLFW.GLFW_KEY_SPACE, 0, null));
        check(events.equals(List.of("CLICK", "action")));

        events.clear();
        button.actionMode(Button.ACTION_MODE.BUTTON_RELEASE);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        window.focusManager().requestFocus(null, null);
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(events.isEmpty());

        Button next = new Button(cue -> events.add(cue.name())).onPress(() -> events.add("next"))
                .actionMode(Button.ACTION_MODE.BUTTON_PRESS);
        next.position(50, 10).requestedSize(30, 20);
        content.addChild(next);
        window.layoutViewport(100, 100, null);
        button.actionMode(Button.ACTION_MODE.BUTTON_PRESS)
                .onPress(() -> window.focusManager().requestFocus(next, null));
        window.focusManager().requestFocus(button, null);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        check(events.equals(List.of("CLICK")));
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        check(events.equals(List.of("CLICK"))); // Holding cannot activate the newly focused button.
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(events.equals(List.of("CLICK", "CLICK", "next")));
        events.clear();
        button.actionMode(Button.ACTION_MODE.BUTTON_RELEASE).onPress(() -> events.add("action"));

        window.pointerPressed(15, 15, null);
        button.visible(false);
        button.visible(true);
        window.pointerReleased(15, 15);
        check(events.isEmpty());
        window.pointerPressed(15, 15, null);
        content.removeChild(button);
        content.addChild(button);
        window.layoutViewport(100, 100, null);
        window.pointerReleased(15, 15);
        check(events.isEmpty());

        window.pointerPressed(15, 15, null);
        Control popup = new Control() { @Override public boolean focusScope() { return true; } };
        overlay.add(popup);
        window.layoutViewport(100, 100, null);
        window.pointerReleased(15, 15);
        check(events.isEmpty());
        popup.visible(false);
        window.pointerPressed(15, 15, null);
        window.cancelInteractions();
        window.pointerReleased(15, 15);
        check(events.isEmpty());

        button.type(Button.Type.TEXT);
        check(button.type() == Button.Type.TEXT);
        check(Button.textBackgroundColor(false, false) == 0);
        check(Button.textBackgroundColor(true, false) == 0x16FFFFFF);
        check(Button.textBackgroundColor(true, true) == 0x1A000000);
        scrolledCapture();
        iconLayout();
        controlledState();
    }

    private static void controlledState() {
        List<String> events = new ArrayList<>();
        Button button = new Button(cue -> events.add(cue.name()));
        button.onPressed(() -> events.add("pressed:" + button.buttonPressed()));
        check(!button.disabled() && !button.toggleButton() && !button.buttonPressed());
        button.buttonPressed(true);
        check(!button.buttonPressed() && events.equals(List.of("pressed:false")));
        button.buttonPressed(false);
        check(events.size() == 1);
        button.toggleButton(true).buttonPressed(true).buttonPressed(true);
        check(button.buttonPressed() && events.equals(List.of("pressed:false", "pressed:true")));
        button.onFocusChanged(false);
        check(button.buttonPressed()); // Focus loss cancels gestures, not retained state.
        button.buttonPressedNoSignal(false).buttonPressedNoSignal(true);
        check(events.size() == 2);
        button.activate();
        check(!button.buttonPressed() && events.equals(List.of("pressed:false", "pressed:true", "CLICK", "pressed:false")));

        events.clear();
        button.position(0, 0).requestedSize(30, 20);
        Window window = new Window(true).content(new Control().addChild(button));
        window.layoutViewport(60, 60, null);
        window.pointerPressed(5, 5, null);
        window.pointerReleased(50, 50);
        check(events.isEmpty() && !button.buttonPressed());
        window.pointerPressed(5, 5, null);
        window.pointerReleased(5, 5);
        check(button.buttonPressed() && events.equals(List.of("CLICK", "pressed:true")));
        events.clear();
        window.pointerPressed(5, 5, null);
        button.disabled(true).disabled(false); // Disabling while held must disarm the release.
        window.pointerReleased(5, 5);
        check(button.buttonPressed() && events.isEmpty());
        button.disabled(true);
        check(!button.acceptsFocus() && !button.activate());
        check(!button.onActivationPressed(GLFW.GLFW_KEY_ENTER));
        window.pointerPressed(5, 5, null);
        window.pointerReleased(5, 5);
        check(events.isEmpty());
        button.buttonPressedNoSignal(false);
        check(!button.buttonPressed());
        button.disabled(false).buttonPressedNoSignal(true).toggleButton(false);
        check(!button.buttonPressed());
    }

    private static void iconLayout() {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText value) { return value.getString().length() * 6; }
        };
        Button.Icon icon = new Button.Icon(OreIcons.SMALL_CLOSE, 8, 8);
        Button button = new Button(cue -> { }).size(Button.Size.SMALL).icon(icon);
        check(button.icon().equals(icon));
        check(button.measureWidth(font, 100) == 18 && button.measureHeight(font, 100) == 18);
        Button.ContentLayout centered = Button.contentLayout(new Rect(0, 0, 18, 18),
                0, false, icon, null, Button.TextAlignment.LEFT);
        check(centered.icon().equals(new Rect(5, 5, 8, 8)) && centered.label().width() == 0);
        button.label(Component.literal("AB"));
        check(button.measureWidth(font, 100) == 40);
        Button.ContentLayout mixed = Button.contentLayout(new Rect(0, 0, 60, 20),
                12, true, icon, null, Button.TextAlignment.CENTER);
        check(mixed.icon().equals(new Rect(18, 6, 8, 8)));
        check(mixed.label().equals(new Rect(30, 0, 12, 20)));
        Button.ContentLayout narrow = Button.contentLayout(new Rect(0, 0, 40, 20),
                100, true, icon, null, Button.TextAlignment.RIGHT);
        check(narrow.label().right() == 32 && narrow.label().width() == 12);
        button.trailingIcon(icon);
        check(button.measureWidth(font, 100) == 52);
        Button.ContentLayout trailing = Button.contentLayout(new Rect(0, 0, 60, 20),
                12, true, icon, icon, Button.TextAlignment.CENTER);
        check(trailing.trailingIcon().equals(new Rect(44, 6, 8, 8)));
        check(trailing.label().right() + 4 <= trailing.trailingIcon().x());
        Control parent = new Control().addChild(button);
        parent.layout(new Rect(0, 0, 100, 20), font);
        button.icon(null);
        check(button.icon() == null && parent.layoutDirty());
        try {
            new Button.Icon(OreIcons.CLOSE, 0, 7);
            throw new AssertionError("Invalid icon dimensions accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static void scrolledCapture() {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText value) { return value.getString().length() * 6; }
        };
        int[] actions = {0};
        Button button = new Button(cue -> { }).onPress(() -> actions[0]++);
        button.position(10, 80).requestedSize(30, 20);
        Control content = new Control().preferred(80, 200).addChild(button);
        Scroll scroll = new Scroll().content(content);
        Window window = new Window(true).content(scroll);
        window.layoutViewport(80, 60, font);
        scroll.scrollBy(0, 70);
        check(window.pointerPressed(15, 15, null));
        check(window.pointerReleased(15, 15) && actions[0] == 1);
        check(window.pointerPressed(15, 15, null));
        scroll.scrollBy(0, 50);
        window.pointerReleased(15, 15);
        check(actions[0] == 1);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected button action, capture or feedback");
    }
}
