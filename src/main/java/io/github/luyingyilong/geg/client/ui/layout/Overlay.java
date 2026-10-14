package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/** Children render after normal content and receive hit tests in reverse order. */
public final class Overlay extends Control {
    private final List<Control> layers = new ArrayList<>();

    public Overlay add(Control layer) { attach(layer); layers.add(layer); return this; }

    @Override
    public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        for (Control layer : layers) layer.layout(new Rect(0, 0,
                localBounds.width(), localBounds.height()), font);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        for (Control layer : layers) if (layer.visible()) layer.draw(context, screenX, screenY);
    }

    public Control hitTop(int rootX, int rootY) {
        for (int index = layers.size() - 1; index >= 0; index--) {
            Control layer = layers.get(index);
            if (layer.hit(rootX, rootY)) return layer;
        }
        return null;
    }
}
