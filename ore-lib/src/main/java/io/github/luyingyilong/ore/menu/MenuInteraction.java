package io.github.luyingyilong.ore.menu;

import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.Slot;

/** Adapter to one container screen's vanilla interaction state. It never redispatches an Ore event. */
public interface MenuInteraction {
    boolean pointer(PointerEvent event);
    boolean key(KeyEvent event);
    default boolean ownsPointer(int x,int y){return true;}
    void activate(int slotId);
    void cancel();
    void renderSlot(Slot view, DrawContext context, int x, int y);
}
