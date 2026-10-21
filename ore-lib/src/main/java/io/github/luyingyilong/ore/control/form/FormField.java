package io.github.luyingyilong.ore.control.form;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.signal.SignalOwner;
import java.util.Objects;
import java.util.function.*;

/** Editing adapter, independent of the Control inheritance hierarchy. */
public final class FormField<T> {
    private final Class<T> type;
    private final Control control;
    private final Supplier<FieldValue<T>> reader;
    private final Consumer<T> writer;
    private final BooleanSupplier prepare;
    private final Supplier<?> snapshot;
    private final BiConsumer<SignalOwner, Runnable> changes;
    private final BiConsumer<SignalOwner, Runnable> blurs;

    public FormField(Class<T> type, Control control, Supplier<FieldValue<T>> reader, Consumer<T> writer,
                     BooleanSupplier prepare, Supplier<?> snapshot,
                     BiConsumer<SignalOwner, Runnable> changes, BiConsumer<SignalOwner, Runnable> blurs) {
        this.type = Objects.requireNonNull(type);
        this.control = Objects.requireNonNull(control);
        this.reader = Objects.requireNonNull(reader);
        this.writer = writer;
        this.prepare = Objects.requireNonNull(prepare);
        this.snapshot = Objects.requireNonNull(snapshot);
        this.changes = Objects.requireNonNull(changes);
        this.blurs = Objects.requireNonNull(blurs);
    }
    public Class<T> getType() { return type; }
    public Control getControl() { return control; }
    public FieldValue<T> read() { return reader.get(); }
    public Object getEditSnapshot() { return snapshot.get(); }
    public boolean prepare() { return prepare.getAsBoolean(); }
    public void setValueNoSignal(T value) { if (writer != null) writer.accept(type.cast(value)); }
    public void connect(SignalOwner owner, Runnable changed, Runnable blurred) {
        changes.accept(owner, changed);
        blurs.accept(owner, blurred);
    }
    public void focus() {
        Control target = focusTarget(control);
        var context = control.getWindowContext();
        if (context != null && context.getOwner() != null && target != null)
            context.getOwner().getFocusManager().requestFocus(target, context.getMui());
    }
    private static Control focusTarget(Control node) {
        if (node.acceptsFocus()) return node;
        for (Control child : node.getChildren()) {
            Control found = focusTarget(child);
            if (found != null) return found;
        }
        return null;
    }
}

