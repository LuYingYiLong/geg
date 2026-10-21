package io.github.luyingyilong.ore.control.form.validation;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;

public final class Validators {
    private Validators() { }
    public static <T> Validator<T> predicate(Predicate<? super T> rule, Component error) {
        return value -> rule.test(value) ? Optional.empty() : Optional.of(error);
    }
    public static <T> Validator<T> required() {
        return predicate(value -> value != null && (!(value instanceof String text) || !text.isBlank()),
                Component.translatable("ore.form.required"));
    }
    public static Validator<String> length(int min, int max) {
        if (min < 0 || max < min) throw new IllegalArgumentException("Invalid length range");
        return predicate(value -> value != null && value.codePointCount(0, value.length()) >= min
                && value.codePointCount(0, value.length()) <= max,
                Component.translatable("ore.form.length", min, max));
    }
    public static Validator<BigDecimal> range(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) throw new IllegalArgumentException("Invalid numeric range");
        return predicate(value -> value != null && (min == null || value.compareTo(min) >= 0)
                && (max == null || value.compareTo(max) <= 0), Component.translatable("ore.form.range",
                min == null ? "−∞" : min.toPlainString(), max == null ? "+∞" : max.toPlainString()));
    }
}

