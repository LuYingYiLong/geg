package io.github.luyingyilong.ore.compat.recipeviewer.canvas;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.data.Table;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.input.Dropdown;
import io.github.luyingyilong.ore.control.input.Input;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.input.NumberInput;
import io.github.luyingyilong.ore.control.input.TextArea;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.overlay.Drawer;
import io.github.luyingyilong.ore.control.overlay.Modal;

import io.github.luyingyilong.ore.event.EventGeometry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Development-only recipe content, also shared with the generic external-Screen fixture. */
public final class CanvasDemo {
    public static final List<CanvasDemo> INSTANCES = new ArrayList<>();
    public final Input input = new Input().setPlaceholder(Component.literal("中文 / Input"));
    public final NumberInput number = new NumberInput();
    public final TextArea area = new TextArea(0).setRows(2);
    public final InputControl inputNode = new InputControl(input), numberNode = new InputControl(number), areaNode = new InputControl(area);
    public final Dropdown<String> dropdown = new Dropdown<>("a", 4);
    public io.github.luyingyilong.ore.render.DrawContext lastFrame;
    public final Control content = new Control() {
        @Override protected void render(io.github.luyingyilong.ore.render.DrawContext context, int x, int y) {
            lastFrame = context;
        }
    };
    public final Button button = new Button(Component.literal("Action"));
    public final Button overlayButton = new Button(Component.literal("Overlay"));
    public Modal lastModal;
    public Drawer lastDrawer;
    public InputControl drawerInput;
    private int overlays;
    public int presses, textChanges;

    public CanvasDemo(int index) {
        area.setPlaceholder(Component.literal("Multiline 自动换行"));
        content.setDebugName("Canvas recipe " + index);
        add(new Label(Component.literal("Ore Canvas " + index)), 0, 0, 170, 14);
        add(inputNode, 0, 16, 170, 22);
        add(numberNode, 0, 40, 170, 22);
        add(areaNode, 0, 64, 170, 42);
        dropdown.setOptions(List.of(new Dropdown.Option<>("a", Component.literal("Option A")), new Dropdown.Option<>("b", Component.literal("Option B"))));
        add(dropdown, 0, 108, 170, 22);
        add(button, 0, 132, 80, 22);
        button.pressed().connect(content, () -> presses++);
        input.textChanged().connect(content, value -> textChanges++);
        add(overlayButton, 82, 132, 88, 22);
        overlayButton.pressed().connect(content, () -> {
            var root = content.getWindowContext().getOwner();
            if (++overlays > 1) {
                drawerInput = new InputControl(new Input().setPlaceholder(Component.literal("Drawer Input")));
                lastDrawer = new Drawer(Component.literal("Canvas Drawer")).setAnimationDuration(0).setContent(drawerInput);
                lastDrawer.closed().connect(lastDrawer, lastDrawer::queueFree);
                root.getPopupOverlay().addChild(lastDrawer); lastDrawer.open();
                return;
            }
            var table = new Table<Integer>().setRowKey(value -> value);
            table.addColumn(new Table.Column<>("value", Component.literal("Value"), 100, 100, 1,
                    width -> 23, new Table.TextCell<Integer>(row -> Component.literal("Row " + row))));
            table.setRows(List.of(1, 2, 3, 4, 5, 6));
            lastModal = new Modal(Component.literal("Canvas Modal")).setSize(220, 130).setContent(new Scroll().setContent(table));
            lastModal.closeRequested().connect(lastModal, () -> lastModal.queueFree());
            root.getPopupOverlay().addChild(lastModal);
        });
        INSTANCES.add(this);
        content.getSignalScope().own(() -> INSTANCES.remove(this));
    }

    private void add(Control node, int x, int y, int width, int height) {
        node.setPosition(x, y).setRequestedSize(width, height); content.addChild(node);
    }
}
