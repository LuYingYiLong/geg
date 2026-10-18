package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.layout.Window;

public interface WindowHost {
    WindowContext context();
    boolean nativeWindow();
    boolean isOpen();
    void open(Window window);
    void close();
    void requestFocus();
}
