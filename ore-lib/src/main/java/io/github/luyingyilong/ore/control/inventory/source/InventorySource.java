package io.github.luyingyilong.ore.control.inventory.source;

import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalOwner;
import net.minecraft.world.item.ItemStack;

/** Fixed-address inventory view. Returned stacks are snapshots, never writable storage. */
public interface InventorySource extends SignalOwner, AutoCloseable {
    int getSlotCount();
    ItemStack getItem(int slot);
    Signal<Integer> slotChanged();
    boolean isClosed();
    @Override void close();

    default SlotBinding getBinding(int slot) { return new SlotBinding(this, slot); }
}
