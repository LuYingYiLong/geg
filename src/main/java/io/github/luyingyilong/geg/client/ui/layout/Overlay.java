package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/** Children render after normal content and receive hit tests in reverse order. */
public final class Overlay extends Container {
    private final List<Control> layers = new ArrayList<>();

    public Overlay add(Control layer) { addChild(layer); return this; }
    @Override public Overlay addChild(Control layer) {
        super.addChild(layer);
        layers.add(layer);
        return this;
    }
    @Override public boolean removeChild(Control layer) {
        if (!super.removeChild(layer)) return false;
        layers.remove(layer);
        return true;
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        for (Control layer : layers) fitChild(layer, new Rect(0, 0,
                localBounds.width(), localBounds.height()), font);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        for (Control layer : layers) if (layer.visible()) layer.draw(context, screenX, screenY);
    }

    @Override public Control hitTop(int rootX, int rootY) {
        for (int index = layers.size() - 1; index >= 0; index--) {
            Control layer = layers.get(index);
            Control hit = layer.hitTop(rootX, rootY);
            if (hit != null) return hit;
        }
        return null;
    }
}
