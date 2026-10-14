package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.widget.sizer.Unit;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** One named, labelled input in a Form */
public final class FormItem {
    private final String name;
    private final Component label;
    private final Input input;
    private boolean active = true;
    private int controlX;
    private int controlY;
    private int controlWidth = 1;
    private int labelX;
    private int labelY;
    private int labelWidth;
    private int errorY;

    public FormItem(String name, Component label, Input input) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Field name is required");
        this.name = name;
        this.label = Objects.requireNonNull(label);
        this.input = Objects.requireNonNull(input);
        input.left(() -> controlX, Unit.Measure.PIXEL);
        input.top(() -> controlY, Unit.Measure.PIXEL);
        input.width(() -> controlWidth, Unit.Measure.PIXEL);
        input.height(input.controlSize().height());
    }

    public String name() { return name; }
    public Component label() { return label; }
    public Input input() { return input; }
    public boolean active() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String value() { return input.getText(); }
    public Component error() { return input instanceof NumberInput number ? number.error() : null; }

    void place(int labelX, int labelY, int labelWidth, int controlX, int controlY,
               int controlWidth, int errorY) {
        boolean moved = this.controlX != controlX || this.controlY != controlY
                || this.controlWidth != controlWidth;
        this.labelX = labelX;
        this.labelY = labelY;
        this.labelWidth = labelWidth;
        this.controlX = controlX;
        this.controlY = controlY;
        this.controlWidth = Math.max(1, controlWidth);
        this.errorY = errorY;
        if (moved) input.scheduleResize();
    }

    int labelX() { return labelX; }
    int labelY() { return labelY; }
    int labelWidth() { return labelWidth; }
    int errorY() { return errorY; }
}
