package io.github.luyingyilong.ore.compat.recipeviewer.emi;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github.luyingyilong.ore.compat.recipeviewer.RecipeCanvasView;
import io.github.luyingyilong.ore.control.base.Control;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Supplier;

/** Register from EmiRecipe.addWidgets. Input stays with the ScreenWindowHost. */
public final class OreEmiCanvasWidget extends Widget implements AutoCloseable {
    private final Bounds bounds;
    private final RecipeCanvasView view;
    public OreEmiCanvasWidget(Bounds bounds, Supplier<? extends Control> factory) {
        this.bounds = bounds;
        view = new RecipeCanvasView(RecipeCanvasView.Viewer.EMI, factory);
    }
    public static OreEmiCanvasWidget add(WidgetHolder holder, Bounds bounds, Supplier<? extends Control> factory) {
        return holder.add(new OreEmiCanvasWidget(bounds, factory));
    }
    @Override public Bounds getBounds() { return bounds; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(bounds.x(), bounds.y(), 0);
            view.render(graphics, bounds.width(), bounds.height(), partial);
        } finally { graphics.pose().popPose(); }
    }
    @Override public void close() { view.close(); }
}
