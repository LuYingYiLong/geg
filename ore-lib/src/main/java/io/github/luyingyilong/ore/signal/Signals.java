package io.github.luyingyilong.ore.signal;

import java.util.Objects;
import java.util.function.Consumer;

public final class Signals {
    private Signals() {
    }

    /**
     * Configure a newly constructed component without hiding its typed signal endpoints.
     */
    public static <T> T configure(T component, Consumer<? super T> setup) {
        Objects.requireNonNull(setup).accept(component);
        return component;
    }
}
