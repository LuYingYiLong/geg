package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.input.Radio;
import io.github.luyingyilong.ore.control.input.RadioGroup;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.signal.Signal;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.Map;

/**
 * XML composition of String-valued RadioGroup and an ordinary Box layout.
 */
public final class UiRadioGroup extends Container {
    private final Box content = new Box();
    private final RadioGroup<String> group = new RadioGroup<>(this, String.class);

    public UiRadioGroup() {
        updateChildren(List.of(content));
    }

    private static String empty(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    Box contentBox() {
        return content;
    }

    @Override
    protected void validateChildren(List<Control> children) {
        if (!isUpdatingChildren()) throw new IllegalStateException("RadioGroup owns its layout; use item entries");
    }

    @Override
    public String getThemeType() {
        return "Box";
    }

    @UiProperty
    public Box.Orientation getOrientation() {
        return content.getOrientation();
    }

    public UiRadioGroup setOrientation(Box.Orientation value) {
        content.setOrientation(value);
        return this;
    }

    @UiProperty
    public int getGap() {
        return content.getGap();
    }

    public UiRadioGroup setGap(int value) {
        content.setGap(value);
        return this;
    }

    @UiProperty
    public boolean getWrap() {
        return content.getWrap();
    }

    public UiRadioGroup setWrap(boolean value) {
        content.setWrap(value);
        return this;
    }

    @Override
    public Size measureSize(Font font, Size available) {
        return content.desiredSize(font, available);
    }

    @Override
    public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(content, new Rect(0, 0, bounds.width(), bounds.height()), font);
    }

    @Override
    public int getMinimumWidth() {
        return Math.max(super.getMinimumWidth(), content.getMinimumWidth());
    }

    @Override
    public int getMinimumHeight() {
        return Math.max(super.getMinimumHeight(), content.getMinimumHeight());
    }

    public RadioGroup<String> getGroup() {
        return group;
    }

    @UiProperty(initialState = true, silentSetter = "setSelectedNoSignal")
    public String getSelected() {
        return group.getSelected();
    }

    public UiRadioGroup setSelected(String value) {
        group.setSelected(empty(value));
        return this;
    }

    public UiRadioGroup setSelectedNoSignal(String value) {
        group.setSelectedNoSignal(empty(value));
        return this;
    }

    @UiSignal
    public Signal<String> selectionChanged() {
        return group.selectionChanged();
    }

    @Override
    protected Object captureTreeState() {
        return new State(group.getRadios(), group.getSelected());
    }

    @Override
    protected void restoreTreeState(Object state) {
        if (state instanceof State saved) bind(saved.radios(), saved.selected());
    }

    void bind(Map<String, Radio> radios, String selected) {
        try (var silence = group.getSignalScope().silence()) {
            for (Radio radio : group.getRadios().values()) group.removeRadio(radio);
            radios.forEach(group::addRadio);
            group.setSelectedNoSignal(selected != null && radios.containsKey(selected) ? selected : null);
        }
    }

    private record State(Map<String, Radio> radios, String selected) {
    }
}
