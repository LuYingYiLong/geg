package io.github.luyingyilong.ore.compat.recipeviewer.rei;

import io.github.luyingyilong.ore.compat.recipeviewer.RecipeCanvasView;
import io.github.luyingyilong.ore.layout.Control;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.List;
import java.util.function.Supplier;

/** Add to DisplayCategory.setupDisplay's widget list; retain REI's own entry widgets. */
public final class OreReiCanvasWidget extends Widget implements AutoCloseable {
    private final Rectangle bounds;
    private final RecipeCanvasView view;
    public OreReiCanvasWidget(Rectangle bounds, Supplier<? extends Control> factory) {
        this.bounds = bounds;
        view = new RecipeCanvasView(RecipeCanvasView.Viewer.REI, factory);
    }
    @Override public List<? extends GuiEventListener> children() { return List.of(); }
    @Override public boolean containsMouse(double mouseX, double mouseY) { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(bounds.x, bounds.y, 0);
            view.render(graphics, bounds.width, bounds.height, partial);
        } finally { graphics.pose().popPose(); }
    }
    @Override public void close() { view.close(); }
}
