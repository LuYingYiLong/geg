package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.inventory.*;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import java.util.*;
import java.util.stream.IntStream;

public final class InventoryTest {
    static final Font FONT=new Font(id->null,false){@Override public int width(FormattedText t){return t.getString().length()*6;}};
    public static void main(String[] args){
        InventoryTestSupport.initializeRegistries();
        MemoryInventorySource source=new MemoryInventorySource(40);
        var original=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT,12);
        source.setItem(8,original);original.setCount(2);
        var copy=source.getItem(8);copy.setCount(1);
        check(source.getItem(8).getCount()==12,"source copies both incoming and outgoing stacks");
        Inventory inventory=new Inventory().setSource(source).setSlotIndices(List.of(8,2,5)).setColumns(2).setMode(Inventory.Mode.SELECT);
        Slot retained=inventory.getSlotControl(8);int[] activated={0},changed={0};
        inventory.slotActivated().connect(inventory,i->activated[0]++);inventory.selectionChanged().connect(inventory,i->changed[0]++);
        Window root=new Window(true).setContent(inventory).setOverlay(new Overlay());root.layoutViewport(129,100,FONT);
        check(inventory.getSlotControl(8).getBounds().equals(new Rect(0,0,18,18)),"source IDs are separate from display order");
        click(root,3,3);check(inventory.getSelected()==8&&activated[0]==1&&changed[0]==1,"release activates selection once");
        click(root,3,3);check(activated[0]==2&&changed[0]==1,"same selection still activates");
        WindowInputs.press(root,3,3,null);WindowInputs.release(root,120,90);check(activated[0]==2,"drag-out cancels selection");
        inventory.setSelectedNoSignal(2);check(changed[0]==1,"silent refill");
        inventory.setSlotIndices(List.of(5,8,2));check(inventory.getSlotControl(8)==retained,"reordering reuses views");
        LifecycleScheduler.flush();
        check(source.slotChanged().connectionCount()==3,"one binding subscription per view after safe cleanup");
        for(int i=0;i<20;i++){root.layoutViewport(127+i%3,100,FONT);inventory.setSlotIndices(i%2==0?List.of(8,2,5):List.of(5,8,2));}
        check(source.slotChanged().connectionCount()==3,"repaint/reorder does not accumulate subscriptions");
        reject(()->inventory.setSlotIndices(List.of(8,8)));reject(()->inventory.setMode(Inventory.Mode.CONTAINER));
        check(inventory.getSlotIndices().size()==3&&inventory.getMode()==Inventory.Mode.SELECT,"rejection keeps prior binding");
        inventory.setPlayerSlots(IntStream.range(0,27).boxed().toList(),IntStream.range(27,36).boxed().toList());root.layoutViewport(127,140,FONT);
        check(inventory.getMinimumWidth()==162&&inventory.getMinimumHeight()==76,"player inventory keeps 9 columns and section gap");
        check(inventory.getSlotControl(27).getBounds().y()==58,"hotbar separated without changing IDs");
        root.queueFree();LifecycleScheduler.flush();check(source.slotChanged().connectionCount()==0,"all slot binding scopes end with tree");
        check(!source.isClosed(),"view does not own shared storage");source.close();
        System.out.println("ORE_INVENTORY_PASS");
    }
    static void click(Window root,int x,int y){WindowInputs.press(root,x,y,null);WindowInputs.release(root,x,y);}
    static void reject(Runnable action){try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){}}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
