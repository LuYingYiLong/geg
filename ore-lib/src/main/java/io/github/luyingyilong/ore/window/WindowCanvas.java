package io.github.luyingyilong.ore.window;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.base.Control;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional bridge for generic Window.open; existing business canvases remain compatible.
 */
public final class WindowCanvas extends Widget<WindowCanvas> implements Interactable {
    private final Window root;
    private final java.util.Set<InputControl> registered = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public WindowCanvas(Window root) {
        this.root = root;
    }

    public static List<InputControl> inputs(Control root) {
        List<InputControl> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(Control c, List<InputControl> out) {
        if (!c.isAlive()) return;
        if (c instanceof InputControl input) out.add(input);
        c.getChildren().forEach(child -> collect(child, out));
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (!root.isAlive()) return;
        List<InputControl> fields = inputs(root);
        for (InputControl old : List.copyOf(registered)) {
            if (!fields.contains(old)) {
                old.getWidget().dispose();
                registered.remove(old);
            }
        }
        for (InputControl field : fields)
            if (!registered.contains(field)) {
                if (field.getWidget().isValid()) throw new IllegalStateException("Input already belongs to a MUI host");
                String pending = field.getWidget().getText();
                if (field.getWidget().getStringValue() == null)
                    field.getWidget().value(new brachy.modularui.value.StringValue(pending));
                registered.add(field);
                field.getWidget().initialise(getPanel(), false);
            }
        root.prepareTree(context);
        root.layoutViewport(context.getScreenArea().width, context.getScreenArea().height, context.getFont());
        for (InputControl field : fields) {
            var area = field.getWidget().getArea();
            var bounds = field.getWidgetBounds();
            area.x = bounds.x();
            area.y = bounds.y();
            area.width = bounds.width();
            area.height = bounds.height();
            field.getWidget().onUpdate();
        }
        try (var frame = io.github.luyingyilong.ore.window.MuiDrawing.begin(context, root.getContext())) { root.draw(frame, 0, 0); }
    }

    @Override
    public Interactable.Result onMousePressed(int button) {
        return io.github.luyingyilong.ore.event.WindowInputs.press(root, getContext().getAbsMouseX(), getContext().getAbsMouseY(), getContext(), button)
                ? Interactable.Result.SUCCESS : Interactable.Result.IGNORE;
    }

    @Override
    public boolean onMouseReleased(int button) {
        return io.github.luyingyilong.ore.event.WindowInputs.pointer(root, io.github.luyingyilong.ore.event.PointerEvent.Action.RELEASE, getContext().getAbsMouseX(), getContext().getAbsMouseY(), button);
    }

    @Override
    public void onMouseDrag(int button, double x, double y) {
        io.github.luyingyilong.ore.event.WindowInputs.pointer(root, io.github.luyingyilong.ore.event.PointerEvent.Action.MOVE, getContext().getAbsMouseX(), getContext().getAbsMouseY(), button);
    }

    @Override
    public Interactable.Result onKeyPressed(int key, int scan, int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.key(root, io.github.luyingyilong.ore.event.KeyEvent.Action.PRESS, key, scan, modifiers, getContext()) ? Interactable.Result.SUCCESS : Interactable.Result.IGNORE;
    }

    @Override
    public boolean onKeyReleased(int key, int scan, int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.key(root, io.github.luyingyilong.ore.event.KeyEvent.Action.RELEASE, key, scan, modifiers, getContext());
    }

    @Override
    public Interactable.Result onCharTyped(char character, int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.text(root, String.valueOf(character), modifiers, getContext()) ? Interactable.Result.SUCCESS : Interactable.Result.IGNORE;
    }

    @Override
    public boolean onMouseScrolled(double delta) {
        return io.github.luyingyilong.ore.event.WindowInputs.scroll(root, getContext().getAbsMouseX(), getContext().getAbsMouseY(), delta, net.minecraft.client.gui.screens.Screen.hasShiftDown());
    }

    @Override
    public void dispose() {
        root.hostDetached();
        registered.forEach(input -> input.getWidget().dispose());
        registered.clear();
        super.dispose();
    }
}
