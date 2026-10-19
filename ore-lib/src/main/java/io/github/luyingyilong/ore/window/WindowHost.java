package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.layout.Window;

public interface WindowHost {
    WindowContext getContext();

    boolean getNativeWindow();

    boolean isOpen();

    void open(Window window);

    void close();

    void requestFocus();
}
