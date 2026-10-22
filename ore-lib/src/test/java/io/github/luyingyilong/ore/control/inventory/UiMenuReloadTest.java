package io.github.luyingyilong.ore.control.inventory;

import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.gui.*;
import io.github.luyingyilong.ore.window.*;
import io.github.luyingyilong.ore.window.container.*;
import io.github.luyingyilong.ore.signal.SignalScope;
import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.control.inventory.source.*;
import io.github.luyingyilong.ore.support.InventoryTestSupport;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class UiMenuReloadTest {
    public static final class Guard extends Control {
        boolean locked;int amount=1;
        @UiProperty public int getAmount(){return amount;}
        public Guard setAmount(int value){if(locked&&value==2)throw new IllegalArgumentException("guard");amount=value;return this;}
    }
    private static final class Menu extends AbstractContainerMenu {
        Menu(){super(null,17);var source=new SimpleContainer(4);for(int i=0;i<4;i++)addSlot(new net.minecraft.world.inventory.Slot(source,i,0,0));}
        @Override public boolean stillValid(Player player){return true;}
        @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    }
    public static void main(String[] args){
        InventoryTestSupport.initializeRegistries();
        Window window=Window.embedded();
        MenuInteraction interaction=new MenuInteraction(){
            @Override public boolean pointer(PointerEvent event){return true;}
            @Override public boolean key(KeyEvent event){return false;}
            @Override public void activate(int slot){}
            @Override public void cancel(){}
            @Override public void renderSlot(Slot view,DrawContext context,int x,int y){}
        };
        MenuSession session=new MenuSession(window,new Menu(),interaction,()->true);
        String initial="<Box id='root'><Inventory id='inventory' source='ref:source' slotIndices='0,1' mode='CONTAINER' showTitle='false'/><Slot id='slot' binding='ref:slot' mode='CONTAINER'/><test:guard id='guard' amount='1'/></Box>";
        AtomicReference<String> xml=new AtomicReference<>(initial);
        UiLoader loader=new UiLoader(UiTypeRegistry.createDefault().register("test:guard",node -> new Guard(),node -> "",Map.of()),id -> new StringReader(xml.get()),Runnable::run);
        UiBindings bindings=new UiBindings().value("source",InventorySource.class,session.getSource()).value("slot",SlotBinding.class,session.getBinding(2));
        try(SignalScope owner=new SignalScope();UiDocument document=loader.open(ResourceLocation.parse("test:gui/menu.xml"),bindings,owner)){
            window.setContent(document.getRoot());window.layoutViewport(128,120,InventoryTestSupport.FONT);LifecycleScheduler.flush();
            Inventory inventory=document.getRoot().findControlFromId("inventory",Inventory.class);Slot first=inventory.getSlotControl(0);
            for(int i=0;i<3;i++){
                document.reload();UiRuntime.flush();LifecycleScheduler.flush();
                check(document.getStatus()==UiDocument.Status.READY,document.getError());
                check(document.getRoot().findControlFromId("inventory")==inventory && inventory.getSlotControl(0)==first,"Staging cannot claim duplicate Menu slots");
                check(session.getSource().slotChanged().connectionCount()==3,"Candidate subscriptions cleaned once");
            }
            Guard guard=document.getRoot().findControlFromId("guard",Guard.class);guard.locked=true;
            xml.set(initial.replace("slotIndices='0,1'","slotIndices='1,0'").replace("amount='1'","amount='2'"));
            document.reload();UiRuntime.flush();LifecycleScheduler.flush();
            check(document.getStatus()==UiDocument.Status.ERROR && inventory.getMode()==Inventory.Mode.CONTAINER,"Failure restores original interactive Menu views");
            session.validateView(first,0);
            xml.set(initial.replace("slotIndices='0,1'","slotIndices='1,0'"));document.reload();UiRuntime.flush();LifecycleScheduler.flush();
            check(document.getStatus()==UiDocument.Status.READY,document.getError());
            Inventory replacement=document.getRoot().findControlFromId("inventory",Inventory.class);
            check(replacement!=inventory && inventory.isDisposed() && replacement.getMode()==Inventory.Mode.CONTAINER,"Replacement hands real slot ownership to the new view");
            check(!session.getSource().isClosed(),"UI replacement cannot close Menu storage");
        } finally {window.queueFree();UiRuntime.flush();LifecycleScheduler.flush();}
        System.out.println("ORE_XML_MENU_RELOAD_PASS");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
