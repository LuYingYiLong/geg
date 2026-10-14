package io.github.luyingyilong.geg.client.ui.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import net.minecraft.client.gui.Font;

/** GUI-viewport root, not a native operating-system window. */
public final class Window extends Control {
    private final boolean fullscreen;
    private Control content;
    private Overlay overlay;

    public Window(boolean fullscreen) { this.fullscreen = fullscreen; }
    public Window content(Control content) { attach(content); this.content = content; return this; }
    public Window overlay(Overlay overlay) { attach(overlay); this.overlay = overlay; return this; }
    public boolean fullscreen() { return fullscreen; }

    public void layoutViewport(int width, int height, Font font) {
        int actualWidth = fullscreen ? width : Math.min(width, measureWidth(font, width));
        int actualHeight = fullscreen ? height : Math.min(height, measureHeight(font, height));
        layout(new Rect(0, 0, actualWidth, actualHeight), font);
    }

    @Override
    public int measureWidth(Font font, int available) {
        return content == null ? 0 : content.measureWidth(font, available);
    }

    @Override
    public int measureHeight(Font font, int available) {
        return content == null ? 0 : content.measureHeight(font, available);
    }

    @Override
    public void layout(Rect localBounds, Font font) {
        super.layout(localBounds, font);
        Rect full = new Rect(0, 0, localBounds.width(), localBounds.height());
        if (content != null) content.layout(full, font);
        if (overlay != null) overlay.layout(full, font);
    }

    @Override
    public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (content != null) content.draw(context, screenX, screenY);
        if (overlay != null) overlay.draw(context, screenX, screenY);
    }
}
