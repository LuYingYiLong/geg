package io.github.luyingyilong.geg.mail;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** A sample with a separate quantity, so attachments may exceed an ordinary stack. */
public record MailItem(ItemStack item, int quantity) {
    public MailItem {
        if (item.isEmpty() || quantity <= 0) throw new IllegalArgumentException("Invalid mail item");
        item = item.copyWithCount(1);
    }

    @Override public ItemStack item() { return item.copy(); }

    @Override public boolean equals(Object other) {
        return other instanceof MailItem value && quantity == value.quantity
                && io.github.luyingyilong.geg.market.MarketKey.of(item)
                .equals(io.github.luyingyilong.geg.market.MarketKey.of(value.item));
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(io.github.luyingyilong.geg.market.MarketKey.of(item), quantity);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("item", item.save(new CompoundTag()));
        tag.putInt("quantity", quantity);
        return tag;
    }

    public static MailItem load(CompoundTag tag) {
        return new MailItem(ItemStack.of(tag.getCompound("item")), tag.getInt("quantity"));
    }
}
