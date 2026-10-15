package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** A named field; labels and input occupy the same Ore layout tree. */
public final class FormItem extends Container {
    private static final int LABEL_HEIGHT = 10;
    private static final int GAP = 3;
    private static final int ERROR_HEIGHT = 10;
    private static final int ROW_GAP = 6;

    private final String name;
    private final Component label;
    private final Input input;
    private final Label labelNode;
    private final Label errorNode;
    private final InputControl inputNode;
    private boolean active = true;
    private boolean horizontal;

    public FormItem(String name, Component label, Input input) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Field name is required");
        this.name = name;
        this.label = Objects.requireNonNull(label);
        this.input = Objects.requireNonNull(input);
        labelNode = new Label(label).color(0xFFD8D9DC).overflow(Label.Overflow.CLIP);
        errorNode = new Label().color(0xFFFF7777).overflow(Label.Overflow.CLIP);
        inputNode = new InputControl(input);
        addChild(labelNode);
        addChild(inputNode);
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
        input.setInputVisible(active && parent() instanceof Form form && form.visible());
    }
    public String value() { return input.getText(); }
    public Component error() { return input instanceof NumberInput number ? number.error() : null; }

    public void horizontal(boolean horizontal) {
        if (this.horizontal == horizontal) return;
        this.horizontal = horizontal;
        requestLayout();
    }

    public int rowHeight() {
        return (horizontal ? 0 : LABEL_HEIGHT + GAP) + input.controlSize().height() + 1
                + (error() == null ? 0 : ERROR_HEIGHT) + ROW_GAP;
    }

    @Override public Size measureSize(Font font, Size available) {
        return new Size(Math.max(minimumWidth(), available.width()),
                Math.max(minimumHeight(), rowHeight()));
    }

    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        int width = bounds.width();
        int labelWidth = horizontal ? Math.min(90, Math.max(36, width / 3)) : width;
        int controlX = horizontal ? labelWidth + GAP : 0;
        int controlY = horizontal ? 0 : LABEL_HEIGHT + GAP;
        int controlWidth = Math.max(1, width - controlX);
        fitChild(labelNode, new Rect(0, horizontal ? 5 : 0, labelWidth, LABEL_HEIGHT), font);
        fitChild(inputNode, new Rect(controlX, controlY, controlWidth,
                input.controlSize().height()), font);
        Component error = error();
        errorNode.text(error == null ? Component.empty() : error);
        errorNode.visible(error != null);
        fitChild(errorNode, new Rect(0, controlY + input.controlSize().height() + 1,
                width, error == null ? 0 : ERROR_HEIGHT), font);
    }
}
