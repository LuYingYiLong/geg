package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.signal.*;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public final class ControlSignalTest {
    public static void main(String[] args) {
        Control source = new Control(), receiver = new Control();
        SignalSource0 external = new SignalSource0(source, "external");
        int[] received = {0};
        Connection connection = external.signal().connect(receiver, () -> received[0]++);
        Box parent = new Box().add(receiver);
        receiver.visible(false); receiver.visible(true);
        parent.removeChild(receiver); parent.add(receiver);
        external.emit();
        check(received[0] == 1 && connection.isConnected(), "Hide and detach preserve lifetime");
        parent.queueFree(); parent.queueFree();
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        external.emit();
        check(receiver.isDisposed() && !connection.isConnected() && received[0] == 1, "Parent destroys receiver");
        check(parent.children().isEmpty() && receiver.parent() == null, "Destroyed tree releases ownership");

        Control row = new Control();
        Connection bound = external.signal().connect(row.bindingScope(), () -> received[0]++);
        Connection structural = external.signal().connect(row, () -> received[0]++);
        row.resetBindingScope();
        check(!bound.isConnected() && structural.isConnected(), "Rebinding retains only structural connections");
        row.queueFree(); source.queueFree();
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        check(!structural.isConnected(), "Recycled instance lifetime ends on permanent disposal");

        Button button = new Button(cue -> { });
        List<String> actions = new ArrayList<>();
        button.pressed().connect(button, () -> actions.add("pressed"));
        button.toggled().connect(button, value -> actions.add("state:" + value));
        button.toggleButton(true).buttonPressed(true).buttonPressed(true);
        check(actions.equals(List.of("state:true")), "Programmatic state is not activation");
        button.setButtonPressedNoSignal(false);
        button.activate();
        check(actions.equals(List.of("state:true", "state:true", "pressed")), "User activation emits each semantic signal once");
        button.queueFree();
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        check(button.pressed().connectionCount() == 0, "Widget owns its sources");

        Checkbox checkbox = new Checkbox(Component.empty());
        List<Boolean> changes = new ArrayList<>();
        checkbox.toggled().connect(checkbox, changes::add);
        checkbox.checked(true).checked(true).setCheckedNoSignal(false);
        check(changes.equals(List.of(true)) && !checkbox.checked(), "Checked equality and silent setter");

        Dropdown<Integer> dropdown = new Dropdown<>(1, 4);
        dropdown.setOptions(List.of(new Dropdown.Option<>(1, Component.literal("A")), new Dropdown.Option<>(2, Component.literal("B"))));
        List<Integer> selections = new ArrayList<>(), activations = new ArrayList<>();
        dropdown.selectionChanged().connect(dropdown, selections::add);
        dropdown.itemActivated().connect(dropdown, activations::add);
        dropdown.select(2); dropdown.select(2); dropdown.selected(1);
        check(selections.equals(List.of(2, 1)) && activations.equals(List.of(2, 2)), "Reselection and property changes are distinct");
        dropdown.setSelectedNoSignal(2);
        check(selections.size() == 2, "Silent dropdown setter");
        dropdown.queueFree(); checkbox.queueFree();
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        System.out.println("ORE_CONTROL_SIGNAL_PASS");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
