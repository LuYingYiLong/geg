package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.form.validation.Validator;

import java.util.Objects;

/**
 * Runtime-checked generic validator reference for declarative form rules.
 */
public record UiValidator<T>(Class<T> type, Validator<? super T> validator) {
    public UiValidator {
        Objects.requireNonNull(type);
        Objects.requireNonNull(validator);
    }

    public Class<T> getType() {
        return type;
    }

    public Validator<? super T> getValidator() {
        return validator;
    }

    Validator<? super T> require(Class<?> expected) {
        if (type != expected)
            throw new IllegalArgumentException("Validator field type mismatch: " + expected.getTypeName());
        return validator;
    }
}
