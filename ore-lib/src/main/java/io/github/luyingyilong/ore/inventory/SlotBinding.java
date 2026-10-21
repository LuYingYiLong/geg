package io.github.luyingyilong.ore.inventory;

import java.util.Objects;

/** The index belongs to the source; a Menu source uses actual menu slot IDs. */
public record SlotBinding(InventorySource source, int slot) {
    public SlotBinding {
        Objects.requireNonNull(source);
        source.getSignalScope().checkThread();
        if (source.isClosed()) throw new IllegalStateException("Inventory source is closed");
        Objects.checkIndex(slot, source.getSlotCount());
    }
}
