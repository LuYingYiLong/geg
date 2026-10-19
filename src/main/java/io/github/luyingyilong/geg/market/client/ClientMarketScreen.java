package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.geg.client.GegInputs;

import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.value.StringValue;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.FormItem;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.NumberInput;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.geg.market.MarketActionPacket;
import io.github.luyingyilong.geg.market.MarketNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;


public final class ClientMarketScreen {
    private static MarketCanvasWidget canvas;
    private static Input search;
    private static NumberInput quantity;
    private static NumberInput price;
    private static NumberInput tradeQuantity;
    private static Form detailForm;
    private static Form tradeForm;
    private static FormItem quantityItem;
    private static FormItem priceItem;
    private static StringValue quantityValue;
    private static StringValue priceValue;
    private static StringValue tradeQuantityValue;

    private ClientMarketScreen() {
    }

    public static void open() {
        if (isOpen()) {
            return;
        }
        search = new Input(175.0F);
        search.setInputVisible(true);
        search.placeholder(Component.translatable("market.geg.search"));
        search.setMaxLength(80);
        search.autoUpdateOnChange(true);
        search.controlSize(Button.Size.SMALL);
        canvas = new MarketCanvasWidget(search);

        quantity = GegInputs.quantity(250.0F)
                .minimumValue(1).boundaryStrategy(NumberInput.BoundaryStrategy.CLAMP);
        quantityValue = new StringValue("");
        quantity.value(quantityValue);
        quantity.placeholder(Component.translatable("market.geg.quantity"));
        quantity.controlSize(Button.Size.MEDIUM);
        quantity.setInputVisible(false);

        price = GegInputs.coin(250.0F, false)
                .minimumValue("0.01").boundaryStrategy(NumberInput.BoundaryStrategy.CLAMP);
        priceValue = new StringValue("");
        price.value(priceValue);
        price.placeholder(Component.translatable("market.geg.unit_price"));
        price.controlSize(Button.Size.MEDIUM);
        price.setInputVisible(false);

        tradeQuantity = GegInputs.quantity(320.0F)
                .minimumValue(1).boundaryStrategy(NumberInput.BoundaryStrategy.CLAMP);
        tradeQuantityValue = new StringValue("1");
        tradeQuantity.value(tradeQuantityValue);
        tradeQuantity.placeholder(Component.translatable("market.geg.quantity"));
        tradeQuantity.controlSize(Button.Size.MEDIUM);
        tradeQuantity.setInputVisible(false);

        quantityItem = new FormItem("quantity", Component.translatable("market.geg.quantity"), quantity);
        priceItem = new FormItem("price", Component.translatable("market.geg.unit_price"), price);
        detailForm = new Form().add(quantityItem).add(priceItem);
        tradeForm = new Form().add(new FormItem("quantity",
                Component.translatable("market.geg.quantity"), tradeQuantity));
        canvas.bindForms(detailForm, tradeForm);
        canvas.prepareLayout(Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight(), Minecraft.getInstance().font);

        ModularPanel<?> panel = new InstantMarketPanel();
        panel.disableThemeBackground(true);
        panel.full();
        panel.child(canvas.full().disableThemeBackground(true));
        panel.child(search);
        panel.child(quantity);
        panel.child(price);
        panel.child(tradeQuantity);
        panel.onCloseAction(() -> {
            MarketNetwork.sendAction(new MarketActionPacket(MarketActionPacket.Action.CLOSE, 0L, 0L, 0L, 0, 0, ItemStack.EMPTY));
            ClientMarketState.clearDetails();
            canvas = null;
        });
        OreScreen.open(new ModularScreen("geg", panel).useTheme(OreTheme.NAME).pausesGame(false));
    }

    public static void openFromTerminal(BlockPos pos) {
        open();
        MarketNetwork.sendAction(MarketActionPacket.open(pos));
    }

    public static boolean isOpen() {
        return canvas != null && ModularScreen.isActive("geg", "market");
    }

    public static void refresh() {
        if (canvas != null) {
            canvas.invalidate();
        }
    }

    static String searchText() {
        return search == null ? "" : search.getText();
    }

    static String quantityText() {
        return detailForm == null ? "" : detailForm.value("quantity");
    }

    static String priceText() {
        return detailForm == null ? "" : detailForm.value("price");
    }

    static String tradeQuantityText() {
        return tradeForm == null ? "" : tradeForm.value("quantity");
    }

    static boolean validateDetailForm() { return detailForm != null && detailForm.validate(); }
    static boolean validateTradeForm() { return tradeForm != null && tradeForm.validate(); }
    static void setDetailQuantityMaximum(int maximum) { quantity.availableMaximum(java.math.BigDecimal.valueOf(maximum)); }
    static void setTradeQuantityMaximum(int maximum) { tradeQuantity.availableMaximum(java.math.BigDecimal.valueOf(maximum)); }

    static void showTradeQuantity(boolean show) {
        tradeForm.setVisible(show);
        if (show) {
            tradeQuantity.availableMaximum(java.math.BigDecimal.valueOf(Integer.MAX_VALUE));
            setTradeQuantity(1);
        }
    }

    static void setTradeQuantity(int value) {
        String text = Integer.toString(value);
        tradeQuantityValue.setStringValue(text);
        tradeQuantity.setTextNoSignal(text);
        tradeQuantity.clearError();
    }

    static void showFields(boolean showQuantity, boolean showPrice, int initialQuantity, long initialPrice) {
        quantityItem.setActive(showQuantity);
        priceItem.setActive(showPrice);
        detailForm.setVisible(showQuantity || showPrice);
        if (showQuantity) {
            quantity.availableMaximum(java.math.BigDecimal.valueOf(Integer.MAX_VALUE));
            String text = Integer.toString(initialQuantity);
            quantityValue.setStringValue(text);
            quantity.setTextNoSignal(text);
            quantity.clearError();
        }
        if (showPrice) {
            String text = io.github.luyingyilong.geg.money.MoneyCurrency.COIN.format(initialPrice);
            priceValue.setStringValue(text);
            price.setTextNoSignal(text);
            price.clearError();
        }
    }

    private static final class InstantMarketPanel extends ModularPanel<InstantMarketPanel> {
        private InstantMarketPanel() {
            super("market");
        }

        @Override
        public boolean shouldAnimate() {
            return false;
        }
    }
}
