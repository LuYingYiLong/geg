package io.github.luyingyilong.ore.window;

import brachy.modularui.api.IMuiScreen;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import io.github.luyingyilong.ore.OreClient;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.layout.Control;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** MUI lifecycle/focus only: never installed as Minecraft.screen and never receives a second input pass. */
public final class MuiInputHost implements AutoCloseable {
    private final Set<InputControl> fields = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ModularScreen screen;
    private int width = -1, height = -1;
    private boolean closed;

    public MuiInputHost(WindowContext context, Screen wrapped, BooleanSupplier active, Runnable close) {
        var panel = new ModularPanel<>("ore_host_inputs").full().disableThemeBackground(true);
        screen = new ModularScreen(OreClient.getOptions().hostModId(), panel) {
            private boolean fieldFocused;
            @Override public boolean isFocused() { return fieldFocused; }
            @Override public void setFocused(boolean value) { fieldFocused = value; }
            @Override public boolean isActive() { return active.getAsBoolean(); }
            @Override public boolean isClientOnly() { return true; }
            @Override public void close() { close.run(); }
            @Override public void close(boolean back) { close.run(); }
        };
        var wrappedReference = new java.lang.ref.WeakReference<>(wrapped);
        screen.construct(new IMuiScreen() {
            @Override public ModularScreen screen() { return screen; }
            @Override public Screen wrappedScreen() { return wrappedReference.get(); }
            @Override public void updateGuiArea(brachy.modularui.utils.Rectangle area) { }
        });
        screen.getContext().setSettings(new UISettings());
        // Native MUI adapter uses its own built-in editing theme.
        resize(context.getGuiWidth(), context.getGuiHeight());
        context.bind(screen.getContext());
        screen.onOpen();
    }

    public ModularScreen getScreen() { return screen; }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width; this.height = height;
        screen.onResize(width, height);
    }

    public void prepare(Control root) {
        List<InputControl> live = WindowCanvas.inputs(root);
        for (InputControl previous : List.copyOf(fields)) if (!live.contains(previous)) {
            previous.getWidget().dispose();
            fields.remove(previous);
        }
        for (InputControl field : live) if (!fields.contains(field)) {
            if (field.getWidget().isValid()) throw new IllegalStateException("Input already belongs to a MUI host");
            if (field.getWidget().getStringValue() == null)
                field.getWidget().value(new brachy.modularui.value.StringValue(field.getWidget().getText()));
            field.getWidget().initialise(screen.getMainPanel(), false);
            fields.add(field);
        }
    }

    public void update() { for (InputControl field : List.copyOf(fields)) if (field.isAlive()) field.getWidget().onUpdate(); }

    public DrawScope begin(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partial) {
        var mui = screen.getContext();
        mui.updateState(mouseX, mouseY, partial);
        mui.setGraphics(graphics);
        mui.setOverrideFont(font);
        mui.reset();
        mui.pushViewport(null, mui.getScreenArea());
        return () -> { mui.popViewport(null); mui.setGraphics(null); };
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (screen.getContext().isFocused()) screen.getContext().removeFocus();
        fields.forEach(field -> field.getWidget().dispose());
        fields.clear();
        screen.onClose();
    }

    public interface DrawScope extends AutoCloseable { @Override void close(); }
}
