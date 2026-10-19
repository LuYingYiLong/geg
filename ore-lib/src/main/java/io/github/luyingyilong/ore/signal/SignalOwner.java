package io.github.luyingyilong.ore.signal;

/** Explicit lifetime of a signal source or receiver; lambdas cannot infer their owner. */
public interface SignalOwner {
    SignalScope getSignalScope();
}
