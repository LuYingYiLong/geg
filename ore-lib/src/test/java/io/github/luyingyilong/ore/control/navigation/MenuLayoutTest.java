package io.github.luyingyilong.ore.control.navigation;

import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.layout.Divider;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.layout.Padding;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.window.Window;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Menu composition, shared Button behavior, refresh anchors and scroll clipping. */
public final class MenuLayoutTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
            @Override public List<net.minecraft.util.FormattedCharSequence> split(FormattedText text, int width) {
                return List.of(net.minecraft.util.FormattedCharSequence.forward(text.getString(), net.minecraft.network.chat.Style.EMPTY));
            }
        };
        List<String> actions = new ArrayList<>();
        Menu menu = Signals.configure(new Menu(null, () -> io.github.luyingyilong.ore.control.input.ButtonTestAccess.create(cue -> { })), connected -> connected.itemActivated().connect(connected, item -> actions.add(item.key())));
        MenuItem first = MenuItem.item("first", Component.literal("First"));
        MenuItem second = MenuItem.item("second", Component.literal("Second"));
        menu.setItems(List.of(first, second, MenuItem.group("group", Component.literal("Group"), List.of(
                MenuItem.item("third", Component.literal("Third")),
                MenuItem.item("disabled", Component.literal("Disabled")).disabled(true)))));
        menu.layout(new Rect(0, 0, 128, 150), font);
        check(menu.getChildren().size() == 1 && menu.getChildren().get(0) instanceof Box, "Box root without outer panel or padding");
        Box sections = (Box) menu.getChildren().get(0);
        check(sections.getSizeFlagsHorizontal() == SizeFlags.SIZE_EXPAND_FILL
                && sections.getSizeFlagsVertical() == SizeFlags.SIZE_EXPAND_FILL, "Expanding sections");
        Rect a = menu.getButton("first").getAbsoluteBounds(), b = menu.getButton("second").getAbsoluteBounds();
        check(a.x() == 0 && a.y() == 0 && a.width() == 128 && a.height() == 20, "Item area without outer inset");
        check(b.y() == a.bottom(), "No item gaps");
        Box group = (Box) sections.getChildren().get(1);
        check(group.getAbsoluteBounds().y() == b.bottom() + 8, "Section gap");
        Padding heading = (Padding) group.getChildren().get(0);
        check(heading.getInsets().equals(new Padding.Insets(0, Button.NORMAL.defaultValue().getContentMargin().left(), 0, Button.NORMAL.defaultValue().getContentMargin().left())), "Heading inset");
        check(group.getChildren().get(1) instanceof Divider && group.getChildren().get(1).getSize().height() == 2, "Themed divider");
        check(menu.getButton("third").getAbsoluteBounds().y() == group.getChildren().get(1).getAbsoluteBounds().bottom(), "Group children");
        check(menu.getButton("third").getThemeTypeVariation().equals("MenuButton"), "Real MENU buttons");
        check(io.github.luyingyilong.ore.control.input.ButtonTestAccess.style(menu.getButton("third"),false,false) instanceof io.github.luyingyilong.ore.theme.StyleBoxFlat flat && flat.color()==0, "Menu resting background is transparent");

        Scroll scroll = new Scroll().setContent(menu).setHorizontal(Scroll.BarMode.DISABLED);
        Window window = new Window(true).setContent(scroll);
        window.layoutViewport(128, 60, font);
        check(scroll.getMaxScrollY() > 0, "Overflow scrolls");
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,3, 10, null) && actions.isEmpty(), "Press waits for release");
        io.github.luyingyilong.ore.event.WindowInputs.release(window,3, 30);
        check(actions.isEmpty(), "Dragging onto another item cancels");
        io.github.luyingyilong.ore.event.WindowInputs.press(window,3, 10, null); io.github.luyingyilong.ore.event.WindowInputs.release(window,3, 10);
        check(actions.equals(List.of("first")) && menu.getButton("first").getButtonPressed(), "Selected action");
        menu.setSelectedKey("second");
        check(!menu.getButton("first").getButtonPressed() && menu.getButton("second").getButtonPressed()
                && actions.size() == 1, "Controlled selection has no callback");
        check(!menu.getButton("disabled").activate(), "Disabled item");
        window.getFocusManager().requestFocus(menu.getButton("first"), null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_DOWN, 0, null);
        check(window.getFocusManager().getFocused() == menu.getButton("second") && actions.size() == 1, "Keyboard navigation");
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions.size() == 1, "Keyboard press waits");
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions.equals(List.of("first", "second")), "Keyboard release activates");

        List<MenuItem> many = IntStream.range(0, 50).mapToObj(i -> MenuItem.item("mail-" + i,
                Component.literal("A very long mail title " + i))).toList();
        menu.setItems(many); window.layoutViewport(128, 60, font); scroll.scrollToTop();
        scroll.setScroll(0, 108);
        Button retained = menu.getButton("mail-5");
        List<MenuItem> updated = new ArrayList<>(many);
        updated.add(0, MenuItem.item("new", Component.literal("New arrival")));
        menu.setItems(updated); window.layoutViewport(128, 60, font);
        check(menu.getButton("mail-5") == retained, "Stable keys reuse buttons");
        check(scroll.getScrollY() == 128, "New arrival preserves visible mail anchor");
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 60, font);
            check(scroll.getMaxScrollX() == 0, "Long text stays within width");
            check(menu.getButton("mail-5").getSize().width() == scroll.getViewport().width(), "Responsive width");
        }
        scroll.setScroll(0, scroll.getMaxScrollY());
        menu.setItems(List.of(first)); window.layoutViewport(128, 60, font);
        check(scroll.getScrollY() == 0 && menu.getSelectedKey() == null, "Removed anchors clamp safely");
        menu.setItems(List.of(MenuItem.group("off", Component.literal("Disabled group"), List.of(first)).disabled(true)));
        check(menu.getButton("first").getDisabled(), "Disabled group propagates");
        try {
            menu.setItems(List.of(first, MenuItem.group("group", Component.empty(), List.of(first))));
            throw new AssertionError("Duplicate key accepted");
        } catch (IllegalArgumentException expected) {
            check(menu.getItems().get(0).key().equals("off"), "Invalid data preserves previous tree");
        }
        menu.setItems(List.of()); window.layoutViewport(128, 60, font);
        check(menu.getButton("first") == null && menu.getContentExtent().height() >= menu.getMinimumHeight(), "Empty state");
        menu.setItems(List.of(MenuItem.group("empty-group", Component.literal("All"),
                List.of(MenuItem.empty("empty-row", Component.literal("No mail"))))));
        window.layoutViewport(128, 60, font);
        check(menu.getButton("empty-row") == null, "Empty rows are not buttons");
        Box emptyGroup = (Box) sections.getChildren().get(0);
        Control emptyRow = emptyGroup.getChildren().get(2).getChildren().get(0).getChildren().get(0);
        Label emptyLabel = (Label) emptyRow.getChildren().get(0);
        check(emptyLabel.getThemeItem(io.github.luyingyilong.ore.control.display.Label.FONT_COLOR) == OreTheme.TEXT_WHITE && !emptyLabel.acceptsFocus(), "White informational label");
        menu.setThemeOverride(Menu.EMPTY_FONT_COLOR, 0xFF12AB34);
        check(emptyLabel.getThemeItem(io.github.luyingyilong.ore.control.display.Label.FONT_COLOR) == 0xFF12AB34, "Empty row color can change after construction");
        Rect emptyArea = emptyLabel.getAbsoluteBounds();
        int previousActions = actions.size();
        io.github.luyingyilong.ore.event.WindowInputs.press(window,emptyArea.x() + 1, emptyArea.y() + 1, null);
        io.github.luyingyilong.ore.event.WindowInputs.release(window,emptyArea.x() + 1, emptyArea.y() + 1);
        check(actions.size() == previousActions && menu.getSelectedKey() == null, "Empty rows have no action");
        menu.setItems(List.of());
        Label wholeEmpty = (Label) sections.getChildren().get(0);
        check(wholeEmpty.getThemeItem(io.github.luyingyilong.ore.control.display.Label.FONT_COLOR) == 0xFF12AB34, "Global and grouped empty states share color");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
