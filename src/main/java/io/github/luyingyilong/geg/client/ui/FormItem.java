package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.input.Input;
import io.github.luyingyilong.geg.client.ui.input.InputControl;
import io.github.luyingyilong.geg.client.ui.input.NumberInput;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Supplier;

/** A named field; the label and control occupy the same Ore layout tree. */
public final class FormItem extends Container {
    private static final int LABEL_HEIGHT = 10;
    private static final int GAP = 3;
    private static final int ERROR_HEIGHT = 10;
    private static final int ROW_GAP = 6;

    private final String name;
    private final Component label;
    private final Input input;
    private final Control field;
    private final Supplier<String> value;
    private final Label labelNode;
    private final Label errorNode;
    private final InputControl inputNode;
    private boolean active = true;
    private boolean horizontal;

    public FormItem(String name, Component label, Input input) {
        this(name, label, new InputControl(Objects.requireNonNull(input)), input::getText, input);
    }

    /** Accepts an Ore control such as a Checkbox group in place of a text input. */
    public FormItem(String name, Component label, Control field, Supplier<String> value) {
        this(name, label, field, value, null);
    }

    private FormItem(String name, Component label, Control field, Supplier<String> value, Input input) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Field name is required");
        this.name = name;
        this.label = Objects.requireNonNull(label);
        this.input = input;
        this.field = Objects.requireNonNull(field);
        this.value = Objects.requireNonNull(value);
        labelNode = new Label(label).color(OreTheme.TEXT_WHITE).overflow(Label.Overflow.CLIP);
        errorNode = new Label().color(OreTheme.TEXT_ERROR).overflow(Label.Overflow.CLIP);
        inputNode = field instanceof InputControl node ? node : null;
        addChild(labelNode);
        addChild(field);
        addChild(errorNode);
    }

    public String name() { return name; }
    public Component label() { return label; }
    public Input input() { return input; }
    public InputControl inputNode() { return inputNode; }
    public boolean active() { return active; }
    public void setActive(boolean active) {
        this.active = active;
        visible(active);
        setInputVisible(active && parent() instanceof Form form && form.visible());
    }
    public void setInputVisible(boolean visible) {
        if (input != null) input.setInputVisible(visible);
    }
    public String value() { return value.get(); }
    public Component error() { return input instanceof NumberInput number ? number.error() : null; }

    public void horizontal(boolean horizontal) {
        if (this.horizontal == horizontal) return;
        this.horizontal = horizontal;
        requestLayout();
    }

    public int rowHeight(Font font, int width) {
        return (horizontal ? 0 : LABEL_HEIGHT + GAP) + fieldHeight(font, width) + 1
                + (error() == null ? 0 : ERROR_HEIGHT) + ROW_GAP;
    }

    private int fieldHeight(Font font, int width) {
        int labelWidth = horizontal ? Math.min(90, Math.max(36, width / 3)) : 0;
        int controlWidth = Math.max(1, width - (horizontal ? labelWidth + GAP : 0));
        return field.measureSize(font, new Size(controlWidth, Integer.MAX_VALUE)).height();
    }

    @Override public Size measureSize(Font font, Size available) {
        return new Size(Math.max(minimumWidth(), available.width()),
                Math.max(minimumHeight(), rowHeight(font, available.width())));
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        int width = bounds.width();
        int labelWidth = horizontal ? Math.min(90, Math.max(36, width / 3)) : width;
        int controlX = horizontal ? labelWidth + GAP : 0;
        int controlY = horizontal ? 0 : LABEL_HEIGHT + GAP;
        int controlWidth = Math.max(1, width - controlX);
        int controlHeight = fieldHeight(font, width);
        fitChild(labelNode, new Rect(0, horizontal ? 5 : 0, labelWidth, LABEL_HEIGHT), font);
        fitChild(field, new Rect(controlX, controlY, controlWidth, controlHeight), font);
        Component error = error();
        errorNode.text(error == null ? Component.empty() : error);
        errorNode.visible(error != null);
        fitChild(errorNode, new Rect(0, controlY + controlHeight + 1,
                width, error == null ? 0 : ERROR_HEIGHT), font);
    }
}
