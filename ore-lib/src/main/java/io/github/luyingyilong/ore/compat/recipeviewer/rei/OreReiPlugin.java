package io.github.luyingyilong.ore.compat.recipeviewer.rei;

import io.github.luyingyilong.ore.compat.recipeviewer.RecipeViewerExclusions;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.gui.screens.Screen;

@REIPluginClient
public final class OreReiPlugin implements REIClientPlugin {
    @Override public void registerScreens(me.shedaniel.rei.api.client.registry.screen.ScreenRegistry registry){
        registry.registerFocusedStack(new me.shedaniel.rei.api.client.registry.screen.FocusedStackProvider(){
            @Override public double getPriority(){return 1000;}
            @Override public dev.architectury.event.CompoundEventResult<me.shedaniel.rei.api.common.entry.EntryStack<?>> provide(Screen screen,me.shedaniel.math.Point point){
            if(screen instanceof io.github.luyingyilong.ore.menu.OreMenuScreen<?> menu&&menu.getMenuSession()!=null){
                var slot=menu.getMenuSession().getNativeSlotAt(point.x,point.y);
                return dev.architectury.event.CompoundEventResult.interruptTrue(slot==null
                        ?me.shedaniel.rei.api.common.entry.EntryStack.empty():me.shedaniel.rei.api.common.util.EntryStacks.of(slot.getItem()));
            }
            return dev.architectury.event.CompoundEventResult.pass();
            }
        });
    }
    @Override public void registerExclusionZones(ExclusionZones zones) {
        zones.register(Screen.class, screen -> RecipeViewerExclusions.getAreas(screen).stream()
                .map(area -> new Rectangle(area.x(), area.y(), area.width(), area.height())).toList());
    }
}
