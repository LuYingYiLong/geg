package io.github.luyingyilong.ore.compat.recipeviewer.jei;

import io.github.luyingyilong.ore.compat.recipeviewer.RecipeViewerExclusions;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

/** Only JEI's plugin discovery loads this class. Ore has no mandatory JEI dependency. */
@JeiPlugin
public final class OreJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("ore", "gui_exclusions"); }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override public Collection<Rect2i> getGuiExtraAreas() {
                Minecraft minecraft = Minecraft.getInstance();
                return RecipeViewerExclusions.getAreas(minecraft == null ? null : minecraft.screen).stream()
                        .map(area -> new Rect2i(area.x(), area.y(), area.width(), area.height())).toList();
            }
        });
    }
}
