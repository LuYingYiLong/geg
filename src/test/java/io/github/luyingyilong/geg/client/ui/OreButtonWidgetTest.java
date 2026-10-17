package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.screen.OreButtonWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Vanilla-host input still runs Ore's release/cancellation/sound and controlled button logic. */
public final class OreButtonWidgetTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        List<String> events = new ArrayList<>();
        Badge unread = new Badge(7);
        Button button = new Button(cue -> events.add(cue.name())).label(Component.literal("Mail"))
                .badge(unread).onPressed(() -> events.add("open"));
        OreButtonWidget widget = new OreButtonWidget(button, Component.literal("Mail, 7 unread"));
        widget.place(font, 4, 50, 32);
        check(widget.button() == button && widget.getWidth() == 56 && widget.getY() == 50);
        var badge = unread.absoluteBounds();
        check(widget.mouseClicked(badge.x() + 1, badge.y() + 1, 0) && events.isEmpty());
        check(widget.mouseReleased(badge.x() + 1, badge.y() + 1, 0));
        check(events.equals(List.of("CLICK", "open")) && !widget.pointerHeld());
        events.clear();
        check(widget.mouseClicked(8, 54, 0));
        check(widget.mouseDragged(200, 200, 0, 192, 146));
        check(widget.mouseReleased(200, 200, 0) && events.isEmpty());
        check(!widget.mouseReleased(8, 54, 0));
        check(!widget.mouseClicked(8, 54, 1));
        check(!widget.mouseClicked(200, 200, 0));
        check(!widget.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        widget.setFocused(true);
        check(widget.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0) && events.isEmpty());
        widget.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(widget.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0));
        check(events.equals(List.of("CLICK", "open")));
        events.clear();
        widget.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
        widget.setFocused(false);
        check(!widget.keyReleased(GLFW.GLFW_KEY_SPACE, 0, 0) && events.isEmpty());
        widget.mouseClicked(8, 54, 0);
        widget.cancelInput();
        check(!widget.mouseReleased(8, 54, 0) && events.isEmpty());
        widget.active = false;
        check(!widget.mouseClicked(8, 54, 0));
        widget.active = true;
        button.disabled(true);
        check(!widget.mouseClicked(8, 54, 0));
        button.disabled(false);
        unread.count(120);
        widget.place(font, 4, 50, 32);
        check(unread.displayText().equals("99+") && widget.getWidth() == 66);
        unread.visible(false);
        widget.place(font, 4, 50, 32);
        check(widget.getWidth() == 40);
        button.offset(2, -1);
        widget.place(font, 4, 50, 32);
        check(widget.getRectangle().left() == 6 && widget.getRectangle().top() == 49);
        check(!widget.isMouseOver(4, 50) && widget.isMouseOver(6, 49));
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError("Vanilla Ore Button adapter regression"); }
}
