package io.github.luyingyilong.ore.signal;

import java.util.function.Consumer;

/** Subscription-only view. Emission is available only to the owning SignalSource. */
public interface Signal<T> {
    Connection connect(SignalOwner owner, Consumer<? super T> slot);
    Connection connect(SignalOwner owner, String label, Consumer<? super T> slot);
    int connectionCount();
}
