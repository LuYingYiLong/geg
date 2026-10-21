package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.menu.*;
import io.github.luyingyilong.ore.render.DrawContext;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class MenuSessionTest {
    private static final class TestMenu extends AbstractContainerMenu {
        TestMenu(){super(null,17);SimpleContainer storage=new SimpleContainer(8);for(int i=0;i<8;i++)addSlot(new net.minecraft.world.inventory.Slot(storage,i,900+i*18,900));}
        @Override public boolean stillValid(Player player){return true;}
        @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    }
    private static final class Interaction implements MenuInteraction {
        MenuSession session;final List<Integer> releases=new ArrayList<>();int cancels,presses,moves;
        @Override public boolean pointer(PointerEvent event){
            var slot=session.getNativeSlotAt(event.windowX(),event.windowY());
            if(event.action()==PointerEvent.Action.PRESS)presses++;
            if(event.action()==PointerEvent.Action.MOVE)moves++;
            if(event.action()==PointerEvent.Action.RELEASE&&slot!=null)releases.add(slot.index);
            return event.action()!=PointerEvent.Action.MOVE;
        }
        @Override public boolean key(KeyEvent event){return false;}
        @Override public void activate(int slotId){releases.add(slotId);}
        @Override public void cancel(){cancels++;}
        @Override public void renderSlot(Slot view,DrawContext context,int x,int y){}
    }
    public static void main(String[] args){
        InventoryTestSupport.initializeRegistries();
        Window root=new Window(true);boolean[] valid={true};Interaction interaction=new Interaction();
        MenuSession session=new MenuSession(root,new TestMenu(),interaction,()->valid[0]);interaction.session=session;
        try{new MenuSession(root,new TestMenu(),interaction,()->true);throw new AssertionError("Duplicate session accepted");}
        catch(IllegalStateException expected){ }
        Inventory a=new Inventory().setSource(session.getSource()).setSlotIndices(List.of(0,1,2,3)).setColumns(2).setMode(Inventory.Mode.CONTAINER);
        Inventory b=new Inventory().setSource(session.getSource()).setSlotIndices(List.of(4,5,6,7)).setColumns(2).setMode(Inventory.Mode.CONTAINER);
        Box content=new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);content.addChild(a);content.addChild(b);
        Scroll scroll=new Scroll().setHorizontal(Scroll.BarMode.NEVER).setVertical(Scroll.BarMode.NEVER).setContent(content);
        root.setContent(scroll).setOverlay(new Overlay());root.layoutViewport(52,70,InventoryTest.FONT);
        scroll.setScroll(25,0);
        InventoryTest.check(scroll.getScrollX()>0,"test uses real horizontal overflow");
        Rect target=EventGeometry.displayed(b.getSlotControl(4));int x=target.x()+10,y=target.y()+10;
        InventoryTest.check(session.getNativeSlotAt(x,y).index==4,"real ID follows scrolled Ore geometry, not vanilla x=900");
        InventoryTest.click(root,x,y);InventoryTest.check(interaction.presses==1&&interaction.releases.equals(List.of(4)),"one dispatch, one action");
        root.dispatchInput(new PointerEvent(root.getContext(),PointerEvent.Action.MOVE,x,y,-1,0));
        InventoryTest.check(interaction.moves==1,"unhandled slot motion must not be dispatched a second time through the host");
        var first=a.getSlotControl(1);Rect p=EventGeometry.displayed(first);
        WindowInputs.press(root,p.x()+10,p.y()+10,null);WindowInputs.release(root,x,y);
        InventoryTest.check(interaction.releases.equals(List.of(4,4)),"captured gesture crosses inventory views");
        InventoryTest.reject(()->new Slot().setBinding(session.getBinding(4)).setMode(Inventory.Mode.CONTAINER));
        InventoryTest.check(session.activate(b.getSlotControl(4)),"keyboard activation routes through session");
        WindowInputs.press(root,x,y,null);int before=interaction.releases.size();
        b.setVisible(false);WindowInputs.release(root,x,y);InventoryTest.check(interaction.releases.size()==before,"hidden captured view cannot release onto another item");
        valid[0]=false;session.update();InventoryTest.check(!b.getSlotControl(4).acceptsFocus(),"ended menu disables input");
        root.queueFree();LifecycleScheduler.flush();InventoryTest.check(session.getSource().isClosed(),"session source listener ends with host");
        System.out.println("ORE_MENU_SESSION_PASS");
    }
}
