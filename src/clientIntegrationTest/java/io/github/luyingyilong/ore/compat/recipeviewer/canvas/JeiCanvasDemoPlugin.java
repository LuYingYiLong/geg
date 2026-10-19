package io.github.luyingyilong.ore.compat.recipeviewer.canvas;

import io.github.luyingyilong.ore.compat.recipeviewer.RecipeCanvasView;
import io.github.luyingyilong.ore.compat.recipeviewer.jei.OreJeiCanvasWidget;
import io.github.luyingyilong.ore.layout.Rect;
import mezz.jei.api.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

@JeiPlugin
public final class JeiCanvasDemoPlugin implements IModPlugin {
    private static final RecipeType<Integer> TYPE = new RecipeType<>(ResourceLocation.fromNamespaceAndPath("ore", "canvas_demo"), Integer.class);
    private static IJeiRuntime runtime;
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("ore", "canvas_demo_plugin"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!enabled()) return;
        var helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new IRecipeCategory<Integer>() {
            @Override public RecipeType<Integer> getRecipeType() { return TYPE; }
            @Override public Component getTitle() { return Component.literal("Ore Canvas Demo"); }
            @Override public int getWidth() { return 220; }
            @Override public int getHeight() { return 158; }
            @Override public mezz.jei.api.gui.drawable.IDrawable getIcon() { return helper.createDrawableItemStack(new ItemStack(Items.CRAFTING_TABLE)); }
            @Override public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder, Integer recipe, IFocusGroup focuses) {
                builder.addSlot(RecipeIngredientRole.INPUT, 192, 18).addItemStack(new ItemStack(Items.DIAMOND));
                builder.addSlot(RecipeIngredientRole.OUTPUT, 192, 46).addItemStack(new ItemStack(Items.EMERALD));
            }
            @Override public void createRecipeExtras(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder builder, Integer recipe, IFocusGroup focuses) {
                OreJeiCanvasWidget.add(builder, new Rect(0, 0, 170, 156), () -> new CanvasDemo(recipe).content);
            }
        });
    }
    @Override public void registerRecipes(IRecipeRegistration registration) { if (enabled()) registration.addRecipes(TYPE, List.of(1, 2, 3, 4)); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; }
    public static void open() { runtime.getRecipesGui().showTypes(List.of(TYPE)); }
    private static boolean enabled() { return Boolean.getBoolean("ore.canvas.demo") && RecipeCanvasView.getEnabled(RecipeCanvasView.Viewer.JEI); }
}
