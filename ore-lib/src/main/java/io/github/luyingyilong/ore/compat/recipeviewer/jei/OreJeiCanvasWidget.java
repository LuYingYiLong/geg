package io.github.luyingyilong.ore.compat.recipeviewer.jei;

import io.github.luyingyilong.ore.compat.recipeviewer.RecipeCanvasView;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;

import java.util.function.Supplier;

/** Register from IRecipeCategory.createRecipeExtras, alongside JEI's own ingredient slots. */
public final class OreJeiCanvasWidget implements IRecipeWidget, AutoCloseable {
    private final Rect bounds;
    private final RecipeCanvasView view;
    public OreJeiCanvasWidget(Rect bounds, Supplier<? extends Control> factory) {
        this.bounds = bounds;
        view = new RecipeCanvasView(RecipeCanvasView.Viewer.JEI, factory);
    }
    public static OreJeiCanvasWidget add(IRecipeExtrasBuilder builder, Rect bounds, Supplier<? extends Control> factory) {
        var widget = new OreJeiCanvasWidget(bounds, factory);
        builder.addWidget(widget);
        return widget;
    }
    @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
    @Override public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(bounds.x(), bounds.y(), 0);
            view.render(graphics, bounds.width(), bounds.height(), net.minecraft.client.Minecraft.getInstance().getFrameTime());
        } finally { graphics.pose().popPose(); }
    }
    @Override public void close() { view.close(); }
}
