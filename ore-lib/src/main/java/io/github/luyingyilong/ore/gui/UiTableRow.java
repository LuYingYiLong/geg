package io.github.luyingyilong.ore.gui;

import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Stable declarative table row. Business row types remain available through Java factories.
 */
public record UiTableRow(String key, Map<String, Component> values) {
    public UiTableRow {
        if (Objects.requireNonNull(key).isBlank()) throw new IllegalArgumentException("Table row key is blank");
        values = Map.copyOf(values);
    }

    public String getKey() {
        return key;
    }

    public Map<String, Component> getValues() {
        return values;
    }

    public Component getValue(String column) {
        return values.getOrDefault(column, Component.empty());
    }
}
