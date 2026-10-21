package io.github.luyingyilong.ore.window.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Private vanilla rendering/state bridge implemented by the client mixin. */
public interface ContainerScreenAccess {
    void oreRenderSlot(GuiGraphics graphics,Slot slot);
    void oreRenderCarried(GuiGraphics graphics,ItemStack carried,int x,int y);
    void oreCancelGesture();
}
