package io.github.luyingyilong.ore.window;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.render.DrawContext;

/** Converts the host's MUI callback once. No Control drawing API depends on ModularGuiContext. */
public final class MuiDrawing {
    private MuiDrawing() { }

    public static DrawContext begin(ModularGuiContext mui, WindowContext window) {
        window.bind(mui);
        return new DrawContext(mui.getGraphics(), mui.getFont(), window,
                new Rect(0, 0, mui.getScreenArea().width, mui.getScreenArea().height),
                mui.getAbsMouseX(), mui.getAbsMouseY(), mui.getRenderPartialTicks());
    }
}
