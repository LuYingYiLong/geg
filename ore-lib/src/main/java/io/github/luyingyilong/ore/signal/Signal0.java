package io.github.luyingyilong.ore.signal;

public interface Signal0 {
    Connection connect(SignalOwner owner, Runnable slot);

    Connection connect(SignalOwner owner, String label, Runnable slot);

    int connectionCount();
}
