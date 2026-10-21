package io.github.luyingyilong.ore.control.form;

import java.util.Objects;

/** A name and its runtime type travel together across heterogeneous forms. */
public record FieldKey<T>(String name, Class<T> type) {
    public FieldKey {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Field name is required");
        Objects.requireNonNull(type);
        if (type.isPrimitive()) throw new IllegalArgumentException("Use boxed field types");
    }
    public String getName() { return name; }
    public Class<T> getType() { return type; }
}

