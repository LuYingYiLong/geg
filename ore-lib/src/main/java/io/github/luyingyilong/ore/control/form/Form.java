package io.github.luyingyilong.ore.control.form;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Named values, submit validation, and field layout share one Ore node.
 */
public final class Form extends Container {
    private final Map<String, FormItem<?>> items = new LinkedHashMap<>();
    private Layout layout = Layout.VERTICAL;

    public Form() {
        setVisible(false);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Form");
    }

    public static Layout effectiveLayout(Layout chosen, int viewportWidth) {
        return chosen == Layout.HORIZONTAL && viewportWidth <= 128 ? Layout.VERTICAL : chosen;
    }

    @io.github.luyingyilong.ore.gui.UiProperty
    public Layout getLayout() {
        return layout;
    }

    public Form setLayout(Layout layout) {
        this.layout = Objects.requireNonNull(layout);
        requestLayout();
        return this;
    }

    public Form addItem(FormItem<?> item) {
        addChild(item);
        return this;
    }

    @Override
    protected void validateChildren(java.util.List<Control> proposed) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Control child : proposed) {
            if (!(child instanceof FormItem<?> item))
                throw new IllegalArgumentException("Form accepts FormItem; use addItem");
            if (!names.add(item.getName())) throw new IllegalArgumentException("Duplicate field: " + item.getName());
        }
    }

    @Override
    protected void childrenChanged() {
        items.clear();
        for (Control child : getChildren())
            if (child instanceof FormItem<?> item && item.isAlive()) {
                items.put(item.getName(), item);
                item.setInputVisible(getVisible() && item.getActive());
            }
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        for (FormItem<?> item : java.util.List.copyOf(items.values()))
            item.setInputVisible(visible && item.getActive());
    }

    public <T> FormItem<T> getItem(FieldKey<T> key) {
        FormItem<?> item = items.get(key.name());
        if (item == null || !item.getKey().equals(key))
            throw new IllegalArgumentException("Unknown field or mismatched type: " + key.name());
        @SuppressWarnings("unchecked") FormItem<T> typed = (FormItem<T>) item;
        return typed;
    }

    public <T> T getValue(FieldKey<T> key) {
        return getItem(key).getValue();
    }

    public boolean getDirty() {
        return items.values().stream().anyMatch(FormItem::getDirty);
    }

    public boolean getTouched() {
        return items.values().stream().anyMatch(FormItem::getTouched);
    }

    public void captureInitialValues() {
        for (FormItem<?> item : java.util.List.copyOf(items.values())) item.captureInitialValue();
    }

    public void reset() {
        for (FormItem<?> item : java.util.List.copyOf(items.values())) item.reset();
    }

    public void setInitialValues(FormValues values) {
        setValuesNoSignal(values);
        captureInitialValues();
    }

    public void setValuesNoSignal(FormValues values) {
        for (FieldKey<?> key : values.getKeys()) getItem(key);
        for (FieldKey<?> key : values.getKeys()) fill(values, key);
    }

    private <T> void fill(FormValues values, FieldKey<T> key) {
        getItem(key).setValueNoSignal(values.getValue(key));
    }

    /**
     * MUI widgets are siblings of the Ore canvas, so hide fields outside a scrolled viewport.
     */
    public void clipInputs(Rect viewport) {
        for (FormItem<?> item : java.util.List.copyOf(items.values())) {
            if (item.getInputNode() == null) continue;
            Rect area = item.getInputNode().getAbsoluteBounds();
            boolean fullyVisible = area.x() >= viewport.x() && area.right() <= viewport.right()
                    && area.y() >= viewport.y() && area.bottom() <= viewport.bottom();
            item.setInputVisible(getVisible() && item.getActive() && fullyVisible);
        }
    }

    public FormValidation validate() {
        Map<String, Component> errors = new LinkedHashMap<>();
        FormValues.Builder values = FormValues.builder();
        if (!isAlive() || !getVisible()) {
            errors.put("_form", Component.translatable("ore.form.inactive"));
            return new FormValidation(null, errors);
        }
        FormItem<?> first = null;
        for (FormItem<?> item : java.util.List.copyOf(items.values())) {
            if (!eligible(item)) continue;
            if (!validateItem(item, values, errors) && first == null) first = item;
        }
        requestLayout();
        if (first != null) first.getField().focus();
        return new FormValidation(errors.isEmpty() ? values.build() : null, errors);
    }

    private boolean eligible(FormItem<?> item) {
        return item.getParent() == this && item.isAlive() && item.getActive() && item.getVisible();
    }

    private <T> boolean validateItem(FormItem<T> item, FormValues.Builder values, Map<String, Component> errors) {
        FieldValue<T> value = item.validate(true);
        if (!value.getValid()) {
            errors.put(item.getName(), value.error());
            return false;
        }
        values.setValue(item.getKey(), value.value());
        return true;
    }

    @Override
    public Size measureSize(Font font, Size available) {
        if (!isAlive()) return new Size(0, 0);
        boolean horizontal = effectiveLayout(layout, available.width()) == Layout.HORIZONTAL;
        int height = 0;
        for (FormItem<?> item : java.util.List.copyOf(items.values()))
            if (eligible(item)) {
                item.setHorizontal(horizontal);
                height += item.rowHeight(font, available.width());
            }
        return new Size(Math.max(getMinimumWidth(), available.width()),
                Math.max(getMinimumHeight(), height));
    }

    @Override
    public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            boolean horizontal = effectiveLayout(layout, bounds.width()) == Layout.HORIZONTAL;
            int top = 0;
            for (FormItem<?> item : java.util.List.copyOf(items.values())) {
                item.setHorizontal(horizontal);
                if (!eligible(item)) continue;
                int height = item.rowHeight(font, bounds.width());
                fitChild(item, new Rect(0, top, bounds.width(), height), font);
                top += height;
            }
        }
    }

    public enum Layout {VERTICAL, HORIZONTAL}
}
