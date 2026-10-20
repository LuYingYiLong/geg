package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.render.*;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Scroll;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.theme.OreTheme;
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
        Button button = new Button(cue -> events.add(cue.name()));
        io.github.luyingyilong.ore.signal.Connection action = button.pressed().connect(button, () -> events.add("action"));
        button.setPosition(10, 10).setRequestedSize(30, 20);
        Control content = new Control();
        content.addChild(button);
        Overlay overlay = new Overlay();
        Window window = new Window(true).setContent(content).setOverlay(overlay);
        window.layoutViewport(100, 100, null);
        check(button.getActionMode() == Button.ACTION_MODE.BUTTON_RELEASE && !button.getEmphasizedSound());

        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null) && events.isEmpty());
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15));
        check(events.equals(List.of("CLICK", "action")));
        check(!io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15));
        events.clear();
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,50, 50) && events.isEmpty());
        io.github.luyingyilong.ore.event.WindowInputs.press(window,50, 50, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15);
        check(events.isEmpty()); // Pressing outside cannot arm a button.

        button.setActionMode(Button.ACTION_MODE.BUTTON_PRESS).setEmphasizedSound(true);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null));
        check(events.equals(List.of("BUTTON", "action")));
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15));
        check(events.size() == 2);

        events.clear();
        button.setActionMode(Button.ACTION_MODE.BUTTON_RELEASE).setEmphasizedSound(false);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null)); // OS repeat.
        check(events.isEmpty());
        check(!io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_SPACE, 0, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null));
        check(events.equals(List.of("CLICK", "action")));
        check(!io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null));

        events.clear();
        button.setActionMode(Button.ACTION_MODE.BUTTON_PRESS);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_SPACE, 0, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_SPACE, 0, null));
        check(events.equals(List.of("CLICK", "action")));

        events.clear();
        button.setActionMode(Button.ACTION_MODE.BUTTON_RELEASE);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        window.getFocusManager().requestFocus(null, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(events.isEmpty());

        Button next = Signals.configure(new Button(cue -> events.add(cue.name())), connected -> connected.pressed().connect(connected, () -> events.add("next")))
                .setActionMode(Button.ACTION_MODE.BUTTON_PRESS);
        next.setPosition(50, 10).setRequestedSize(30, 20);
        content.addChild(next);
        window.layoutViewport(100, 100, null);
        action.disconnect();
        button.setActionMode(Button.ACTION_MODE.BUTTON_PRESS);
        action = button.pressed().connect(button, () -> window.getFocusManager().requestFocus(next, null));
        window.getFocusManager().requestFocus(button, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(events.equals(List.of("CLICK")));
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(events.equals(List.of("CLICK"))); // Holding cannot activate the newly focused button.
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(events.equals(List.of("CLICK", "CLICK", "next")));
        events.clear();
        action.disconnect();
        button.setActionMode(Button.ACTION_MODE.BUTTON_RELEASE);
        button.pressed().connect(button, () -> events.add("action"));

        io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null);
        button.setVisible(false);
        button.setVisible(true);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15);
        check(events.isEmpty());
        io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null);
        content.removeChild(button);
        content.addChild(button);
        window.layoutViewport(100, 100, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15);
        check(events.isEmpty());

        io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null);
        Control popup = new Control() { @Override public boolean getFocusScope() { return true; } };
        overlay.addChild(popup);
        window.layoutViewport(100, 100, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15);
        check(events.isEmpty());
        popup.setVisible(false);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null);
        window.cancelInteractions();
        io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15);
        check(events.isEmpty());

        button.setThemeTypeVariation("TextButton");
        check(button.getThemeTypeVariation().equals("TextButton"));
        check(((io.github.luyingyilong.ore.theme.StyleBoxFlat)button.getPresentationStyle(false,false)).color() == 0);
        check(((io.github.luyingyilong.ore.theme.StyleBoxFlat)button.getPresentationStyle(true,false)).color() == 0x16FFFFFF);
        check(((io.github.luyingyilong.ore.theme.StyleBoxFlat)button.getPresentationStyle(true,true)).color() == 0x1A000000);
        scrolledCapture();
        iconLayout();
        pressedLayout();
        pressedFocus();
        controlledState();
    }

    private static void pressedLayout() {
        io.github.luyingyilong.ore.layout.Size icon=new io.github.luyingyilong.ore.layout.Size(7,7);
        for (int height : new int[]{18, 20, 22, 32}) {
            Rect area = new Rect(10, 30, 120, height);
            Button button=new Button(cue -> { });
            var normalStyle=button.getPresentationStyle(false,false);
            var pressedStyle=button.getPresentationStyle(false,true);
            check(normalStyle.getBackgroundRect(area).equals(area));
            Rect pressedBackground=pressedStyle.getBackgroundRect(area);
            check(pressedBackground.y() == area.y() + 2 && pressedBackground.height() == height - 2);
            check(pressedBackground.bottom() == area.bottom());
            for (Button.TextAlignment alignment : Button.TextAlignment.values()) {
                Button.ContentLayout normal = Button.contentLayout(area, normalStyle, 24, true, icon, icon, alignment);
                Button.ContentLayout pressed = Button.contentLayout(area, pressedStyle, 24, true, icon, icon, alignment);
                check(pressed.label().equals(normal.label().translated(0, 2)));
                check(pressed.icon().equals(normal.icon().translated(0, 2)));
                check(pressed.trailingIcon().equals(normal.trailingIcon().translated(0, 2)));
            }
            Button.ContentLayout iconOnly = Button.contentLayout(area, pressedStyle, 0, false, icon, null, Button.TextAlignment.CENTER);
            Button.ContentLayout normalIcon = Button.contentLayout(area, normalStyle, 0, false, icon, null, Button.TextAlignment.CENTER);
            check(iconOnly.icon().equals(normalIcon.icon().translated(0, 2)));
        }
        check(new Button(cue -> { }).getPresentationStyle(false,true).getBackgroundRect(new Rect(0,0,10,1)).height()==0);
    }

    private static void pressedFocus() {
        io.github.luyingyilong.ore.layout.Size icon=new io.github.luyingyilong.ore.layout.Size(7,7);
        for (String type : Button.BUILTIN_VARIATIONS) {
            int offset = type.equals("TextButton") || type.equals("MenuButton") || type.equals("DarkButton") ? 0 : 2;
            Button button = new Button(cue -> { }).setThemeTypeVariation(type);
            button.setPosition(20, 20).setRequestedSize(100, 20);
            var childContainer8 = new Control();
            childContainer8.addChild(button);
            Window window = new Window(true).setContent(childContainer8);
            window.layoutViewport(160, 80, null);
            window.getFocusManager().requestFocus(button, null);
            window.getFocusManager().keyboardNavigation();
            Rect normal = window.getFocusManager().getOutlineBounds();
            check(normal.equals(new Rect(19, 19, 102, 22)));
            io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
            Rect held = window.getFocusManager().getOutlineBounds();
            check(held.y() == normal.y() + offset && held.bottom() == normal.bottom());
            check(held.x() == normal.x() && held.width() == normal.width());
            io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
            check(window.getFocusManager().getOutlineBounds().equals(normal));

            button.setToggleButton(true).setButtonPressedNoSignal(true);
            check(window.getFocusManager().getOutlineBounds().y() == normal.y() + offset);
            check(window.getFocusManager().getOutlineBounds().bottom() == normal.bottom());
            button.setButtonPressedNoSignal(false).setToggleButton(false);
            io.github.luyingyilong.ore.event.WindowInputs.press(window,25, 25, null);
            check(button.getFocusBounds().y() == 20 + offset);
            io.github.luyingyilong.ore.event.WindowInputs.move(window,159, 70);
            check(button.getFocusBounds().equals(button.getAbsoluteBounds()));
            io.github.luyingyilong.ore.event.WindowInputs.move(window,25, 25);
            check(button.getFocusBounds().y() == 20 + offset);
            io.github.luyingyilong.ore.event.WindowInputs.release(window,159, 70);
            check(button.getFocusBounds().equals(button.getAbsoluteBounds()));

            Rect area = button.getAbsoluteBounds();
            Button.ContentLayout before = Button.contentLayout(area, button.getPresentationStyle(false,false), 24, true, icon, icon, Button.TextAlignment.CENTER);
            Button.ContentLayout pressed = Button.contentLayout(area, button.getPresentationStyle(false,true), 24, true, icon, icon, Button.TextAlignment.CENTER);
            check(pressed.label().equals(before.label().translated(0, offset)));
            check(pressed.icon().equals(before.icon().translated(0, offset)));
            check(pressed.trailingIcon().equals(before.trailingIcon().translated(0, offset)));
            if (offset == 0) check(button.getPresentationStyle(false,true).getBackgroundRect(area).equals(area));
            button.setToggleButton(true).setButtonPressedNoSignal(true).setDisabled(true);
            check(button.getFocusBounds().equals(area));
        }
    }

    private static void controlledState() {
        List<String> events = new ArrayList<>();
        Button button = new Button(cue -> events.add(cue.name()));
        button.pressed().connect(button, () -> events.add("pressed:" + button.getButtonPressed()));
        button.toggled().connect(button, value -> events.add("toggled:" + value));
        check(!button.getDisabled() && !button.getToggleButton() && !button.getButtonPressed());
        button.setButtonPressed(true);
        check(!button.getButtonPressed() && events.isEmpty());
        button.setButtonPressed(false);
        check(events.isEmpty());
        button.setToggleButton(true).setButtonPressed(true).setButtonPressed(true);
        check(button.getButtonPressed() && events.equals(List.of("toggled:true")));
        button.onFocusChanged(false);
        check(button.getButtonPressed()); // Focus loss cancels gestures, not retained state.
        button.setButtonPressedNoSignal(false).setButtonPressedNoSignal(true);
        check(events.size() == 1);
        button.activate();
        check(!button.getButtonPressed() && events.equals(List.of("toggled:true", "CLICK", "toggled:false", "pressed:false")));

        events.clear();
        button.setPosition(0, 0).setRequestedSize(30, 20);
        var childContainer9 = new Control();
        childContainer9.addChild(button);
        Window window = new Window(true).setContent(childContainer9);
        window.layoutViewport(60, 60, null);
        io.github.luyingyilong.ore.event.WindowInputs.press(window,5, 5, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,50, 50);
        check(events.isEmpty() && !button.getButtonPressed());
        io.github.luyingyilong.ore.event.WindowInputs.press(window,5, 5, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,5, 5);
        check(button.getButtonPressed() && events.equals(List.of("CLICK", "toggled:true", "pressed:true")));
        events.clear();
        io.github.luyingyilong.ore.event.WindowInputs.press(window,5, 5, null);
        button.setDisabled(true).setDisabled(false); // Disabling while held must disarm the release.
        io.github.luyingyilong.ore.event.WindowInputs.release(window,5, 5);
        check(button.getButtonPressed() && events.isEmpty());
        button.setDisabled(true);
        check(!button.acceptsFocus() && !button.activate());
        check(!button.onActivationPressed(GLFW.GLFW_KEY_ENTER));
        io.github.luyingyilong.ore.event.WindowInputs.press(window,5, 5, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,5, 5);
        check(events.isEmpty());
        button.setButtonPressedNoSignal(false);
        check(!button.getButtonPressed());
        button.setDisabled(false).setButtonPressedNoSignal(true).setToggleButton(false);
        check(!button.getButtonPressed());
    }

    private static void iconLayout() {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText value) { return value.getString().length() * 6; }
        };
        io.github.luyingyilong.ore.layout.Size iconSize=new io.github.luyingyilong.ore.layout.Size(8,8);
        TextureSource icon = TextureSource.region(TextureRegion.full(io.github.luyingyilong.ore.theme.IconTheme.SMALL_CLOSE.defaultValue().getTexture(), 8, 8));
        Button button = new Button(cue -> { }).setSize(Button.Size.SMALL).setIcon(icon);
        check(button.getIcon().equals(icon));
        check(button.measureWidth(font, 100) == 18 && button.measureHeight(font, 100) == 18);
        Button.ContentLayout centered = Button.contentLayout(new Rect(0, 0, 18, 18),
                0, false, iconSize, null, Button.TextAlignment.LEFT);
        check(centered.icon().equals(new Rect(5, 5, 8, 8)) && centered.label().width() == 0);
        button.setLabel(Component.literal("AB"));
        check(button.measureWidth(font, 100) == 40);
        Button.ContentLayout mixed = Button.contentLayout(new Rect(0, 0, 60, 20),
                12, true, iconSize, null, Button.TextAlignment.CENTER);
        check(mixed.icon().equals(new Rect(18, 6, 8, 8)));
        check(mixed.label().equals(new Rect(30, 0, 12, 20)));
        Button.ContentLayout narrow = Button.contentLayout(new Rect(0, 0, 40, 20),
                100, true, iconSize, null, Button.TextAlignment.RIGHT);
        check(narrow.label().right() == 32 && narrow.label().width() == 12);
        button.setTrailingIcon(icon);
        check(button.measureWidth(font, 100) == 52);
        Button.ContentLayout trailing = Button.contentLayout(new Rect(0, 0, 60, 20),
                12, true, iconSize, iconSize, Button.TextAlignment.CENTER);
        check(trailing.trailingIcon().equals(new Rect(44, 6, 8, 8)));
        check(trailing.label().right() + 4 <= trailing.trailingIcon().x());
        Control parent = new Control();
        parent.addChild(button);
        parent.layout(new Rect(0, 0, 100, 20), font);
        button.setIcon(null);
        check(button.getIcon() == null && parent.getLayoutDirty());
        try {
            TextureSource.region(TextureRegion.full(io.github.luyingyilong.ore.theme.IconTheme.CLOSE.defaultValue().getTexture(), 0, 7));
            throw new AssertionError("Invalid icon dimensions accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static void scrolledCapture() {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText value) { return value.getString().length() * 6; }
        };
        int[] actions = {0};
        Button button = Signals.configure(new Button(cue -> { }), connected -> connected.pressed().connect(connected, () -> actions[0]++));
        button.setPosition(10, 80).setRequestedSize(30, 20);
        Control content = new Control().setPreferred(80, 200);
        content.addChild(button);
        Scroll scroll = new Scroll().setContent(content);
        Window window = new Window(true).setContent(scroll);
        window.layoutViewport(80, 60, font);
        scroll.scrollBy(0, 70);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15) && actions[0] == 1);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,15, 15, null));
        scroll.scrollBy(0, 50);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,15, 15);
        check(actions[0] == 1);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected button action, capture or feedback");
    }
}
