package io.github.luyingyilong.geg.market.client;

import brachy.modularui.factory.ClientGUI;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.value.StringValue;
import io.github.luyingyilong.geg.client.ui.OreTheme;
import io.github.luyingyilong.geg.market.MarketActionPacket;
import io.github.luyingyilong.geg.market.MarketNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;


public final class ClientMarketScreen {
    private static MarketCanvasWidget canvas;
    private static MarketLayeredTextFieldWidget search;
    private static MarketLayeredTextFieldWidget quantity;
    private static MarketLayeredTextFieldWidget price;
    private static MarketLayeredTextFieldWidget tradeQuantity;
    private static StringValue quantityValue;
    private static StringValue priceValue;
    private static StringValue tradeQuantityValue;

    private ClientMarketScreen() {
    }

    public static void open() {
        canvas = new MarketCanvasWidget();
        int modalWidth = Math.min(340, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 12);
        int modalLeftOffset = -modalWidth / 2;
        search = new MarketLayeredTextFieldWidget(175.0F);
        search.setMarketVisible(true);
        search.hintText(Component.translatable("market.geg.search"));
        search.setMaxLength(80);
        search.autoUpdateOnChange(true);
        search.right(88).top(3).size(Math.min(138, Math.max(60, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 182)), 18);

        quantity = new MarketLayeredTextFieldWidget(250.0F);
        quantityValue = new StringValue("");
        quantity.value(quantityValue);
        quantity.hintText(Component.translatable("market.geg.quantity"));
        quantity.setMaxLength(9);
        quantity.autoUpdateOnChange(true);
        quantity.leftRel(0.5F, modalLeftOffset + 12, 0.0F).topRel(0.5F, 48, 0.0F).size(86, 18);
        quantity.setMarketVisible(false);

        price = new MarketLayeredTextFieldWidget(250.0F);
        priceValue = new StringValue("");
        price.value(priceValue);
        price.hintText(Component.translatable("market.geg.unit_price"));
        price.setMaxLength(22);
        price.autoUpdateOnChange(true);
        price.leftRel(0.5F, modalLeftOffset + 112, 0.0F).topRel(0.5F, 48, 0.0F).size(116, 18);
        price.setMarketVisible(false);

        tradeQuantity = new MarketLayeredTextFieldWidget(320.0F);
        tradeQuantityValue = new StringValue("1");
        tradeQuantity.value(tradeQuantityValue);
        tradeQuantity.hintText(Component.translatable("market.geg.quantity"));
        tradeQuantity.setMaxLength(9);
        tradeQuantity.autoUpdateOnChange(true);
        tradeQuantity.leftRel(0.5F, -95, 0.0F).topRel(0.5F, -43, 0.0F).size(190, 18);
        tradeQuantity.setMarketVisible(false);

        ModularPanel<?> panel = new ModularPanel<>("market");
        panel.widgetTheme(OreTheme.BACKGROUND);
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
        ClientGUI.open(new ModularScreen("geg", panel).useTheme(OreTheme.NAME).pausesGame(false));
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

    static void setSearchEnabled(boolean enabled) {
        search.setEnabled(enabled);
    }

    static String quantityText() {
        return quantity == null ? "" : quantity.getText();
    }

    static String priceText() {
        return price == null ? "" : price.getText();
    }

    static String tradeQuantityText() {
        return tradeQuantity == null ? "" : tradeQuantity.getText();
    }

    static void showTradeQuantity(boolean show) {
        tradeQuantity.setMarketVisible(show);
        if (show) setTradeQuantity(1);
    }

    static void setTradeQuantity(int value) {
        String text = Integer.toString(value);
        tradeQuantityValue.setStringValue(text);
        tradeQuantity.setText(text);
    }

    static void showFields(boolean showQuantity, boolean showPrice, int initialQuantity, long initialPrice) {
        quantity.setMarketVisible(showQuantity);
        price.setMarketVisible(showPrice);
        if (showQuantity) {
            String text = Integer.toString(initialQuantity);
            quantityValue.setStringValue(text);
            quantity.setText(text);
        }
        if (showPrice) {
            String text = io.github.luyingyilong.geg.money.MoneyCurrency.COIN.format(initialPrice);
            priceValue.setStringValue(text);
            price.setText(text);
        }
    }
}
