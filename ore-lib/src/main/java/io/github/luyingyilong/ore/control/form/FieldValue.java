package io.github.luyingyilong.ore.control.form;

import net.minecraft.network.chat.Component;

/** Parsing failure is a value, never a stale previously accepted document. */
public record FieldValue<T>(T value, Component error) {
    public boolean getValid() { return error == null; }
    public static <T> FieldValue<T> valid(T value) { return new FieldValue<>(value, null); }
    public static <T> FieldValue<T> invalid(Component error) {
        return new FieldValue<>(null, java.util.Objects.requireNonNull(error));
    }
}

