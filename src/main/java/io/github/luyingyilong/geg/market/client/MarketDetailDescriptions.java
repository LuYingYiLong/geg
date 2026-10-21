package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.ore.control.display.Descriptions;
import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** Stable read-only fields shared by listing, posting and history detail views. */
final class MarketDetailDescriptions extends Container {
    enum Field {
        STATUS("status"), SELLER("seller"), QUANTITY("quantity"), PRICE("unit_price"),
        ESCROW("escrow"), INVENTORY("inventory_quantity"), FEE("seller_fee_rate");

        final String label;
        Field(String label) { this.label = "market.geg." + label; }
    }

    private final Descriptions info = new Descriptions().setColumns(2).setRowGap(4);

    MarketDetailDescriptions() {
        for (Field field : Field.values()) {
            info.addItem(field.name(), Component.translatable(field.label), Component.empty());
            info.getItem(field.name()).setVisible(false);
        }
        this.addChild(info);
    }

    void show(Map<Field, Component> values) {
        for (Field field : Field.values()) {
            var item = info.getItem(field.name());
            item.setVisible(values.containsKey(field));
            if (values.containsKey(field)) item.setContent(values.get(field));
        }
    }

    Descriptions descriptions() { return info; }
    @Override public Size measureSize(Font font, Size available) { return info.measureSize(font, available); }
    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(info, new Rect(0, 0, bounds.width(), bounds.height()), font);
    }
}
