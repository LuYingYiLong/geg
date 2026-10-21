package io.github.luyingyilong.ore.control.form;

import java.util.*;

/** Immutable successful submission or initial-value snapshot. */
public final class FormValues {
    private final Map<FieldKey<?>, Object> values;
    private FormValues(Map<FieldKey<?>, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
    public <T> T getValue(FieldKey<T> key) {
        if (!values.containsKey(key)) throw new IllegalArgumentException("Unknown field or mismatched type: " + key.name());
        return key.type().cast(values.get(key));
    }
    public Set<FieldKey<?>> getKeys() { return values.keySet(); }
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private final Map<FieldKey<?>, Object> values = new LinkedHashMap<>();
        public <T> Builder setValue(FieldKey<T> key, T value) {
            Objects.requireNonNull(key);
            for (FieldKey<?> existing : values.keySet())
                if (existing.name().equals(key.name()) && !existing.equals(key))
                    throw new IllegalArgumentException("Conflicting field type: " + key.name());
            values.put(key, key.type().cast(value));
            return this;
        }
        public FormValues build() { return new FormValues(values); }
    }
}

