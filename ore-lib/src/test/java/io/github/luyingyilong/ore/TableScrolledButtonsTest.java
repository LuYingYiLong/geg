package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.event.EventGeometry;
import io.github.luyingyilong.ore.event.WindowInputs;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** All retained table actions survive Scroll's intermediate layout passes. */
public final class TableScrolledButtonsTest {
    private record Action(String id, Button.Type type, boolean toggle) { }
    private static final class Cell extends Container {
        private Action action;
        private final Button button;
        Cell(List<String> events) {
            button = Signals.configure(new Button(cue -> { }), connected -> connected.pressed().connect(connected, () -> events.add(action.id())));
            addChild(button);
        }
        void bind(Action action) {
            this.action = action;
            button.label(Component.literal(action.id())).type(action.type()).toggleButton(action.toggle());
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
        List<Action> data = List.of(new Action("Buy", Button.Type.PRIMARY, false),
                new Action("Favorite", Button.Type.SECONDARY, true),
                new Action("Approve", Button.Type.PRIMARY, false),
                new Action("Reject", Button.Type.DESTRUCTIVE, false));
        Table<Action> table = new Table<Action>().rowKey(Action::id)
                .column(new Table.Column<>("name", Component.empty(), 230, 230, 0,
                        width -> 23, (context, row, bounds) -> { }))
                .column(new Table.Column<>("action", Component.empty(), 70, 70, 1,
                        width -> 23, new Table.ControlCell<>(() -> new Cell(events), Cell::bind)));
        table.minimum(300, 80);
        table.rows(data);
        long[] time = {0};
        Drawer drawer = new Drawer(Component.literal("Actions"), () -> time[0], cue -> { }).content(table);
        Window window = Window.embedded().content(new Control()).overlay(new Overlay().add(drawer));
        drawer.open();time[0] = 1000;
        window.layoutViewport(400, 220, font);
        drawer.bodyScroll().setScroll(drawer.bodyScroll().maxScrollX(), 0);
        for (Action action : data) {
            Cell cell = table.children().stream().flatMap(row -> row.children().stream())
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
            if(action.toggle())check(cell.button.buttonPressed(), "Favorite stays toggled");
            WindowInputs.press(window, x, y, null);
            window.layoutViewport(400, 220, font);
            WindowInputs.release(window, 0, 0);
            check(events.size()==before+1, action.id()+" drag-out cancels");
            window.focusManager().restoreFocus(cell.button, null);
            WindowInputs.keyDown(window, GLFW.GLFW_KEY_SPACE, 0, null);
            window.layoutViewport(400, 220, font);
            WindowInputs.keyUp(window, GLFW.GLFW_KEY_SPACE, 0, null);
            check(events.size()==before+2, action.id()+" keyboard release after layout");
        }
        System.out.println("ORE_TABLE_SCROLLED_BUTTONS_PASS");
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
