package io.github.luyingyilong.ore.inventory;

import io.github.luyingyilong.ore.menu.MenuSession;
import io.github.luyingyilong.ore.signal.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Observes a client Menu. A comparison pass also catches packet application and prediction updates. */
public final class MenuInventorySource implements InventorySource {
    private final SignalScope scope=new SignalScope();
    private final SignalSource<Integer> changed=new SignalSource<>(this,"slotChanged","Integer");
    private final MenuSession session;
    private final ItemStack[] snapshots;
    private final ContainerListener listener=new ContainerListener(){
        @Override public void slotChanged(AbstractContainerMenu menu,int slot,ItemStack stack){refresh(slot);}
        @Override public void dataChanged(AbstractContainerMenu menu,int index,int value){ }
    };
    public MenuInventorySource(MenuSession session){
        this.session=Objects.requireNonNull(session);snapshots=new ItemStack[session.getMenu().slots.size()];
        Arrays.fill(snapshots,ItemStack.EMPTY);
        session.getMenu().addSlotListener(listener);
        scope.own(()->session.getMenu().removeSlotListener(listener));
        refresh();
    }
    @Override public SignalScope getSignalScope(){return scope;}
    public MenuSession getSession(){return session;}
    @Override public int getSlotCount(){return snapshots.length;}
    @Override public boolean isClosed(){return scope.isClosed();}
    @Override public Signal<Integer> slotChanged(){return changed.signal();}
    @Override public ItemStack getItem(int slot){scope.checkThread();Objects.checkIndex(slot,snapshots.length);return session.getMenu().getSlot(slot).getItem().copy();}
    public void refresh(){scope.checkThread();if(!isClosed())for(int i=0;i<snapshots.length;i++)refresh(i);}
    private void refresh(int slot){
        if(isClosed()||slot<0||slot>=snapshots.length)return;
        ItemStack stack=session.getMenu().getSlot(slot).getItem();
        if(!ItemStack.matches(snapshots[slot],stack)){snapshots[slot]=stack.copy();changed.emit(slot);}
    }
    @Override public void close(){scope.close();}
}
