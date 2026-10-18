package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Padding;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Scroll;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
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
        Menu menu = new Menu(null, () -> new Button(cue -> { }))
                .onSelect(item -> actions.add(item.key()));
        MenuItem first = MenuItem.item("first", Component.literal("First"));
        MenuItem second = MenuItem.item("second", Component.literal("Second"));
        menu.items(List.of(first, second, MenuItem.group("group", Component.literal("Group"), List.of(
                MenuItem.item("third", Component.literal("Third")),
                MenuItem.item("disabled", Component.literal("Disabled")).disabled(true)))));
        menu.layout(new Rect(0, 0, 128, 150), font);
        check(menu.children().size() == 1 && menu.children().get(0) instanceof Box, "Box root without outer panel or padding");
        Box sections = (Box) menu.children().get(0);
        check(sections.sizeFlagsHorizontal() == SizeFlags.SIZE_EXPAND_FILL
                && sections.sizeFlagsVertical() == SizeFlags.SIZE_EXPAND_FILL, "Expanding sections");
        Rect a = menu.button("first").absoluteBounds(), b = menu.button("second").absoluteBounds();
        check(a.x() == 0 && a.y() == 0 && a.width() == 128 && a.height() == 20, "Item area without outer inset");
        check(b.y() == a.bottom(), "No item gaps");
        Box group = (Box) sections.children().get(1);
        check(group.absoluteBounds().y() == b.bottom() + 8, "Section gap");
        Padding heading = (Padding) group.children().get(0);
        check(heading.insets().equals(new Padding.Insets(0, Button.PADDING_X, 0, Button.PADDING_X)), "Heading inset");
        check(group.children().get(1) instanceof Divider && group.children().get(1).size().height() == 2, "Themed divider");
        check(menu.button("third").absoluteBounds().y() == group.children().get(1).absoluteBounds().bottom(), "Group children");
        check(menu.button("third").type() == Button.Type.MENU, "Real MENU buttons");
        check(!Button.paintsThemedBackground(Button.Type.MENU, false, false)
                && Button.paintsThemedBackground(Button.Type.MENU, true, false)
                && Button.paintsThemedBackground(Button.Type.MENU, false, true), "Menu states");

        Scroll scroll = new Scroll().content(menu).horizontal(Scroll.BarMode.DISABLED);
        Window window = new Window(true).content(scroll);
        window.layoutViewport(128, 60, font);
        check(scroll.maxScrollY() > 0, "Overflow scrolls");
        check(window.pointerPressed(3, 10, null) && actions.isEmpty(), "Press waits for release");
        window.pointerReleased(3, 30);
        check(actions.isEmpty(), "Dragging onto another item cancels");
        window.pointerPressed(3, 10, null); window.pointerReleased(3, 10);
        check(actions.equals(List.of("first")) && menu.button("first").buttonPressed(), "Selected action");
        menu.selectedKey("second");
        check(!menu.button("first").buttonPressed() && menu.button("second").buttonPressed()
                && actions.size() == 1, "Controlled selection has no callback");
        check(!menu.button("disabled").activate(), "Disabled item");
        window.focusManager().requestFocus(menu.button("first"), null);
        window.keyPressed(GLFW.GLFW_KEY_DOWN, 0, null);
        check(window.focusManager().focused() == menu.button("second") && actions.size() == 1, "Keyboard navigation");
        window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions.size() == 1, "Keyboard press waits");
        window.keyReleased(GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions.equals(List.of("first", "second")), "Keyboard release activates");

        List<MenuItem> many = IntStream.range(0, 50).mapToObj(i -> MenuItem.item("mail-" + i,
                Component.literal("A very long mail title " + i))).toList();
        menu.items(many); window.layoutViewport(128, 60, font); scroll.scrollToTop();
        scroll.setScroll(0, 108);
        Button retained = menu.button("mail-5");
        List<MenuItem> updated = new ArrayList<>(many);
        updated.add(0, MenuItem.item("new", Component.literal("New arrival")));
        menu.items(updated); window.layoutViewport(128, 60, font);
        check(menu.button("mail-5") == retained, "Stable keys reuse buttons");
        check(scroll.scrollY() == 128, "New arrival preserves visible mail anchor");
        for (int width : new int[]{127, 128, 129, 640}) {
            window.layoutViewport(width, 60, font);
            check(scroll.maxScrollX() == 0, "Long text stays within width");
            check(menu.button("mail-5").size().width() == scroll.viewport().width(), "Responsive width");
        }
        scroll.setScroll(0, scroll.maxScrollY());
        menu.items(List.of(first)); window.layoutViewport(128, 60, font);
        check(scroll.scrollY() == 0 && menu.selectedKey() == null, "Removed anchors clamp safely");
        menu.items(List.of(MenuItem.group("off", Component.literal("Disabled group"), List.of(first)).disabled(true)));
        check(menu.button("first").disabled(), "Disabled group propagates");
        try {
            menu.items(List.of(first, MenuItem.group("group", Component.empty(), List.of(first))));
            throw new AssertionError("Duplicate key accepted");
        } catch (IllegalArgumentException expected) {
            check(menu.items().get(0).key().equals("off"), "Invalid data preserves previous tree");
        }
        menu.items(List.of()); window.layoutViewport(128, 60, font);
        check(menu.button("first") == null && menu.contentExtent().height() >= menu.minimumHeight(), "Empty state");
        menu.items(List.of(MenuItem.group("empty-group", Component.literal("All"),
                List.of(MenuItem.empty("empty-row", Component.literal("No mail"))))));
        window.layoutViewport(128, 60, font);
        check(menu.button("empty-row") == null, "Empty rows are not buttons");
        Box emptyGroup = (Box) sections.children().get(0);
        Control emptyRow = emptyGroup.children().get(2).children().get(0).children().get(0);
        Label emptyLabel = (Label) emptyRow.children().get(0);
        check(emptyLabel.color() == OreTheme.TEXT_WHITE && !emptyLabel.acceptsFocus(), "White informational label");
        menu.emptyColor(0xFF12AB34);
        check(emptyLabel.color() == 0xFF12AB34, "Empty row color can change after construction");
        Rect emptyArea = emptyLabel.absoluteBounds();
        int previousActions = actions.size();
        window.pointerPressed(emptyArea.x() + 1, emptyArea.y() + 1, null);
        window.pointerReleased(emptyArea.x() + 1, emptyArea.y() + 1);
        check(actions.size() == previousActions && menu.selectedKey() == null, "Empty rows have no action");
        menu.items(List.of());
        Label wholeEmpty = (Label) sections.children().get(0);
        check(wholeEmpty.color() == 0xFF12AB34, "Global and grouped empty states share color");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
