package io.github.luyingyilong.geg.market.client;

import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphics;

final class MarketLayeredTextFieldWidget extends TextFieldWidget {
    private final float depth;
    private boolean marketVisible;

    MarketLayeredTextFieldWidget(float depth) {
        this.depth = depth;
    }

    void setMarketVisible(boolean visible) {
        marketVisible = visible;
        setEnabled(visible);
    }

    @Override
    public void preDraw(ModularGuiContext context, boolean drawText) {
        if (drawText) {
            if (marketVisible) {
                withDepth(context, () -> super.preDraw(context, true));
            }
        } else {
            super.preDraw(context, false);
        }
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (marketVisible) {
            withDepth(context, () -> super.drawBackground(context, theme));
        }
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (marketVisible) {
            withDepth(context, () -> super.draw(context, theme));
        }
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (marketVisible) {
            withDepth(context, () -> super.drawOverlay(context, theme));
        }
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        if (marketVisible) {
            withDepth(context, () -> super.drawForeground(context));
        }
    }

    private void withDepth(ModularGuiContext context, Runnable draw) {
        GuiGraphics graphics = context.getGraphics();
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, depth);
        try {
            draw.run();
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }
}
