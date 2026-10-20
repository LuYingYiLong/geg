package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.geg.client.GegInputs;

import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.value.StringValue;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.FormItem;
import io.github.luyingyilong.ore.form.*;
import java.math.BigDecimal;
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
    private static final FieldKey<BigDecimal> QUANTITY = new FieldKey<>("quantity", BigDecimal.class);
    private static final FieldKey<BigDecimal> PRICE = new FieldKey<>("price", BigDecimal.class);
    private static FormValues detailValues, tradeValues;
    private static FormItem<BigDecimal> quantityItem;
    private static FormItem<BigDecimal> priceItem;
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
        search.setPlaceholder(Component.translatable("market.geg.search"));
        search.setMaxLength(80);
        search.autoUpdateOnChange(true);
        search.setControlSize(Button.Size.SMALL);
        canvas = new MarketCanvasWidget(search);

        quantity = GegInputs.quantity(250.0F)
                .setMinimumValue(1).setBoundaryStrategy(NumberInput.BoundaryStrategy.CLAMP);
        quantityValue = new StringValue("");
        quantity.value(quantityValue);
        quantity.setPlaceholder(Component.translatable("market.geg.quantity"));
        quantity.setControlSize(Button.Size.MEDIUM);
        quantity.setInputVisible(false);

        price = GegInputs.coin(250.0F, false)
                .setMinimumValue("0.01").setBoundaryStrategy(NumberInput.BoundaryStrategy.CLAMP);
        priceValue = new StringValue("");
        price.value(priceValue);
        price.setPlaceholder(Component.translatable("market.geg.unit_price"));
        price.setControlSize(Button.Size.MEDIUM);
        price.setInputVisible(false);

        tradeQuantity = GegInputs.quantity(320.0F)
                .setMinimumValue(1).setBoundaryStrategy(NumberInput.BoundaryStrategy.CLAMP);
        tradeQuantityValue = new StringValue("1");
        tradeQuantity.value(tradeQuantityValue);
        tradeQuantity.setPlaceholder(Component.translatable("market.geg.quantity"));
        tradeQuantity.setControlSize(Button.Size.MEDIUM);
        tradeQuantity.setInputVisible(false);

        quantityItem = new FormItem<>(QUANTITY, Component.translatable("market.geg.quantity"), FormFields.number(quantity));
        priceItem = new FormItem<>(PRICE, Component.translatable("market.geg.unit_price"), FormFields.number(price));
        detailForm = new Form().addItem(quantityItem).addItem(priceItem);
        tradeForm = new Form().addItem(new FormItem<>(QUANTITY,
                Component.translatable("market.geg.quantity"), FormFields.number(tradeQuantity)));
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
        OreScreen.open(new ModularScreen("geg", panel).pausesGame(false));
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

    static BigDecimal quantityAmount() { return detailValues.getValue(QUANTITY); }
    static BigDecimal priceAmount() { return detailValues.getValue(PRICE); }
    static BigDecimal tradeAmount() { return tradeValues.getValue(QUANTITY); }
    static BigDecimal tradePreview() { return tradeQuantity.getPreviewValue(); }
    static boolean validateDetailForm() {
        var result = detailForm.validate();
        detailValues = result.getValid() ? result.getValues() : null;
        return result.getValid();
    }
    static boolean validateTradeForm() {
        var result = tradeForm.validate();
        tradeValues = result.getValid() ? result.getValues() : null;
        return result.getValid();
    }
    static void setDetailQuantityMaximum(int maximum) { quantity.setAvailableMaximum(java.math.BigDecimal.valueOf(maximum)); }
    static void setTradeQuantityMaximum(int maximum) { tradeQuantity.setAvailableMaximum(java.math.BigDecimal.valueOf(maximum)); }

    static void showTradeQuantity(boolean show) {
        tradeForm.setVisible(show);
        if (show) {
            tradeQuantity.setAvailableMaximum(java.math.BigDecimal.valueOf(Integer.MAX_VALUE));
            setTradeQuantity(1);
            tradeForm.captureInitialValues();
            tradeValues = null;
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
            quantity.setAvailableMaximum(java.math.BigDecimal.valueOf(Integer.MAX_VALUE));
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
        detailForm.captureInitialValues();
        detailValues = null;
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
