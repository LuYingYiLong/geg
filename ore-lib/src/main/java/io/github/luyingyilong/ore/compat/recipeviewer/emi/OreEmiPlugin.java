package io.github.luyingyilong.ore.compat.recipeviewer.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiExclusionArea;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import io.github.luyingyilong.ore.compat.recipeviewer.RecipeViewerExclusions;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

@EmiEntrypoint
public final class OreEmiPlugin implements EmiPlugin, EmiExclusionArea<Screen> {
    @Override public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea(this);
    }

    @Override public void addExclusionArea(Screen screen, Consumer<Bounds> consumer) {
        RecipeViewerExclusions.getAreas(screen).forEach(
                area -> consumer.accept(new Bounds(area.x(), area.y(), area.width(), area.height())));
    }
}
