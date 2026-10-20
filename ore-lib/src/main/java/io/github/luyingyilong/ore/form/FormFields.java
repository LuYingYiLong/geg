package io.github.luyingyilong.ore.form;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.input.*;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.signal.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;

public final class FormFields {
    private FormFields() { }
    public static FormField<String> text(Input input) {
        if (input instanceof NumberInput) throw new IllegalArgumentException("Use number() for NumberInput");
        InputControl control = inputControl(input);
        return new FormField<>(String.class, control, () -> FieldValue.valid(input.getText()), input::setTextNoSignal,
                () -> true, input::getText, (owner, run) -> input.textChanged().connect(owner, value -> run.run()),
                (owner, run) -> input.focusExited().connect(owner, run));
    }
    public static FormField<BigDecimal> number(NumberInput input) {
        InputControl control = inputControl(input);
        return new FormField<>(BigDecimal.class, control, () -> {
            try { return FieldValue.valid(input.getPreviewValue()); }
            catch (IllegalArgumentException | ArithmeticException invalid) {
                return FieldValue.invalid(input.getError() != null ? input.getError()
                        : net.minecraft.network.chat.Component.translatable("ore.number.invalid", input.getDecimalPlaces()));
            }
        }, value -> input.setTextNoSignal(value == null ? "" : value.toPlainString()), input::commit, input::getText,
                (owner, run) -> {
                    input.textChanged().connect(owner, value -> run.run());
                    input.valueCommitted().connect(owner, value -> run.run());
                }, (owner, run) -> input.focusExited().connect(owner, run));
    }
    public static FormField<Boolean> checkbox(Checkbox control) {
        return custom(Boolean.class, control, control::getChecked, control::setCheckedNoSignal, control.toggled());
    }
    public static <T> FormField<T> radioGroup(Class<T> type,Control content,RadioGroup<T> group) {
        if(type!=group.getValueType())throw new IllegalArgumentException("RadioGroup field type mismatch");
        return custom(type,content,group::getSelected,group::setSelectedNoSignal,group.selectionChanged());
    }
    public static FormField<Boolean> switchField(Switch control) {
        return custom(Boolean.class, control, control::getChecked, control::setCheckedNoSignal, control.toggled());
    }
    public static <T> FormField<T> dropdown(Class<T> type, Dropdown<T> control) {
        return custom(type, control, control::getSelected, control::setSelectedNoSignal, control.selectionChanged());
    }
    public static FormField<Integer> segmented(Segmented control) {
        return custom(Integer.class, control, control::getSelected, control::setSelectedNoSignal, control.selectionChanged());
    }
    public static <T> FormField<T> segmented(Class<T> type, Segmented control, List<T> values) {
        List<T> copy = List.copyOf(values);
        return custom(type, control, () -> copy.get(control.getSelected()), value -> {
            int index = copy.indexOf(value);
            if (index < 0) throw new IllegalArgumentException("Unknown segmented value");
            control.setSelectedNoSignal(index);
        }, control.selectionChanged());
    }
    public static <T> FormField<T> readOnly(Class<T> type, Control control, Supplier<T> value) {
        return new FormField<>(type, control, () -> FieldValue.valid(value.get()), null, () -> true, value,
                (owner, run) -> { }, (owner, run) -> { });
    }
    public static <T> FormField<T> custom(Class<T> type, Control control, Supplier<T> read, Consumer<T> write,
                                        Signal<?>... changes) {
        return new FormField<>(type, control, () -> FieldValue.valid(read.get()), write, () -> true, read,
                (owner, run) -> { for (Signal<?> signal : changes) signal.connect(owner, ignored -> run.run()); },
                (owner, run) -> connectBlur(control, control, owner, run));
    }
    private static InputControl inputControl(Input input) {
        Objects.requireNonNull(input);
        if (input.getThemeControl() instanceof InputControl)
            throw new IllegalArgumentException("Input already has a layout owner; reuse its FormField instead of wrapping it twice");
        return new InputControl(input);
    }
    private static void connectBlur(Control root, Control node, SignalOwner owner, Runnable run) {
        node.focusExited().connect(owner, () -> {
            // FocusManager emits exit before switching targets. Defer composite blur until its next tick.
            if (root.getChildCount() == 0) run.run();
            else pendingBlur(root, run);
        });
        for (Control child : node.getChildren()) connectBlur(root, child, owner, run);
    }
    private static void pendingBlur(Control root, Runnable run) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null) { run.run(); return; }
        client.tell(() -> {
            if (!root.isAlive()) return;
            var context = root.getWindowContext();
            Control focused = context == null || context.getOwner() == null ? null : context.getOwner().getFocusManager().getFocused();
            for (Control node = focused; node != null; node = node.getParent()) if (node == root) return;
            run.run();
        });
    }
}

