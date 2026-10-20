package io.github.luyingyilong.ore.inspector;

import java.util.Objects;
import java.util.function.Consumer;

/** Coalesces shortcut requests without opening/focusing a native window on the input callback stack. */
final class InspectorOpenRequest {
    private final Consumer<Runnable> enqueue;
    private final Runnable open;
    private long generation;
    private boolean pending;

    InspectorOpenRequest(Consumer<Runnable> enqueue, Runnable open) {
        this.enqueue = Objects.requireNonNull(enqueue);
        this.open = Objects.requireNonNull(open);
    }

    void request() {
        if (pending) return;
        pending = true;
        long requested = ++generation;
        try {
            enqueue.accept(() -> {
                if (!pending || requested != generation) return;
                pending = false;
                open.run();
            });
        } catch (RuntimeException | Error error) {
            if (requested == generation) pending = false;
            throw error;
        }
    }

    void cancel() {
        pending = false;
        generation++;
    }
}
