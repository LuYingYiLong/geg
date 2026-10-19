package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Natural text widths and explicit expansion share the same painted/hit/focus geometry. */
public final class SegmentedLayoutTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        Segmented options = new Segmented(List.of(Component.literal("A"), Component.literal("Longer")));
        check(options.measureWidth(font, 500) == 24 + 52);
        Box row = new Box().setOrientation(Box.Orientation.HORIZONTAL).add(options)
                .add(new Control().setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL));
        Window window = new Window(true).setContent(row);
        window.layoutViewport(300, 20, font);
        check(options.getBounds().equals(new Rect(0, 0, 76, 20)));
        check(options.hitIndex(0, 0, 76, 23, 10) == 0 && options.hitIndex(0, 0, 76, 24, 10) == 1);
        window.getFocusManager().requestFocus(options, null);
        check(options.getFocusBounds().equals(new Rect(0, 2, 25, 18)));
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null);
        check(options.getFocusBounds().equals(new Rect(24, 0, 53, 20)) && options.getSelected() == 0);
        options.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        row.removeChild(row.getChildren().get(1));
        window.layoutViewport(300, 20, font);
        check(options.getBounds().width() == 300);
        // Each option receives half of the extra 224px, retaining its own text width.
        check(options.hitIndex(0, 0, 300, 135, 10) == 0 && options.hitIndex(0, 0, 300, 136, 10) == 1);
        options.setSelected(0);
        window.getFocusManager().requestFocus(null, null);
        window.layoutViewport(40, 20, font);
        window.getFocusManager().requestFocus(options, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null);
        window.layoutViewport(40, 20, font);
        check(options.getScrollX() == 24 && options.getSelected() == 0);
        window.layoutViewport(40, 20, font);
        check(options.getScrollX() == 24);
        check(io.github.luyingyilong.ore.EventFixture.press(options,39,10));
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError("Segmented sizing regression"); }
}
