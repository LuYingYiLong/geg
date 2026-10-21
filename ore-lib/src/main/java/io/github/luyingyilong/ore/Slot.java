package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.theme.ThemeKey;
import io.github.luyingyilong.ore.theme.ThemePadding;
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
    public static final ThemeKey<Float> COUNT_FONT_SIZE = new ThemeKey<>("SlotCount", "font_size", Float.class,
            ThemeKey.Kind.FLOAT, 8.0F, true, value -> Float.isFinite(value) && value > 0);
    private static final ThemePadding PADDING = ThemePadding.of("Slot", "", 3, 5, 7, 5);
    public static final ThemeKey<Integer> PADDING_TOP = PADDING.top();
    public static final ThemeKey<Integer> PADDING_RIGHT = PADDING.right();
    public static final ThemeKey<Integer> PADDING_BOTTOM = PADDING.bottom();
    public static final ThemeKey<Integer> PADDING_LEFT = PADDING.left();
    private static final ThemePadding COUNT_PADDING = ThemePadding.of("Slot", "count_", 12, 1, 2, 1);
    public static final ThemeKey<Integer> COUNT_PADDING_TOP = COUNT_PADDING.top();
    public static final ThemeKey<Integer> COUNT_PADDING_RIGHT = COUNT_PADDING.right();
    public static final ThemeKey<Integer> COUNT_PADDING_BOTTOM = COUNT_PADDING.bottom();
    public static final ThemeKey<Integer> COUNT_PADDING_LEFT = COUNT_PADDING.left();

    public static final int SIZE = 26;
    private final Label count = ownPart("count", new Label().setThemeTypeVariation("SlotCount").setShadow(true)
            .setAlignment(Label.Horizontal.RIGHT, Label.Vertical.BOTTOM));
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
        db.registerVariation("SlotCount", "Label");
        db.register(COUNT_FONT_SIZE);
        PADDING.register(db); COUNT_PADDING.register(db);
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
        count.layout(COUNT_PADDING.getInsets(this).inset(getThemeItem(OreTheme.SLOT_PANEL).getContentRect(new Rect(0, 0, bounds.width(), bounds.height()))), font);
    }

    @Override public int measureWidth(net.minecraft.client.gui.Font font, int available) {
        return Math.max(super.measureWidth(font, available), 16 + PADDING.getInsets(this).getHorizontal() + getThemeItem(OreTheme.SLOT_PANEL).getContentMargin().getHorizontal());
    }

    @Override public int measureHeight(net.minecraft.client.gui.Font font, int available) {
        return Math.max(super.measureHeight(font, available), 16 + PADDING.getInsets(this).getVertical() + getThemeItem(OreTheme.SLOT_PANEL).getContentMargin().getVertical());
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        if (backgroundVisible) {
            getThemeItem(OreTheme.SLOT_PANEL).draw(context, area);
        }
        if (item.isEmpty()) return;
        Rect inner = PADDING.getInsets(this).inset(getThemeItem(OreTheme.SLOT_PANEL).getContentRect(area));
        context.getGraphics().renderItem(item, inner.x() + (inner.width() - 16) / 2, inner.y());
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
