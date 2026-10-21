package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.event.InputEvent;

/** Optional host behavior, invoked inside the same Window dispatch after control/navigation handling. */
public interface HostInputHandler {
    boolean input(InputEvent event);
    void cancel();
}
