package io.github.luyingyilong.ore.signal;

import java.util.Objects;

public final class SignalSource0 {
    private final SignalSource<Void> source;
    private final Signal0 view;

    public SignalSource0(SignalOwner owner, String name) {
        source = new SignalSource<>(owner, name, 0, "");
        view = new Signal0() {
            @Override public Connection connect(SignalOwner receiver, Runnable slot) {
                return connect(receiver, "", slot);
            }
            @Override public Connection connect(SignalOwner receiver, String label, Runnable slot) {
                Objects.requireNonNull(slot);
                return source.signal().connect(receiver, label, ignored -> slot.run());
            }
            @Override public int connectionCount() { return source.signal().connectionCount(); }
        };
    }
    public Signal0 signal() { return view; }
    public void emit() { source.emit(null); }
}
