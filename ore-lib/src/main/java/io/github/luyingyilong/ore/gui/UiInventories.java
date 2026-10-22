package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.inventory.Inventory;
import io.github.luyingyilong.ore.control.inventory.source.InventorySource;
import io.github.luyingyilong.ore.control.inventory.source.MemoryInventorySource;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class UiInventories {
    private UiInventories() {
    }

    static Inventory create(UiBlueprint.Node node, List<Control> children, UiBindings bindings) {
        Inventory inventory = new Inventory();
        try {
            InventorySource source;
            if (node.attributes().containsKey("source")) {
                String ref = node.require("source");
                if (!ref.startsWith("ref:"))
                    throw new UiException(node.source(), "Inventory source must reference a Java InventorySource");
                source = (InventorySource) bindings.requireValue(ref.substring(4), InventorySource.class);
                if (!UiTypeRegistry.items(node).isEmpty())
                    throw new UiException(node.source(), "Shared inventory sources cannot be modified by XML item entries");
            } else {
                int count = UiBuiltins.integer(node, "slotCount", 36);
                if (count < 0 || count > 4096) throw new UiException(node.source(), "slotCount must be in 0..4096");
                MemoryInventorySource memory = new MemoryInventorySource(count);
                inventory.getSignalScope().own(memory);
                Set<Integer> used = new HashSet<>();
                for (var item : UiTypeRegistry.items(node)) {
                    UiTypeRegistry.attributes(item, Set.of("index", "item", "count"));
                    if (!item.children().isEmpty())
                        throw new UiException(item.source(), "Inventory item cannot contain children");
                    int index = UiBuiltins.integer(item, "index", -1);
                    if (!used.add(index)) throw new UiException(item.source(), "Duplicate inventory slot " + index);
                    ItemStack stack = (ItemStack) UiCodecs.parse(ItemStack.class, item.require("item"), "item");
                    int amount = UiBuiltins.integer(item, "count", stack.getCount());
                    if (amount < 0) throw new UiException(item.source(), "Negative item count");
                    stack.setCount(amount);
                    memory.setItem(index, stack);
                }
                source = memory;
            }
            inventory.setSource(source);
            inventory.setColumns(UiBuiltins.integer(node, "columns", 9));
            if (node.attributes().containsKey("mainSlots") || node.attributes().containsKey("hotbarSlots")) {
                if (node.attributes().containsKey("slotIndices"))
                    throw new UiException(node.source(), "Use slotIndices or mainSlots/hotbarSlots");
                inventory.setPlayerSlots(indices(node.require("mainSlots")), indices(node.require("hotbarSlots")));
            } else if (node.attributes().containsKey("slotIndices"))
                inventory.setSlotIndices(indices(node.require("slotIndices")));
            return inventory;
        } catch (RuntimeException | Error failure) {
            inventory.queueFree("XML inventory factory failed");
            throw failure;
        }
    }

    static List<Integer> indices(String value) {
        if (value.isBlank()) return List.of();
        return Arrays.stream(value.split(",", -1)).map(String::strip).map(Integer::valueOf).toList();
    }
}
