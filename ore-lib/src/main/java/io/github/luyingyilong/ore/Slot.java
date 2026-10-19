package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.debug.DebugBridge;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Read-only item preview. It never moves items or participates in inventory clicks. */
public final class Slot extends Control {
    public static final int SIZE = 26;
    private ItemStack item = ItemStack.EMPTY;
    private int quantity;
    private boolean backgroundVisible = true;
    private final Label count = ownPart("count", new Label().setColor(OreTheme.TEXT_WHITE).setShadow(true)
            .setAlignment(Label.Horizontal.RIGHT, Label.Vertical.BOTTOM).setFontSize(8));

    public Slot() {
        setMinimum(SIZE, SIZE);
        setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        addChild(count);
    }

    public Slot setItem(ItemStack item, int quantity) {
        this.item = item.copyWithCount(1); this.quantity = quantity;
        count.setText(Component.literal(Integer.toString(quantity)));
        count.setVisible(quantity > 1 && !item.isEmpty());
        return this;
    }
    public ItemStack getItem() { return item.copy(); }
    public int getQuantity() { return quantity; }

    public Slot setBackgroundVisible(boolean visible) {
        backgroundVisible = visible;
        return this;
    }
    public boolean getBackgroundVisible() { return backgroundVisible; }

    @Override public boolean hasTooltip() { return !item.isEmpty(); }

    @Override public void renderTooltip(ModularGuiContext context, int mouseX, int mouseY) {
        context.getGraphics().renderTooltip(context.getFont(), item, mouseX, mouseY);
    }

    @Override public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
        super.layout(bounds, font);
        count.layout(new Rect(1, 12, Math.max(0, bounds.width() - 2), 12), font);
    }

    @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        if (backgroundVisible) {
            var style = context.getTheme().getItemSlotTheme().theme();
            if (style.getBackground() != null)
                style.getBackground().draw(context, area.x(), area.y(), area.width(), area.height(), style);
        }
        if (item.isEmpty()) return;
        context.getGraphics().renderItem(item, area.x() + (area.width() - 16) / 2, area.y() + 3);
    }

    @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            render(context, screenX, screenY);
            if (!count.getVisible()) return;
            var graphics = context.getGraphics();
            graphics.flush(); graphics.pose().pushPose();
            try {
                // Match vanilla item-decoration depth so the quantity stays above the item model.
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                count.draw(context, screenX, screenY); graphics.flush();
            } finally { graphics.pose().popPose(); }
        }
    }
}
