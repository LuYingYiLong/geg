package io.github.luyingyilong.ore.control.form.validation;

import net.minecraft.network.chat.Component;
import java.util.Optional;

@FunctionalInterface
public interface Validator<T> {
    Optional<Component> validate(T value);
}

