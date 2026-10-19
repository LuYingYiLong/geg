package io.github.luyingyilong.ore.compat.recipeviewer.rei;

import io.github.luyingyilong.ore.compat.recipeviewer.RecipeViewerExclusions;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.gui.screens.Screen;

@REIPluginClient
public final class OreReiPlugin implements REIClientPlugin {
    @Override public void registerExclusionZones(ExclusionZones zones) {
        zones.register(Screen.class, screen -> RecipeViewerExclusions.getAreas(screen).stream()
                .map(area -> new Rectangle(area.x(), area.y(), area.width(), area.height())).toList());
    }
}
