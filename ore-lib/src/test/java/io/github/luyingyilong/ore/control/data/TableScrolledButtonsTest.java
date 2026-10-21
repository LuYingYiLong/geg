package io.github.luyingyilong.ore.control.data;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.overlay.Drawer;
import io.github.luyingyilong.ore.control.overlay.Overlay;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.event.WindowInputs;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** All retained table actions survive Scroll's intermediate layout passes. */
public final class TableScrolledButtonsTest {
    private record Action(String id, String variation, boolean toggle) { }
    private static final class Cell extends Container {
        private Action action;
        private final Button button;
        Cell(List<String> events) {
            button = Signals.configure(io.github.luyingyilong.ore.control.input.ButtonTestAccess.create(cue -> { }), connected -> connected.pressed().connect(connected, () -> events.add(action.id())));
            this.addChild(button);
        }
        void bind(Action action) {
            this.action = action;
            button.setLabel(Component.literal(action.id())).setThemeTypeVariation(action.variation()).setToggleButton(action.toggle());
        }
        @Override public void layout(Rect area, Font font) {
            super.layout(area, font);
            fitChild(button, new Rect(0, 0, area.width(), area.height()), font);
        }
    }
    public static void main(String[] args) {
        var font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        List<String> events = new ArrayList<>();
        List<Action> data = List.of(new Action("Buy", "PrimaryButton", false),
                new Action("Favorite", "SecondaryButton", true),
                new Action("Approve", "PrimaryButton", false),
                new Action("Reject", "DestructiveButton", false));
        Table<Action> table = new Table<Action>().setRowKey(Action::id)
                .addColumn(new Table.Column<>("name", Component.empty(), 230, 230, 0,
                        width -> 23, (context, row, bounds) -> { }))
                .addColumn(new Table.Column<>("action", Component.empty(), 70, 70, 1,
                        width -> 23, new Table.ControlCell<>(() -> new Cell(events), Cell::bind)));
        table.setMinimum(300, 80);
        table.setRows(data);
        long[] time = {0};
        Drawer drawer = io.github.luyingyilong.ore.control.overlay.OverlayTestAccess.drawer(Component.literal("Actions"), () -> time[0], cue -> { }).setContent(table);
        var childContainer60 = new Overlay();
        childContainer60.addChild(drawer);
        Window window = Window.embedded().setContent(new Control()).setOverlay(childContainer60);
        drawer.open();time[0] = 1000;
        window.layoutViewport(400, 220, font);
        drawer.getBodyScroll().setScroll(drawer.getBodyScroll().getMaxScrollX(), 0);
        for (Action action : data) {
            Cell cell = table.getChildren().stream().flatMap(row -> row.getChildren().stream())
                    .filter(Cell.class::isInstance).map(Cell.class::cast)
                    .filter(value -> value.action.id().equals(action.id())).findFirst().orElseThrow();
            Rect area = EventGeometry.displayed(cell.button);
            int x = area.x() + area.width() / 2, y = area.y() + area.height() / 2;
            int before = events.size();
            check(WindowInputs.press(window, x, y, null), action.id()+" press");
            for (int frame=0; frame<3; frame++) window.layoutViewport(400, 220, font);
            check(events.size()==before, action.id()+" must wait for release");
            WindowInputs.release(window, x, y);
            check(events.size()==before+1 && events.get(before).equals(action.id()), action.id()+" release after layout");
            if(action.toggle())check(cell.button.getButtonPressed(), "Favorite stays toggled");
            WindowInputs.press(window, x, y, null);
            window.layoutViewport(400, 220, font);
            WindowInputs.release(window, 0, 0);
            check(events.size()==before+1, action.id()+" drag-out cancels");
            window.getFocusManager().restoreFocus(cell.button, null);
            WindowInputs.keyDown(window, GLFW.GLFW_KEY_SPACE, 0, null);
            window.layoutViewport(400, 220, font);
            WindowInputs.keyUp(window, GLFW.GLFW_KEY_SPACE, 0, null);
            check(events.size()==before+2, action.id()+" keyboard release after layout");
        }
        System.out.println("ORE_TABLE_SCROLLED_BUTTONS_PASS");
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
