package io.github.luyingyilong.ore.layout;


import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Checkbox;
import io.github.luyingyilong.ore.Table;
import io.github.luyingyilong.ore.Segmented;
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
        content.addChild(left);
        content.addChild(right);
        content.addChild(bottom);
        Overlay overlay = new Overlay();
        window.setContent(content).setOverlay(overlay);
        window.layoutViewport(100, 100, null);

        FocusManager focus = window.getFocusManager();
        focus.requestFocus(left, null);
        check(focus.getOutlineBounds() == null);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null));
        check(focus.getFocused() == right);
        check(focus.getOutlineBounds().equals(new Rect(39, 9, 22, 22)));
        check(new Checkbox(Component.literal("check")).getFocusExpansion() == 1);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_LEFT, 0, null));
        check(focus.getFocused() == left);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(window,45, 12, null));
        check(focus.getFocused() == right);
        check(focus.getOutlineBounds() == null);
        window.getFocusManager().pointerActivity();
        check(focus.getOutlineBounds() == null);
        focus.requestFocus(left, null);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_DOWN, 0, null));
        check(focus.getFocused() == bottom);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_TAB, 0, null));
        check(focus.getFocused() == left);

        Popup popup = new Popup();
        Button action = button(12, 12);
        popup.addChild(action);
        overlay.addChild(popup);
        window.layoutViewport(100, 100, null);
        check(focus.getFocused() == action);
        check(!io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null));
        popup.setVisible(false);
        check(focus.getFocused() == left);

        left.setVisible(false);
        check(focus.getFocused() == null);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_TAB, 0, null));
        check(focus.getFocused() == right);
        virtualGridKeepsItsItem();
        tableActionsCanBeFocused();
        selectedOptionFocusFollowsTexture();
        nativeFocusLossClearsOreFocus();
    }

    private static void virtualGridKeepsItsItem() {
        List<Integer> items = new ArrayList<>();
        for (int index = 0; index < 20; index++) items.add(index);
        Grid<Integer> grid = new Grid<>();
        grid.setColumns(20, 1, 0).setAdapter(new Grid.Adapter<>() {
            @Override public int getSize() { return items.size(); }
            @Override public Integer getItem(int index) { return items.get(index); }
            @Override public Object getKey(Integer item) { return item; }
            @Override public int measureHeight(Integer item, int width, Font font) { return 20; }
            @Override public Grid.View<Integer> createView() {
                return new Grid.View<>() {
                    @Override public void bind(Integer item) { }
                    @Override public void draw(ModularGuiContext context, Rect area, boolean hovered) { }
                };
            }
        });
        Scroll scroll = new Scroll().setContent(grid);
        Window window = new Window(true).setContent(scroll);
        window.layoutViewport(30, 40, null);
        window.getFocusManager().requestFocus(grid, null);
        for (int index = 0; index < 5; index++)
            check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_DOWN, 0, null));
        check(scroll.getScrollY() > 0);
        int oldY = grid.getFocusBounds().y();
        items.remove(0);
        grid.invalidate();
        window.layoutViewport(30, 40, null);
        check(grid.getFocusBounds().y() == oldY - 20);
    }

    private static void tableActionsCanBeFocused() {
        int[] activated = {0};
        Table<Integer> table = new Table<Integer>()
                .setRowKey(value -> value)
                .addColumn(new Table.Column<>("action", Component.literal("Action"),
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
        table.setRows(List.of(1, 2));
        Window window = new Window(true).setContent(table);
        window.layoutViewport(120, 80, null);
        window.getFocusManager().requestFocus(table, null);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null));
        check(activated[0] == 0);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(activated[0] == 1);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_DOWN, 0, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null));
        check(activated[0] == 1);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(activated[0] == 2);
    }

    private static Button button(int x, int y) {
        Button button = new Button(Component.literal("x"));
        button.setPosition(x, y).setRequestedSize(20, 20);
        return button;
    }

    private static void selectedOptionFocusFollowsTexture() {
        Segmented segmented = new Segmented(List.of(Component.literal("First"), Component.literal("Second")));
        int[] selected = {0};
        segmented.selectionChanged().connect(segmented, index -> selected[0]++);
        Window window = new Window(true).setContent(segmented);
        window.layoutViewport(100, 20, null);
        check(segmented.getFocusBounds().equals(new Rect(0, 2, 51, 18)));
        window.getFocusManager().requestFocus(segmented, null);
        check(selected[0] == 0);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null));
        check(segmented.getSelected() == 0 && selected[0] == 0);
        check(segmented.getFocusBounds().equals(new Rect(50, 0, 51, 20)));
        segmented.setSelected(0); // Market synchronizes the active page on every layout pass.
        window.layoutViewport(100, 20, null);
        check(segmented.getFocusBounds().equals(new Rect(50, 0, 51, 20)));
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_LEFT, 0, null));
        check(segmented.getFocusBounds().equals(new Rect(0, 2, 51, 18)));
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null));
        check(io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null));
        check(segmented.getSelected() == 0 && selected[0] == 0);
        check(io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null));
        check(segmented.getSelected() == 1 && selected[0] == 1);
        check(segmented.getFocusBounds().equals(new Rect(50, 2, 51, 18)));
    }

    private static void nativeFocusLossClearsOreFocus() {
        Window window = new Window(true);
        Control content = new Control();
        Control input = new Control().setFocusable(true);
        Button button = button(40, 0);
        content.addChild(input);
        content.addChild(button);
        window.setContent(content);
        window.layoutViewport(100, 30, null);
        FocusManager focus = window.getFocusManager();
        focus.requestFocus(input, null);
        check(focus.getFocused() == input);
        focus.nativeFocusLost(input);
        check(focus.getFocused() == null);
        focus.requestFocus(input, null);
        focus.pointerPressed(button, null);
        check(focus.getFocused() == button);
        focus.nativeFocusLost(input);
        check(focus.getFocused() == button);
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Focus navigation regression");
    }

    private static final class Popup extends Control {
        @Override public boolean getFocusScope() { return true; }
    }
}
