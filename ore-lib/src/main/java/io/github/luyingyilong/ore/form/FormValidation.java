package io.github.luyingyilong.ore.form;

import java.util.*;
import net.minecraft.network.chat.Component;

public final class FormValidation {
    private final FormValues values;
    private final Map<String, Component> errors;
    public FormValidation(FormValues values, Map<String, Component> errors) {
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
        this.values = this.errors.isEmpty() ? Objects.requireNonNull(values) : null;
    }
    public boolean getValid() { return values != null; }
    public Map<String, Component> getErrors() { return errors; }
    public FormValues getValues() {
        if (!getValid()) throw new IllegalStateException("Invalid form has no submission values");
        return values;
    }
}

