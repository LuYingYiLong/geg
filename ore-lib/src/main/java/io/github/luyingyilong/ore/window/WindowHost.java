package io.github.luyingyilong.ore.window;


public interface WindowHost {
    WindowContext getContext();

    boolean getNativeWindow();

    boolean isOpen();

    void open(Window window);

    void close();

    void requestFocus();
}
