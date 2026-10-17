package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.geg.client.ui.Descriptions;
import io.github.luyingyilong.geg.client.ui.layout.Container;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Size;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** Stable read-only fields shared by listing, posting, history and claim detail views. */
final class MarketDetailDescriptions extends Container {
    enum Field {
        STATUS("status"), SELLER("seller"), QUANTITY("quantity"), PRICE("unit_price"),
        ESCROW("escrow"), INVENTORY("inventory_quantity"), CLAIM_ITEMS("claim_quantity"),
        CLAIM_COIN("claim_amount"), FEE("seller_fee_rate");

        final String label;
        Field(String label) { this.label = "market.geg." + label; }
    }

    private final Descriptions info = new Descriptions().columns(2).rowGap(4);

    MarketDetailDescriptions() {
        for (Field field : Field.values()) {
            info.add(field.name(), Component.translatable(field.label), Component.empty());
            info.item(field.name()).visible(false);
        }
        addChild(info);
    }

    void show(Map<Field, Component> values) {
        for (Field field : Field.values()) {
            var item = info.item(field.name());
            item.visible(values.containsKey(field));
            if (values.containsKey(field)) item.content(values.get(field));
        }
    }

    Descriptions descriptions() { return info; }
    @Override public Size measureSize(Font font, Size available) { return info.measureSize(font, available); }
    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(info, new Rect(0, 0, bounds.width(), bounds.height()), font);
    }
}
