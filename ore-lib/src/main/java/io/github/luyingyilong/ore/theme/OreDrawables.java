package io.github.luyingyilong.ore.theme;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.drawable.AdaptableUITexture;
import brachy.modularui.drawable.ColorType;
import brachy.modularui.drawable.UITexture;
import io.github.luyingyilong.ore.render.DrawContext;
import brachy.modularui.theme.WidgetTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.luyingyilong.ore.layout.Rect;

/**
 * Bridge batched Minecraft drawing and ModularUI's immediate texture rendering.
 */
public final class OreDrawables {
    private OreDrawables() {
    }

    public static void draw(DrawContext context, IDrawable drawable, Rect area, WidgetTheme style) {
        if (drawable == null || area.width() <= 0 || area.height() <= 0) return;
        // Submit backgrounds/track vertices before a translucent MUI texture samples them.
        context.getGraphics().flush();
        RenderSystem.defaultBlendFunc();
        var mui = context.getWindowContext().getMui();
        if (drawable instanceof UITexture texture && texture.nonOpaque()) {
            if (texture instanceof AdaptableUITexture adaptable && hasBorders(adaptable)) {
                texture.applyColor((texture.colorType() == null ? ColorType.DEFAULT : texture.colorType()).getColor(style));
                // MUI's native-size fast path drops nonOpaque; use the nine-slice path directly.
                if (adaptable.tiled()) adaptable.drawTiled(mui, area.x(), area.y(), area.width(), area.height());
                else adaptable.drawStretched(mui, area.x(), area.y(), area.width(), area.height());
            } else if (!(texture instanceof AdaptableUITexture adaptable) || !adaptable.tiled()) {
                texture.drawSubArea(mui, area.x(), area.y(), area.width(), area.height(), 0, 0, 1, 1, style);
            } else {
                drawable.draw(mui, area.x(), area.y(), area.width(), area.height(), style);
            }
        } else {
            drawable.draw(mui, area.x(), area.y(), area.width(), area.height(), style);
        }
    }

    private static boolean hasBorders(AdaptableUITexture texture) {
        return texture.bl() > 0 || texture.bt() > 0 || texture.br() > 0 || texture.bb() > 0;
    }
}
