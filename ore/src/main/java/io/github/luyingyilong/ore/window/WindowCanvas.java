package io.github.luyingyilong.ore.window;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Window;

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
        if (c instanceof InputControl input) out.add(input);
        c.children().forEach(child -> collect(child, out));
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        List<InputControl> fields = inputs(root);
        for (InputControl old : List.copyOf(registered)) {
            if (!fields.contains(old)) { old.widget().dispose(); registered.remove(old); }
        }
        for (InputControl field : fields) if (!registered.contains(field)) {
            if (field.widget().isValid()) throw new IllegalStateException("Input already belongs to a MUI host");
            String pending = field.widget().getText();
            if (field.widget().getStringValue() == null) field.widget().value(new brachy.modularui.value.StringValue(pending));
            registered.add(field); field.widget().initialise(getPanel(), false);
        }
        root.layoutViewport(context.getScreenArea().width, context.getScreenArea().height, context.getFont());
        for (InputControl field : fields) {
            var area = field.widget().getArea(); var bounds = field.displayedBounds();
            area.x = bounds.x(); area.y = bounds.y(); area.width = bounds.width(); area.height = bounds.height();
            field.widget().onUpdate();
        }
        root.draw(context, 0, 0);
    }

    @Override
    public Interactable.Result onMousePressed(int button) {
        if (button != 0) return Interactable.Result.IGNORE;
        boolean handled = root.pointerPressed(getContext().getAbsMouseX(), getContext().getAbsMouseY(), getContext());
        if (!root.context().popups().capturing() && root.focusManager().focused() instanceof InputControl input
                && input.displayedBounds().contains(getContext().getAbsMouseX(), getContext().getAbsMouseY()))
            return input.widget().onMousePressed(button);
        return handled ? Interactable.Result.SUCCESS : Interactable.Result.IGNORE;
    }

    @Override
    public boolean onMouseReleased(int button) {
        return button == 0 && root.pointerReleased(getContext().getAbsMouseX(), getContext().getAbsMouseY());
    }

    @Override
    public void onMouseDrag(int button, double x, double y) {
        if (button == 0 && !root.pointerDragged(getContext().getAbsMouseX(), getContext().getAbsMouseY())
                && root.focusManager().focused() instanceof InputControl input) input.widget().onMouseDrag(button, x, y);
    }

    @Override
    public Interactable.Result onKeyPressed(int key, int scan, int modifiers) {
        return root.keyPressed(key, modifiers, getContext()) ? Interactable.Result.SUCCESS : Interactable.Result.IGNORE;
    }

    @Override
    public boolean onKeyReleased(int key, int scan, int modifiers) {
        return root.keyReleased(key, modifiers, getContext());
    }

    @Override
    public boolean onMouseScrolled(double delta) {
        return root.dispatchScroll(getContext().getAbsMouseX(), getContext().getAbsMouseY(), delta, false);
    }

    @Override public void dispose() {
        root.hostClosed(); registered.forEach(input -> input.widget().dispose()); registered.clear(); super.dispose();
    }
}
