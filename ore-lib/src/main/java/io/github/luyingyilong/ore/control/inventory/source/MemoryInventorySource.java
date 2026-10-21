package io.github.luyingyilong.ore.control.inventory.source;

import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalScope;
import io.github.luyingyilong.ore.signal.SignalSource;

import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Independent preview data; never writes to a player's inventory. The creator owns its lifetime. */
public final class MemoryInventorySource implements InventorySource {
    private final SignalScope scope = new SignalScope();
    private final SignalSource<Integer> changed = new SignalSource<>(this, "slotChanged", "Integer");
    private final ItemStack[] items;

    public MemoryInventorySource(int size) {
        if (size < 0) throw new IllegalArgumentException("Negative inventory size");
        items = new ItemStack[size];
        Arrays.fill(items, ItemStack.EMPTY);
    }
    @Override public SignalScope getSignalScope() { return scope; }
    @Override public int getSlotCount() { return items.length; }
    @Override public boolean isClosed() { return scope.isClosed(); }
    @Override public Signal<Integer> slotChanged() { return changed.signal(); }
    @Override public ItemStack getItem(int slot) { scope.checkThread(); return items[Objects.checkIndex(slot, items.length)].copy(); }
    public MemoryInventorySource setItem(int slot, ItemStack item) {
        scope.checkThread();
        if (isClosed()) throw new IllegalStateException("Inventory source is closed");
        Objects.checkIndex(slot, items.length);
        Objects.requireNonNull(item);
        if (!ItemStack.matches(items[slot], item)) {
            items[slot] = item.copy();
            changed.emit(slot);
        }
        return this;
    }
    @Override public void close() { scope.close(); }
}
