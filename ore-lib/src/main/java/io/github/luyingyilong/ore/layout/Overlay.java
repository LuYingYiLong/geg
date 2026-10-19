package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.debug.DebugBridge;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

import java.util.List;

/** Children render after normal content and receive hit tests in reverse order. */
public final class Overlay extends Container {
    public Overlay add(Control layer) { addChild(layer); return this; }
    @Override public Overlay addChild(Control layer) {
        super.addChild(layer);
        return this;
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(localBounds, font);
            for (Control layer : getChildren()) if (layer.getParent() == this && layer.isAlive())
                layoutChild(layer, new Rect(0, 0, localBounds.width(), localBounds.height()), font);
        }
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            for (Control layer : getChildren()) if (layer.getParent() == this && layer.isAlive() && layer.getVisible())
                layer.draw(context, screenX, screenY);
        }
    }

    @Override public Control hitTop(int rootX, int rootY) {
        List<Control> layers = getChildren();
        for (int index = layers.size() - 1; index >= 0; index--) {
            Control layer = layers.get(index);
            if (layer.getParent() != this || !layer.isAlive()) continue;
            Control hit = layer.hitTop(rootX, rootY);
            if (hit != null) return hit;
        }
        return null;
    }
}
