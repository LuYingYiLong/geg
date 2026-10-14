package io.github.luyingyilong.geg.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;


public record MarketKey(CompoundTag itemData) {
    public MarketKey {
        itemData = itemData.copy();
        itemData.putByte("Count", (byte) 1);
    }


    public static MarketKey of(ItemStack item) {
        return new MarketKey(item.copyWithCount(1).save(new CompoundTag()));
    }
}
