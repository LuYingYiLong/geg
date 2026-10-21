package io.github.luyingyilong.ore.control.inventory;
import io.github.luyingyilong.ore.support.InventoryTestSupport;

import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.inventory.source.MemoryInventorySource;
import io.github.luyingyilong.ore.control.overlay.Overlay;
import io.github.luyingyilong.ore.event.WindowInputs;
import io.github.luyingyilong.ore.window.Window;

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
        Inventory inventory=new Inventory().setShowTitle(false).setSource(source).setSlotIndices(List.of(8,2,5)).setColumns(2).setMode(Inventory.Mode.SELECT);
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
        titleLayout();
        System.out.println("ORE_INVENTORY_PASS");
    }
    private static void titleLayout(){
        MemoryInventorySource data=new MemoryInventorySource(36);
        Inventory inventory=new Inventory().setSource(data).setPlayerSlots(IntStream.range(0,27).boxed().toList(),IntStream.range(27,36).boxed().toList());
        Window window=new Window(true).setContent(inventory).setOverlay(new Overlay());
        window.layoutViewport(129,150,FONT);
        Label title=inventory.getTitleControl();Slot first=inventory.getSlotControl(0),hotbar=inventory.getSlotControl(27);
        int heading=title.lineHeight(FONT),offset=heading+inventory.getThemeItem(Inventory.TITLE_GAP);
        check(inventory.getShowTitle()&&title.getVisible(),"title is enabled by default");
        check(title.getText().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents&&contents.getKey().equals("container.inventory"),"title uses vanilla inventory translation");
        check(title.getBounds().x()==0&&title.getBounds().y()==-2&&title.getBounds().height()==heading,"title is lifted two pixels and retains its measured height");
        check(first.getBounds().y()==offset&&hotbar.getBounds().y()==offset+58&&inventory.getMinimumHeight()==76+offset,"title reserves space without changing slot geometry");
        int[] activations={0};inventory.setMode(Inventory.Mode.SELECT);inventory.slotActivated().connect(inventory,i->activations[0]++);
        click(window,3,3);check(activations[0]==0,"title does not activate a slot");
        click(window,3,offset+3);check(activations[0]==1&&inventory.getSelected()==0,"slot hit testing follows title offset");
        inventory.setShowTitle(false);check(window.getLayoutDirty(),"title visibility invalidates ancestors");window.layoutViewport(128,150,FONT);
        check(!title.getVisible()&&first.getBounds().y()==0&&hotbar.getBounds().y()==58&&inventory.getMinimumHeight()==76,"hidden title removes all title spacing");
        inventory.setShowTitle(true).setThemeOverride(Inventory.TITLE_GAP,7);
        window.layoutViewport(127,150,FONT);
        check(first.getBounds().y()==heading+7&&inventory.getSlotControl(0)==first&&inventory.getChild(0)==title,"toggle reuses title and slot instances");
        inventory.setThemeOverride(io.github.luyingyilong.ore.theme.OreTheme.FONT_SIZE,16.0F);
        window.layoutViewport(129,160,FONT);
        check(title.lineHeight(FONT)>heading&&first.getBounds().y()==title.lineHeight(FONT)+7,"font theme changes reserve the new title height");
        inventory.setThemeOverride(Inventory.TITLE_HEIGHT,10);window.layoutViewport(129,160,FONT);
        check(title.getBounds().height()==10&&first.getBounds().y()==17&&inventory.getMinimumHeight()==93,"fixed title height preserves textured slot anchors despite font metrics");
        inventory.removeThemeOverride(Inventory.TITLE_HEIGHT);window.layoutViewport(129,160,FONT);
        check(first.getBounds().y()==title.lineHeight(FONT)+7,"clearing fixed height restores automatic metrics");
        inventory.setSlotIndices(List.of(0));window.layoutViewport(18,150,FONT);
        check(inventory.getMinimumWidth()==18&&title.getBounds().width()==18,"title ellipsis does not impose its full text width as a hard minimum");
        inventory.setSource(null);window.layoutViewport(128,150,FONT);
        check(inventory.getChildCount()==1&&inventory.getChild(0)==title,"source replacement preserves only one title");
        inventory.setSource(data);window.layoutViewport(128,180,FONT);LifecycleScheduler.flush();
        check(data.slotChanged().connectionCount()==36,"source replacement does not accumulate subscriptions");
        window.queueFree();LifecycleScheduler.flush();check(title.isDisposed()&&data.slotChanged().connectionCount()==0,"title and slots are cleaned with inventory");data.close();
    }
    static void click(Window root,int x,int y){WindowInputs.press(root,x,y,null);WindowInputs.release(root,x,y);}
    static void reject(Runnable action){try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){}}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
