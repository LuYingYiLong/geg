package io.github.luyingyilong.geg.client.ui.window;

import io.github.luyingyilong.geg.client.ui.layout.Window;

public interface WindowHost {
    WindowContext context();
    boolean nativeWindow();
    boolean isOpen();
    void open(Window window);
    void close();
    void requestFocus();
}
