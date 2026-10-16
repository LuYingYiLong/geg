package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.Button;
import io.github.luyingyilong.geg.client.ui.Checkbox;
import io.github.luyingyilong.geg.client.ui.Table;
import io.github.luyingyilong.geg.client.ui.Tabs;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Standalone keyboard focus checks; run with java -ea and the client dependency classpath. */
public final class FocusNavigationTest {
    private FocusNavigationTest() { }

    public static void main(String[] args) {
        Window window = new Window(true);
        Control content = new Control();
        Button left = button(10, 10);
        Button right = button(40, 10);
        Button bottom = button(10, 42);
        content.addChild(left).addChild(right).addChild(bottom);
        Overlay overlay = new Overlay();
        window.content(content).overlay(overlay);
        window.layoutViewport(100, 100, null);

        FocusManager focus = window.focusManager();
        focus.requestFocus(left, null);
        check(focus.outlineBounds() == null);
        check(window.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, null));
        check(focus.focused() == right);
        check(focus.outlineBounds().equals(new Rect(39, 9, 22, 22)));
        check(new Checkbox(Component.literal("check")).focusExpansion() == 1);
        check(window.keyPressed(GLFW.GLFW_KEY_LEFT, 0, null));
        check(focus.focused() == left);
        check(window.pointerPressed(45, 12, null));
        check(focus.focused() == right);
        check(focus.outlineBounds() == null);
        window.focusManager().pointerActivity();
        check(focus.outlineBounds() == null);
        focus.requestFocus(left, null);
        check(window.keyPressed(GLFW.GLFW_KEY_DOWN, 0, null));
        check(focus.focused() == bottom);
        check(window.keyPressed(GLFW.GLFW_KEY_TAB, 0, null));
        check(focus.focused() == left);

        Popup popup = new Popup();
        Button action = button(12, 12);
        popup.addChild(action);
        overlay.add(popup);
        window.layoutViewport(100, 100, null);
        check(focus.focused() == action);
        check(!window.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, null));
        popup.visible(false);
        check(focus.focused() == left);

        left.visible(false);
        check(focus.focused() == null);
        check(window.keyPressed(GLFW.GLFW_KEY_TAB, 0, null));
        check(focus.focused() == right);
        virtualGridKeepsItsItem();
        tableActionsCanBeFocused();
        selectedTabFocusFollowsTexture();
        nativeFocusLossClearsOreFocus();
    }

    private static void virtualGridKeepsItsItem() {
        List<Integer> items = new ArrayList<>();
        for (int index = 0; index < 20; index++) items.add(index);
        Grid<Integer> grid = new Grid<>();
        grid.columns(20, 1, 0).adapter(new Grid.Adapter<>() {
            @Override public int size() { return items.size(); }
            @Override public Integer item(int index) { return items.get(index); }
            @Override public Object key(Integer item) { return item; }
            @Override public int measureHeight(Integer item, int width, Font font) { return 20; }
            @Override public Grid.View<Integer> createView() {
                return new Grid.View<>() {
                    @Override public void bind(Integer item) { }
                    @Override public void draw(ModularGuiContext context, Rect area, boolean hovered) { }
                };
            }
        });
        Scroll scroll = new Scroll().content(grid);
        Window window = new Window(true).content(scroll);
        window.layoutViewport(30, 40, null);
        window.focusManager().requestFocus(grid, null);
        for (int index = 0; index < 5; index++)
            check(window.keyPressed(GLFW.GLFW_KEY_DOWN, 0, null));
        check(scroll.scrollY() > 0);
        int oldY = grid.focusBounds().y();
        items.remove(0);
        grid.invalidate();
        window.layoutViewport(30, 40, null);
        check(grid.focusBounds().y() == oldY - 20);
    }

    private static void tableActionsCanBeFocused() {
        int[] activated = {0};
        Table<Integer> table = new Table<Integer>()
                .rowKey(value -> value)
                .column(new Table.Column<>("action", Component.literal("Action"),
                        40, 80, 1, width -> 20, new Table.Cell<>() {
                    @Override public void draw(ModularGuiContext context, Integer row, Rect bounds) { }
                    @Override public List<Rect> focusTargets(Integer row, Rect bounds) {
                        return List.of(new Rect(bounds.x() + 2, bounds.y() + 2, 20, 18));
                    }
                    @Override public boolean click(Integer row, Rect bounds, int x, int y) {
                        activated[0] = row;
                        return true;
                    }
                }));
        table.rows(List.of(1, 2));
        Window window = new Window(true).content(table);
        window.layoutViewport(120, 80, null);
        window.focusManager().requestFocus(table, null);
        check(window.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, null));
        check(window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null));
        check(activated[0] == 1);
        check(window.keyPressed(GLFW.GLFW_KEY_DOWN, 0, null));
        check(window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null));
        check(activated[0] == 2);
    }

    private static Button button(int x, int y) {
        Button button = new Button(Component.literal("x"));
        button.position(x, y).requestedSize(20, 20);
        return button;
    }

    private static void selectedTabFocusFollowsTexture() {
        Tabs tabs = new Tabs(List.of(Component.literal("First"), Component.literal("Second")));
        int[] selected = {0};
        tabs.onSelect(index -> selected[0]++);
        Window window = new Window(true).content(tabs);
        window.layoutViewport(100, 20, null);
        check(tabs.focusBounds().equals(new Rect(0, 2, 51, 18)));
        window.focusManager().requestFocus(tabs, null);
        check(selected[0] == 0);
        check(window.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, null));
        check(tabs.selected() == 0 && selected[0] == 0);
        check(tabs.focusBounds().equals(new Rect(50, 0, 51, 20)));
        tabs.selected(0); // Market synchronizes the active page on every layout pass.
        window.layoutViewport(100, 20, null);
        check(tabs.focusBounds().equals(new Rect(50, 0, 51, 20)));
        check(window.keyPressed(GLFW.GLFW_KEY_LEFT, 0, null));
        check(tabs.focusBounds().equals(new Rect(0, 2, 51, 18)));
        check(window.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, null));
        check(window.keyPressed(GLFW.GLFW_KEY_ENTER, 0, null));
        check(tabs.selected() == 1 && selected[0] == 1);
        check(tabs.focusBounds().equals(new Rect(50, 2, 51, 18)));
    }

    private static void nativeFocusLossClearsOreFocus() {
        Window window = new Window(true);
        Control content = new Control();
        Control input = new Control().focusable(true);
        Button button = button(40, 0);
        content.addChild(input).addChild(button);
        window.content(content);
        window.layoutViewport(100, 30, null);
        FocusManager focus = window.focusManager();
        focus.requestFocus(input, null);
        check(focus.focused() == input);
        focus.nativeFocusLost(input);
        check(focus.focused() == null);
        focus.requestFocus(input, null);
        focus.pointerPressed(button, null);
        check(focus.focused() == button);
        focus.nativeFocusLost(input);
        check(focus.focused() == button);
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Focus navigation regression");
    }

    private static final class Popup extends Control {
        @Override public boolean focusScope() { return true; }
    }
}
