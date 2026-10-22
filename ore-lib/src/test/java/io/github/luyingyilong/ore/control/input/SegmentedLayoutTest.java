package io.github.luyingyilong.ore.control.input;

import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.support.EventFixture;

import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.window.Window;
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
        Box row = new Box().setOrientation(Box.Orientation.HORIZONTAL);
        row.addChild(options);
        row.addChild(new Control().setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL));
        Window window = new Window(true).setContent(row);
        window.layoutViewport(300, 20, font);
        check(options.getBounds().equals(new Rect(0, 0, 76, 20)));
        check(options.hitIndex(0, 0, 76, 23, 10) == 0 && options.hitIndex(0, 0, 76, 24, 10) == 1);
        window.getFocusManager().requestFocus(options, null);
        check(options.getFocusBounds().equals(new Rect(0, 2, 24, 18)));
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null);
        check(options.getFocusBounds().equals(new Rect(24, 0, 52, 20)) && options.getSelected() == 0);
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
        check(io.github.luyingyilong.ore.support.EventFixture.press(options,39,10));
        labelAlignment();
        icons(font);
    }

    private static void icons(Font font) {
        Segmented options = new Segmented(List.of(Component.literal("First"), Component.literal("Second")));
        Window root = Window.embedded().setContent(options);
        var source = io.github.luyingyilong.ore.render.TextureSource.region(io.github.luyingyilong.ore.render.TextureRegion.full(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test", "icons/wide.png"), 16, 8));
        var parts = options.getOwnedParts();
        int[] changes = {0}; options.selectionChanged().connect(options, value -> changes[0]++);
        try {
            int natural = options.minimumRowWidth(font);
            check(options.getIcon(0) == null);
            options.setIcon(0, source);
            check(options.getIcon(0) == source && options.minimumRowWidth(font) == natural + 12);
            for (var style : List.of(options.getOptionStyle(0, false), options.getOptionStyle(1, false),
                    options.getOptionStyle(1, true), options.getThemeItem(Segmented.PRESSED))) {
                Rect area = new Rect(10, 20, 80, 20), content = style.getContentDrawRect(area);
                options.layoutOption(0, area, style, font);
                Rect icon = parts.get("option_icon_0").getBounds(), label = parts.get("option_0").getBounds();
                check(icon.width() == 8 && icon.height() == 4 && icon.y() == content.y() + (content.height() - 4) / 2 - 2);
                check(label.y() == content.y() - 2 && label.x() == icon.right() + 4);
            }
            root.layoutViewport(128, 30, font);
            io.github.luyingyilong.ore.event.WindowInputs.press(root, 5, 5, null);
            options.setIcon(0, null);
            io.github.luyingyilong.ore.event.WindowInputs.release(root, 5, 5);
            check(changes[0] == 0 && options.minimumRowWidth(font) == natural);
            options.layoutOption(0, new Rect(0, 0, 80, 20), options.getOptionStyle(0, false), font);
            check(parts.get("option_icon_0").getBounds().width() == 0);
            for (int i = 0; i < 10; i++) options.setIcon(0, source).setIcon(0, null);
            check(options.getOwnedParts().equals(parts) && changes[0] == 0);
            try { options.setIcon(2, source); throw new AssertionError("Expected invalid option index"); }
            catch (IndexOutOfBoundsException expected) { check(options.getIcon(0) == null); }
            options.setIcon(1, source); root.layoutViewport(128, 30, font);
            io.github.luyingyilong.ore.event.WindowInputs.press(root, 120, 5, null);
            io.github.luyingyilong.ore.event.WindowInputs.release(root, 120, 5);
            check(options.getSelected() == 1 && changes[0] == 1);
        } finally { root.queueFree(); LifecycleScheduler.flush(); }
        check(parts.values().stream().allMatch(Control::isDisposed));
        try { options.setIcon(0, source); throw new AssertionError("Expected freed control rejection"); }
        catch (IllegalStateException expected) { }
    }

    private static void labelAlignment() {
        for (Button.Size size : Button.Size.values()) {
            Segmented options = new Segmented(List.of(Component.literal("玩家"), Component.literal("World")), size);
            for (int width : new int[]{24, 127, 128, 129, 300}) {
                Rect area = new Rect(10, 30, width, size.getHeight());
                for (var style : List.of(options.getOptionStyle(0, false), options.getOptionStyle(1, false),
                        options.getOptionStyle(1, true), options.getThemeItem(Segmented.PRESSED))) {
                    Rect label = Segmented.getOptionLabelBounds(area, style);
                    Rect buttonLabel = Button.contentLayout(area, style, 24, true, null, null,
                            Button.TextAlignment.CENTER).label();
                    Rect content = style.getContentDrawRect(area);
                    check(label.equals(buttonLabel));
                    check(label.y() == content.y() - 2 && label.height() == content.height());
                }
                Rect normal = Segmented.getOptionLabelBounds(area, options.getThemeItem(Segmented.NORMAL));
                Rect pressed = Segmented.getOptionLabelBounds(area, options.getThemeItem(Segmented.PRESSED));
                Rect selected = Segmented.getOptionLabelBounds(area, options.getOptionStyle(0, false));
                check(pressed.equals(normal.translated(0, 2)));
                check(selected.equals(normal.translated(0, 2)));
            }
            options.queueFree();
        }
        LifecycleScheduler.flush();
    }

    private static void check(boolean condition) { if (!condition) throw new AssertionError("Segmented sizing regression"); }
}
