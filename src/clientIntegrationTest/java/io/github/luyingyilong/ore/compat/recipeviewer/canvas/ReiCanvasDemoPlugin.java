package io.github.luyingyilong.ore.compat.recipeviewer.canvas;

import io.github.luyingyilong.ore.compat.recipeviewer.RecipeCanvasView;
import io.github.luyingyilong.ore.compat.recipeviewer.rei.OreReiCanvasWidget;
import me.shedaniel.math.*;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.*;
import me.shedaniel.rei.api.client.gui.widgets.*;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.util.*;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

@REIPluginClient
public final class ReiCanvasDemoPlugin implements REIClientPlugin {
    private static final CategoryIdentifier<Display> CATEGORY = CategoryIdentifier.of("ore", "canvas_demo");
    private static final class Display extends BasicDisplay {
        final int index;
        Display(int index) { super(List.of(EntryIngredients.of(Items.DIAMOND)), List.of(EntryIngredients.of(Items.EMERALD))); this.index = index; }
        @Override public CategoryIdentifier<?> getCategoryIdentifier() { return CATEGORY; }
    }
    private static boolean enabled() { return Boolean.getBoolean("ore.canvas.demo") && RecipeCanvasView.getEnabled(RecipeCanvasView.Viewer.REI); }
    @Override public void registerCategories(CategoryRegistry registry) {
        if (!enabled()) return;
        registry.add(new DisplayCategory<Display>() {
            @Override public CategoryIdentifier<Display> getCategoryIdentifier() { return CATEGORY; }
            @Override public Component getTitle() { return Component.literal("Ore Canvas Demo"); }
            @Override public me.shedaniel.rei.api.client.gui.Renderer getIcon() { return EntryStacks.of(Items.CRAFTING_TABLE); }
            @Override public int getDisplayWidth(Display display) { return 228; }
            @Override public int getDisplayHeight() { return 166; }
            @Override public List<Widget> setupDisplay(Display display, Rectangle bounds) {
                return List.of(Widgets.createRecipeBase(bounds),
                        Widgets.createSlot(new Point(bounds.x + 196, bounds.y + 22)).entries(display.getInputEntries().get(0)).markInput(),
                        Widgets.createSlot(new Point(bounds.x + 196, bounds.y + 50)).entries(display.getOutputEntries().get(0)).markOutput(),
                        new OreReiCanvasWidget(new Rectangle(bounds.x + 4, bounds.y + 4, 170, 156), () -> new CanvasDemo(display.index).content));
            }
        });
    }
    @Override public void registerDisplays(DisplayRegistry registry) { if (enabled()) for (int i = 1; i <= 4; i++) registry.add(new Display(i)); }
    public static void open() { me.shedaniel.rei.api.client.view.ViewSearchBuilder.builder().addCategory(CATEGORY).open(); }
}
