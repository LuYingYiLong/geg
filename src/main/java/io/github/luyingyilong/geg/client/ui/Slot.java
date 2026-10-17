package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.SizeFlags;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Read-only reward preview. It never moves items or participates in inventory clicks. */
public final class Slot extends Control {
    public static final int SIZE = 26;
    private ItemStack item = ItemStack.EMPTY;
    private int quantity;
    private final Label count = new Label().color(OreTheme.TEXT_WHITE).shadow(true)
            .align(Label.Horizontal.RIGHT, Label.Vertical.BOTTOM).fontSize(8);

    public Slot() {
        minimum(SIZE, SIZE);
        sizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).sizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        addChild(count);
    }

    public Slot item(ItemStack item, int quantity) {
        this.item = item.copyWithCount(1); this.quantity = quantity;
        count.text(Component.literal(Integer.toString(quantity)));
        count.visible(quantity > 1 && !item.isEmpty());
        return this;
    }
    public ItemStack item() { return item.copy(); }
    public int quantity() { return quantity; }

    @Override public boolean hasTooltip() { return !item.isEmpty(); }

    @Override public void renderTooltip(ModularGuiContext context, int mouseX, int mouseY) {
        context.getGraphics().renderTooltip(context.getFont(), item, mouseX, mouseY);
    }

    @Override public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
        super.layout(bounds, font);
        count.layout(new Rect(1, 12, Math.max(0, bounds.width() - 2), 12), font);
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = screenBounds(screenX, screenY);
        var style = context.getTheme().getItemSlotTheme().theme();
        if (style.getBackground() != null)
            style.getBackground().draw(context, area.x(), area.y(), area.width(), area.height(), style);
        if (item.isEmpty()) return;
        context.getGraphics().renderItem(item, area.x() + (area.width() - 16) / 2, area.y() + 3);
    }

    @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
        render(context, screenX, screenY);
        if (!count.visible()) return;
        var graphics = context.getGraphics();
        graphics.flush(); graphics.pose().pushPose();
        try {
            // Match vanilla item-decoration depth so the quantity stays above the item model.
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            count.draw(context, screenX, screenY); graphics.flush();
        } finally { graphics.pose().popPose(); }
    }
}
