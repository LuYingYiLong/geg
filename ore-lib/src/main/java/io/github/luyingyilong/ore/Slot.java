package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Read-only item preview. It never moves items or participates in inventory clicks.
 */
public final class Slot extends Control {
    public static final int SIZE = 26;
    private final Label count = ownPart("count", new Label().setShadow(true)
            .setAlignment(Label.Horizontal.RIGHT, Label.Vertical.BOTTOM).setFontSize(8));
    private ItemStack item = ItemStack.EMPTY;
    private int quantity;
    private boolean backgroundVisible = true;

    public Slot() {
        setMinimum(SIZE, SIZE);
        setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        updateChildren(java.util.List.of(count));
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Slot");
    }

    @Override
    protected void validateChildren(java.util.List<io.github.luyingyilong.ore.layout.Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Slot has a fixed skeleton; configure its properties");
    }

    public Slot setItem(ItemStack item, int quantity) {
        this.item = item.copyWithCount(1);
        this.quantity = quantity;
        count.setText(Component.literal(Integer.toString(quantity)));
        count.setVisible(quantity > 1 && !item.isEmpty());
        return this;
    }

    public ItemStack getItem() {
        return item.copy();
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean getBackgroundVisible() {
        return backgroundVisible;
    }

    public Slot setBackgroundVisible(boolean visible) {
        backgroundVisible = visible;
        return this;
    }

    @Override
    public boolean hasTooltip() {
        return !item.isEmpty();
    }

    @Override
    public void renderTooltip(DrawContext context, int mouseX, int mouseY) {
        context.getGraphics().renderTooltip(context.getFont(), item, mouseX, mouseY);
    }

    @Override
    public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
        super.layout(bounds, font);
        count.layout(new Rect(1, 12, Math.max(0, bounds.width() - 2), 12), font);
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        if (backgroundVisible) {
            getThemeItem(OreTheme.SLOT_PANEL).draw(context, area);
        }
        if (item.isEmpty()) return;
        context.getGraphics().renderItem(item, area.x() + (area.width() - 16) / 2, area.y() + 3);
    }

    @Override
    protected void drawContent(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            render(context, screenX, screenY);
            if (!count.getVisible()) return;
            var graphics = context.getGraphics();
            graphics.flush();
            graphics.pose().pushPose();
            try {
                // Match vanilla item-decoration depth so the quantity stays above the item model.
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                count.draw(context, screenX, screenY);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }
}
