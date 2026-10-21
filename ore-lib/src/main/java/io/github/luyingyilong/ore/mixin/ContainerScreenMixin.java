package io.github.luyingyilong.ore.mixin;

import io.github.luyingyilong.ore.window.container.ContainerScreenAccess;
import io.github.luyingyilong.ore.window.container.OreMenuScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Group;
import java.util.Set;

/** All behavioral changes are gated to OreMenuScreen. Aliases support official dev and SRG release names. */
@Mixin(value=AbstractContainerScreen.class,remap=false)
public abstract class ContainerScreenMixin implements ContainerScreenAccess {
    @Shadow(aliases="f_97711_") private ItemStack draggingItem;
    @Shadow(aliases="f_97710_") private boolean isSplittingStack;
    @Shadow(aliases="f_97720_") private int quickCraftingRemainder;
    @Shadow(aliases="f_97719_") private boolean skipNextRelease;
    @Shadow(aliases="f_97723_") private boolean doubleclick;
    @Shadow(aliases="f_97721_") private long lastClickTime;
    @Shadow(aliases="f_97709_") private Slot lastClickSlot;
    @Shadow(aliases="f_97706_") private Slot clickedSlot;
    @Shadow(aliases="f_97708_") private Slot quickdropSlot;
    @Shadow(aliases="f_97715_") private ItemStack snapbackItem;
    @Shadow(aliases="f_97738_") protected boolean isQuickCrafting;
    @Shadow(aliases="f_97737_") @Final protected Set<Slot> quickCraftSlots;
    @Shadow(aliases="m_280092_") private void renderSlot(GuiGraphics graphics,Slot slot){throw new AssertionError();}
    @Shadow(aliases="m_280211_") private void renderFloatingItem(GuiGraphics graphics,ItemStack stack,int x,int y,String count){throw new AssertionError();}

    @Inject(method={"findSlot","m_97744_"},at=@At("HEAD"),cancellable=true)
    private void ore$find(double x,double y,CallbackInfoReturnable<Slot> result){
        if((Object)this instanceof OreMenuScreen<?> screen)
            result.setReturnValue(screen.getMenuSession()==null?null:screen.getMenuSession().getNativeSlotAt(x,y));
    }
    @Group(name="oreModifiers",min=1)
    @Redirect(method={"mouseClicked","mouseReleased","m_6375_","m_6348_"},
            at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(JI)Z"),require=0)
    private boolean ore$modifiers(long handle,int key){return ore$keyDown(handle,key);}
    @Group(name="oreModifiers",min=1)
    @Redirect(method={"mouseClicked","mouseReleased","m_6375_","m_6348_"},
            at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/InputConstants;m_84830_(JI)Z"),require=0)
    private boolean ore$srgModifiers(long handle,int key){return ore$keyDown(handle,key);}
    @Unique private boolean ore$keyDown(long handle,int key){
        if((Object)this instanceof OreMenuScreen<?> screen&&screen.getOreWindow()!=null&&(key==340||key==344))
            return (screen.getOreWindow().getContext().getModifiers()&org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT)!=0;
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(handle,key);
    }
    @Override public void oreRenderSlot(GuiGraphics graphics,Slot slot){renderSlot(graphics,slot);}
    @Override public void oreRenderCarried(GuiGraphics graphics,ItemStack carried,int x,int y){
        ItemStack stack=draggingItem.isEmpty()?carried:draggingItem;
        String count=null;
        if(!draggingItem.isEmpty()&&isSplittingStack)stack=stack.copyWithCount((stack.getCount()+1)/2);
        else if(isQuickCrafting&&quickCraftSlots.size()>1){stack=stack.copyWithCount(quickCraftingRemainder);if(stack.isEmpty())count="\u00a7e0";}
        if(!stack.isEmpty()||count!=null)renderFloatingItem(graphics,stack,x-8,y-(draggingItem.isEmpty()?8:16),count);
    }
    @Override public void oreCancelGesture(){
        draggingItem=ItemStack.EMPTY;clickedSlot=null;quickdropSlot=null;snapbackItem=ItemStack.EMPTY;
        isQuickCrafting=false;quickCraftSlots.clear();skipNextRelease=true;doubleclick=false;lastClickTime=0;lastClickSlot=null;
    }
}
