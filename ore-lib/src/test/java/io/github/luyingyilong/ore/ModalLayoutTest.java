package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Control;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Placement checks independent of a live Minecraft client. */
public final class ModalLayoutTest {
    private ModalLayoutTest() {
    }

    public static void main(String[] args) {
        Rect centered = Modal.centeredBounds(200, 120, 427, 240);
        check(centered.equals(new Rect(113, 60, 200, 120)));

        Rect rightHalf = Modal.positionedBounds(213, 240, 214, 0, 427, 240);
        check(rightHalf.equals(new Rect(214, 0, 213, 240)));

        Rect clamped = Modal.positionedBounds(300, 300, 400, -10, 127, 128);
        check(clamped.equals(new Rect(0, 0, 127, 128)));
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        Modal modal = new Modal(Component.literal("Members"), null, null, null).setSize(180, 120)
                .setContent(new Control().setPreferred(40, 20));
        for (int width : new int[]{127, 128, 129, 640}) {
            modal.layout(new Rect(0, 0, width, 240), font);
            Button close = iconButton(modal);
            check(close != null && close.getSize().width() == 20 && close.getSize().height() == 20);
        }
    }

    private static Button iconButton(Control node) {
        if (node instanceof Button button && button.getIcon() != null) return button;
        for (Control child : node.getChildren()) {
            Button found = iconButton(child);
            if (found != null) return found;
        }
        return null;
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected Ore modal placement");
    }
}
