package io.github.luyingyilong.ore.theme;

import java.util.Objects;
import java.util.function.Predicate;

/** A declared, typed item, scoped by a control type or an independent resource namespace. */
public record ThemeKey<T>(String type, String name, Class<T> valueType, Kind kind, T defaultValue,
                          boolean affectsLayout, Predicate<T> validator) {
    public enum Kind { STYLE, COLOR, FONT, FLOAT, INTEGER, BOOLEAN, TEXTURE, FONT_METRICS }
    public ThemeKey {
        Objects.requireNonNull(type); Objects.requireNonNull(name); Objects.requireNonNull(valueType);
        Objects.requireNonNull(kind); Objects.requireNonNull(defaultValue); Objects.requireNonNull(validator);
        if (type.isBlank() || name.isBlank() || name.contains(":")) throw new IllegalArgumentException("Invalid theme item name");
        if (!valueType.isInstance(defaultValue) || !validator.test(defaultValue)) throw new IllegalArgumentException("Invalid theme default");
    }
    public String getId() { return type + ":" + name; }
    public T validate(Object value) {
        T typed = valueType.cast(Objects.requireNonNull(value));
        if (!validator.test(typed)) throw new IllegalArgumentException("Invalid value for " + getId());
        return typed;
    }
    public static <T> ThemeKey<T> of(String type, String name, Class<T> clazz, Kind kind, T value, boolean layout) {
        return new ThemeKey<>(type, name, clazz, kind, value, layout, v -> true);
    }
}
