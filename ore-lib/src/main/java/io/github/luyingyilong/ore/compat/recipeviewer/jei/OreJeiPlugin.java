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
    private mezz.jei.api.runtime.IJeiRuntime runtime;
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime){this.runtime=runtime;}
    @Override public void onRuntimeUnavailable(){runtime=null;}
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("ore", "gui_exclusions"); }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override public java.util.Optional<mezz.jei.api.runtime.IClickableIngredient<?>> getClickableIngredientUnderMouse(double x,double y){
                if(runtime==null||!(Minecraft.getInstance().screen instanceof io.github.luyingyilong.ore.menu.OreMenuScreen<?> screen)
                        ||screen.getMenuSession()==null)return java.util.Optional.empty();
                var view=screen.getMenuSession().getSlotAt((int)x,(int)y);
                if(view==null||view.getItem().isEmpty())return java.util.Optional.empty();
                var r=io.github.luyingyilong.ore.event.EventGeometry.displayed(view);
                return runtime.getIngredientManager().createClickableIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                        view.getItem(),new Rect2i(r.x(),r.y(),r.width(),r.height()),false)
                        .map(value->(mezz.jei.api.runtime.IClickableIngredient<?>)value);
            }
            @Override public Collection<Rect2i> getGuiExtraAreas() {
                Minecraft minecraft = Minecraft.getInstance();
                return RecipeViewerExclusions.getAreas(minecraft == null ? null : minecraft.screen).stream()
                        .map(area -> new Rect2i(area.x(), area.y(), area.width(), area.height())).toList();
            }
        });
    }
}
