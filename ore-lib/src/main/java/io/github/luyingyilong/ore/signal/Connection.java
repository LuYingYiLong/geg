package io.github.luyingyilong.ore.signal;

public interface Connection extends AutoCloseable {
    void disconnect();

    boolean isConnected();

    @Override
    default void close() {
        disconnect();
    }
}
