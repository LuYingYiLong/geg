package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.theme.ThemeKey;
import io.github.luyingyilong.ore.theme.ThemePadding;
import io.github.luyingyilong.ore.form.*;
import io.github.luyingyilong.ore.input.*;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import java.util.*;

/** A typed field, its validation lifetime, and its label/error geometry. */
public final class FormItem<T> extends Container {
    private static final ThemePadding PADDING = ThemePadding.of("FormItem", "", 0, 0, 6, 0);
    public static final ThemeKey<Integer> PADDING_TOP = PADDING.top();
    public static final ThemeKey<Integer> PADDING_RIGHT = PADDING.right();
    public static final ThemeKey<Integer> PADDING_BOTTOM = PADDING.bottom();
    public static final ThemeKey<Integer> PADDING_LEFT = PADDING.left();

    private static final int GAP = 3;
    private final FieldKey<T> key;
    private final Component label;
    private final FormField<T> field;
    private final Label labelNode;
    private final Label errorNode;
    private final List<Validator<? super T>> validators = new ArrayList<>();
    private boolean active = true, horizontal, touched, validating, filling;
    private Object initialEdit;
    private T initialValue;
    private Component error;

    public FormItem(FieldKey<T> key, Component label, FormField<T> field) {
        this.key = Objects.requireNonNull(key);
        this.label = Objects.requireNonNull(label);
        this.field = Objects.requireNonNull(field);
        if (!key.type().equals(field.getType())) throw new IllegalArgumentException("Field adapter type differs from key");
        labelNode = new Label(label).setWrap(Label.Wrap.WORD).setOverflow(Label.Overflow.CLIP);
        errorNode = new Label().setThemeTypeVariation("LabelError").setWrap(Label.Wrap.WORD);
        errorNode.setVisible(false);
        updateChildren(List.of(labelNode, field.getControl(), errorNode));
        captureInitialValue();
        field.connect(this, this::changed, this::blurred);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "FormItem");
        PADDING.register(db);
    }
    @Override protected void validateChildren(List<Control> proposed) {
        if (!isUpdatingChildren()) throw new IllegalStateException("FormItem has a fixed field skeleton");
    }
    public FieldKey<T> getKey() { return key; }
    public String getName() { return key.name(); }
    public Component getLabel() { return label; }
    public FormField<T> getField() { return field; }
    public InputControl getInputNode() { return field.getControl() instanceof InputControl node ? node : null; }
    public Input getInput() { return getInputNode() == null ? null : getInputNode().getWidget(); }
    public boolean getActive() { return active; }
    public void setActive(boolean value) {
        active = value;
        setVisible(value);
        setInputVisible(value && getParent() instanceof Form form && form.getVisible());
        requestLayout();
    }
    public void setInputVisible(boolean value) { if (getInput() != null) getInput().setInputVisible(value); }
    public boolean getTouched() { return touched; }
    public boolean getDirty() { return !Objects.equals(initialEdit, field.getEditSnapshot()); }
    public FormItem<T> addValidator(Validator<? super T> rule) { validators.add(Objects.requireNonNull(rule)); return this; }
    public Component getError() {
        return error != null ? error : getInput() instanceof NumberInput number ? number.getError() : null;
    }
    public T getValue() {
        FieldValue<T> read = field.read();
        if (!read.getValid()) throw new IllegalStateException("Invalid field: " + getName());
        return key.type().cast(read.value());
    }
    private void changed() { if (!filling && touched && !validating && isAlive()) validate(false); }
    private void blurred() { if (!filling && isAlive() && active) { touched = true; validate(false); } }
    public FieldValue<T> validate(boolean prepare) {
        if (validating) return field.read();
        validating = true;
        try {
            touched = true;
            boolean prepared = !prepare || field.prepare();
            FieldValue<T> value = field.read();
            Component next = value.error();
            if (!prepared && next == null) next = Component.translatable("ore.form.invalid");
            if (next == null) {
                T typed = key.type().cast(value.value());
                for (Validator<? super T> rule : List.copyOf(validators)) {
                    Optional<Component> failed = rule.validate(typed);
                    if (failed.isPresent()) { next = failed.get(); break; }
                }
            }
            setError(next);
            return next == null ? value : FieldValue.invalid(next);
        } finally { validating = false; }
    }
    private void setError(Component value) {
        if (Objects.equals(error, value)) return;
        error = value;
        errorNode.setText(value == null ? Component.empty() : value);
        errorNode.setVisible(value != null);
        requestLayout();
    }
    public void captureInitialValue() {
        FieldValue<T> value = field.read();
        initialValue = value.getValid() ? value.value() : null;
        initialEdit = field.getEditSnapshot();
        touched = false;
        setError(null);
    }
    public void setValueNoSignal(T value) {
        filling = true;
        try { field.setValueNoSignal(value); }
        finally { filling = false; }
        if (touched) validate(false);
        requestLayout();
    }
    public void reset() {
        filling = true;
        try {
            if (getInput() != null && initialEdit instanceof String text) getInput().setTextNoSignal(text);
            else field.setValueNoSignal(initialValue);
            if (getInput() instanceof NumberInput number) number.clearError();
        } finally { filling = false; }
        touched = false;
        setError(null);
        requestLayout();
    }
    public void setHorizontal(boolean value) { if (horizontal != value) { horizontal = value; requestLayout(); } }
    private int labelWidth(int width) { return horizontal ? Math.min(90, Math.max(36, width / 3)) : width; }
    private int textHeight(Label node, Font font, int width) {
        return font == null ? 10 : node.measureHeight(font, Math.max(1, width));
    }
    private Geometry geometry(Font font, int width) {
        int labelWidth = labelWidth(width);
        int controlX = horizontal ? labelWidth + GAP : 0;
        int controlWidth = Math.max(1, width - controlX);
        int labelHeight = textHeight(labelNode, font, labelWidth);
        int controlY = horizontal ? 0 : labelHeight + GAP;
        int controlHeight = field.getControl().desiredSize(font, new Size(controlWidth, Integer.MAX_VALUE)).height();
        int row = horizontal ? Math.max(labelHeight, controlHeight) : controlY + controlHeight;
        Component shown = getError();
        errorNode.setText(shown == null ? Component.empty() : shown);
        errorNode.setVisible(shown != null);
        int errorHeight = shown == null ? 0 : textHeight(errorNode, font, width);
        return new Geometry(labelWidth, labelHeight, controlX, controlY, controlWidth, controlHeight, row, errorHeight);
    }
    public int rowHeight(Font font, int width) {
        var padding = PADDING.getInsets(this);
        Geometry g = geometry(font, Math.max(0, width - padding.getHorizontal()));
        return Math.max(getMinimumHeight(), padding.getVertical() + g.row + (g.errorHeight == 0 ? 0 : 1 + g.errorHeight));
    }
    @Override public Size measureSize(Font font, Size available) {
        return new Size(Math.max(getMinimumWidth(), available.width()), rowHeight(font, available.width()));
    }
    @Override public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var phase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            var padding = PADDING.getInsets(this);
            int width = Math.max(0, bounds.width() - padding.getHorizontal());
            Geometry g = geometry(font, width);
            fitChild(labelNode, new Rect(padding.left(), padding.top() + (horizontal ? Math.max(0, (g.controlHeight - g.labelHeight) / 2) : 0),
                    g.labelWidth, g.labelHeight), font);
            fitChild(field.getControl(), new Rect(padding.left() + g.controlX, padding.top() + g.controlY, g.controlWidth, g.controlHeight), font);
            fitChild(errorNode, new Rect(padding.left(), padding.top() + g.row + 1, width, g.errorHeight), font);
        }
    }
    private record Geometry(int labelWidth, int labelHeight, int controlX, int controlY,
                            int controlWidth, int controlHeight, int row, int errorHeight) { }
}
