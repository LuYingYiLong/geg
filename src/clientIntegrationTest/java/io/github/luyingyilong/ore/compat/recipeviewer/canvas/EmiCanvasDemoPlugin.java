package io.github.luyingyilong.ore.compat.recipeviewer.canvas;

import dev.emi.emi.api.*;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.*;
import io.github.luyingyilong.ore.compat.recipeviewer.emi.OreEmiCanvasWidget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

@EmiEntrypoint
public final class EmiCanvasDemoPlugin implements EmiPlugin {
    private static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(ResourceLocation.fromNamespaceAndPath("ore", "canvas_demo"), EmiStack.of(Items.CRAFTING_TABLE));
    @Override public void register(EmiRegistry registry) {
        if (!Boolean.getBoolean("ore.canvas.demo")) return;
        registry.addCategory(CATEGORY);
        for (int i = 1; i <= 4; i++) {
            int index = i;
            registry.addRecipe(new BasicEmiRecipe(CATEGORY, ResourceLocation.fromNamespaceAndPath("ore", "canvas_demo/" + i), 220, 158) {
                { inputs.add(EmiStack.of(Items.DIAMOND)); outputs.add(EmiStack.of(Items.EMERALD)); }
                @Override public void addWidgets(WidgetHolder holder) {
                    holder.addSlot(inputs.get(0), 192, 18); holder.addSlot(outputs.get(0), 192, 46).recipeContext(this);
                    OreEmiCanvasWidget.add(holder, new Bounds(0, 0, 170, 156), () -> new CanvasDemo(index).content);
                }
            });
        }
    }
    public static void open() { EmiApi.displayRecipeCategory(CATEGORY); }
}
